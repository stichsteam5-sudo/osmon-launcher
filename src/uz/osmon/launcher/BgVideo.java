package uz.osmon.launcher;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;

/**
 * Silent background video player built directly on MediaExtractor + MediaCodec
 * (the same low-level path ExoPlayer, YouTube and Aerial Views use).
 * The TV's stock MediaPlayer refuses to render into a launcher, so we don't use it at all.
 * Network reads go through our own HTTP data source; everything runs off the UI thread.
 */
public class BgVideo extends SurfaceView implements SurfaceHolder.Callback {
    static final String TAG = "Osmon";

    public interface Listener {
        void onFirstFrame();

        void onEnded();

        void onFailed(int what, int extra);
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private Surface surface;
    private String source;
    private boolean loop;
    private Session session;
    private volatile boolean paused;
    private boolean released;

    public BgVideo(Context c, Listener l) {
        super(c);
        listener = l;
        setFocusable(false);
        getHolder().addCallback(this);
    }

    // ------------------------------------------------------------------ API (UI thread)

    public void play(String src, boolean looping) {
        source = src;
        loop = looping;
        restart();
    }

    public void setPlaying(boolean play) {
        paused = !play;
    }

    public void release() {
        released = true;
        stopSession(false);
    }

    private void restart() {
        stopSession(false);
        if (released || surface == null || source == null) return;
        session = new Session(source, loop, surface, session);
        session.start();
    }

    private void stopSession(boolean wait) {
        Session s = session;
        session = null;
        if (s == null) return;
        s.cancel();
        if (wait) {
            try {
                s.join(800);
            } catch (InterruptedException ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ surface

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        surface = h.getSurface();
        if (source != null && !released) restart();
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        stopSession(true); // the decoder must let go of the surface before it disappears
        surface = null;
    }

    // ------------------------------------------------------------------ decoding thread

    private class Session extends Thread {
        final String src;
        final boolean looping;
        final Surface out;
        final Session previous;
        volatile boolean cancelled;

        Session(String src, boolean looping, Surface out, Session previous) {
            super("osmon-video");
            this.src = src;
            this.looping = looping;
            this.out = out;
            this.previous = previous;
        }

        void cancel() {
            cancelled = true;
        }

        @Override
        public void run() {
            if (previous != null) {
                try {
                    previous.join(2000); // only one decoder may own the surface
                } catch (InterruptedException ignored) {
                }
            }
            if (cancelled) return;
            MediaExtractor ex = null;
            MediaCodec codec = null;
            HttpSource http = null;
            try {
                Log.i(TAG, "video open " + src);
                ex = new MediaExtractor();
                if (src.startsWith("http")) {
                    http = new HttpSource(src);
                    Log.i(TAG, "video size " + http.getSize()); // fetch the first block here, on our own thread
                    ex.setDataSource(http);
                    Log.i(TAG, "video extractor ready, tracks " + ex.getTrackCount());
                } else {
                    ex.setDataSource(src);
                }
                int track = -1;
                MediaFormat fmt = null;
                for (int i = 0; i < ex.getTrackCount(); i++) {
                    MediaFormat f = ex.getTrackFormat(i);
                    String mime = f.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("video/")) {
                        track = i;
                        fmt = f;
                        break;
                    }
                }
                if (track < 0) throw new IOException("no video track");
                ex.selectTrack(track);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                Log.i(TAG, "video track " + mime + " " + fmt);
                codec = MediaCodec.createDecoderByType(mime);
                codec.configure(fmt, out, null, 0);
                codec.start();
                decodeLoop(ex, codec);
            } catch (Throwable t) {
                Log.w(TAG, "video failed", t);
                if (!cancelled) post(false, true);
            } finally {
                try {
                    if (codec != null) {
                        codec.stop();
                        codec.release();
                    }
                } catch (Throwable ignored) {
                }
                try {
                    if (ex != null) ex.release();
                } catch (Throwable ignored) {
                }
                if (http != null) http.close();
            }
        }

        void decodeLoop(MediaExtractor ex, MediaCodec codec) {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, firstFrame = true;
            long baseNs = -1, baseUs = 0, pausedAt = -1;
            while (!cancelled) {
                if (paused) {
                    if (pausedAt < 0) pausedAt = System.nanoTime();
                    nap(50);
                    continue;
                }
                if (pausedAt >= 0) { // shift the clock so playback resumes smoothly
                    if (baseNs >= 0) baseNs += System.nanoTime() - pausedAt;
                    pausedAt = -1;
                }
                if (!inputDone) {
                    int in = codec.dequeueInputBuffer(0);
                    if (in >= 0) {
                        ByteBuffer buf = codec.getInputBuffer(in);
                        int n = ex.readSampleData(buf, 0);
                        if (n < 0) {
                            codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(in, 0, n, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int outIdx = codec.dequeueOutputBuffer(info, 5000);
                if (outIdx >= 0) {
                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (info.size > 0) {
                        if (baseNs < 0) {
                            baseNs = System.nanoTime();
                            baseUs = info.presentationTimeUs;
                        }
                        long dueNs = baseNs + (info.presentationTimeUs - baseUs) * 1000L;
                        long lateNs = System.nanoTime() - dueNs;
                        if (lateNs > 500000000L) { // fell behind (slow network): re-sync the clock
                            baseNs = System.nanoTime();
                            baseUs = info.presentationTimeUs;
                            dueNs = baseNs;
                        }
                        // wake up a little early and hand the exact display time to the compositor
                        long waitMs = (dueNs - System.nanoTime()) / 1000000L - 8;
                        if (waitMs > 0) nap(Math.min(waitMs, 200));
                        codec.releaseOutputBuffer(outIdx, dueNs);
                        if (firstFrame) {
                            firstFrame = false;
                            Log.i(TAG, "video first frame");
                            post(true, false);
                        }
                    } else {
                        codec.releaseOutputBuffer(outIdx, false);
                    }
                    if (eos) {
                        if (looping) {
                            ex.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                            codec.flush();
                            inputDone = false;
                            baseNs = -1;
                        } else {
                            Log.i(TAG, "video ended");
                            post(false, false);
                            return;
                        }
                    }
                }
            }
        }

        void nap(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException ignored) {
            }
        }

        /** firstFrame=true → onFirstFrame; failed=true → onFailed; otherwise onEnded. */
        void post(final boolean firstFrame, final boolean failed) {
            ui.post(new Runnable() {
                public void run() {
                    if (cancelled || released || session != Session.this) return;
                    if (firstFrame) listener.onFirstFrame();
                    else if (failed) listener.onFailed(-1, 0);
                    else listener.onEnded();
                }
            });
        }
    }

    // ------------------------------------------------------------------ HTTP with range requests

    /**
     * Lets MediaExtractor read a remote file through our own HTTP client.
     * The file is fetched in 2 MB blocks (Range requests) kept in a small LRU cache, because the
     * extractor jumps around a lot (Apple's .mov keeps its index at the END of the file).
     */
    static class HttpSource extends MediaDataSource {
        private static final int CHUNK = 3 * 1024 * 1024;
        private static final int KEEP = 8; // ~24 MB of cache at most
        private static final int AHEAD = 2; // blocks downloaded in advance while playing
        private final java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        private final java.util.Map<Long, java.util.concurrent.Future<byte[]>> pending =
                new java.util.HashMap<Long, java.util.concurrent.Future<byte[]>>();
        private volatile boolean closed;
        private final String url;
        private volatile long size = -1;
        private volatile int fetches;
        private final java.util.LinkedHashMap<Long, byte[]> cache =
                new java.util.LinkedHashMap<Long, byte[]>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(java.util.Map.Entry<Long, byte[]> e) {
                        return size() > KEEP;
                    }
                };

        HttpSource(String url) {
            // Apple's video CDN is signed by Apple's own CA, which Android TVs don't trust —
            // HTTPS fails with "Trust anchor not found". The same files are served over plain HTTP.
            this.url = url.startsWith("https://sylvan.apple.com/") ? "http://" + url.substring(8) : url;
        }

        private byte[] fetch(long from, long to) throws IOException {
            IOException last = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                HttpURLConnection c = null;
                long t0 = System.currentTimeMillis();
                try {
                    if (fetches < 2) Log.i(TAG, "video http request " + from + "-" + to + " try " + attempt);
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(12000);
                    c.setRequestProperty("Range", "bytes=" + from + "-" + to);
                    int code = c.getResponseCode();
                    if (fetches < 2) {
                        Log.i(TAG, "video http answer " + code + " in " + (System.currentTimeMillis() - t0) + " ms, "
                                + c.getHeaderField("Content-Range"));
                    }
                    if (code != 206 && code != 200) throw new IOException("HTTP " + code);
                    if (size < 0) {
                        String cr = c.getHeaderField("Content-Range"); // bytes a-b/total
                        if (cr != null && cr.lastIndexOf('/') > 0) {
                            try {
                                size = Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1).trim());
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        if (size < 0 && code == 200) size = c.getContentLength();
                    }
                    int want = (int) (to - from + 1);
                    byte[] buf = new byte[want];
                    InputStream in = c.getInputStream();
                    int got = 0;
                    while (got < want) {
                        int n = in.read(buf, got, want - got);
                        if (n < 0) break;
                        got += n;
                    }
                    in.close();
                    fetches++;
                    if (fetches <= 3 || fetches % 20 == 0) {
                        Log.i(TAG, "video http block #" + fetches + " at " + (from >> 20) + " MB, size "
                                + (size >> 20) + " MB");
                    }
                    if (got == want) return buf;
                    byte[] cut = new byte[got];
                    System.arraycopy(buf, 0, cut, 0, got);
                    return cut;
                } catch (IOException e) {
                    Log.w(TAG, "video http error after " + (System.currentTimeMillis() - t0) + " ms: " + e);
                    last = e;
                } finally {
                    if (c != null) c.disconnect();
                }
            }
            throw last;
        }

        private byte[] block(long idx) throws IOException {
            byte[] b = cache.get(idx);
            if (b == null) {
                java.util.concurrent.Future<byte[]> f = pending.remove(idx);
                if (f != null) {
                    try {
                        b = f.get();
                    } catch (Exception e) {
                        b = null; // prefetch failed — fetch it directly below
                    }
                }
                if (b == null) b = fetchBlock(idx);
                cache.put(idx, b);
            }
            prefetch(idx);
            return b;
        }

        private byte[] fetchBlock(long idx) throws IOException {
            long from = idx * CHUNK;
            long to = from + CHUNK - 1;
            if (size >= 0) to = Math.min(to, size - 1);
            return fetch(from, to);
        }

        /** Queue the next blocks so playback never waits on the network. */
        private void prefetch(long idx) {
            if (closed || size < 0) return;
            for (long n = idx + 1; n <= idx + AHEAD; n++) {
                if (n * CHUNK >= size || cache.containsKey(n) || pending.containsKey(n)) continue;
                final long want = n;
                pending.put(want, pool.submit(new java.util.concurrent.Callable<byte[]>() {
                    public byte[] call() throws Exception {
                        return fetchBlock(want);
                    }
                }));
            }
        }

        @Override
        public synchronized int readAt(long position, byte[] buffer, int offset, int len) throws IOException {
            if (len == 0) return 0;
            if (size < 0) getSize();
            if (position >= size) return -1;
            long idx = position / CHUNK;
            byte[] b = block(idx);
            int inOff = (int) (position - idx * CHUNK);
            int n = Math.min(len, b.length - inOff);
            if (n <= 0) return -1;
            System.arraycopy(b, inOff, buffer, offset, n);
            return n;
        }

        @Override
        public synchronized long getSize() throws IOException {
            if (size < 0) {
                Log.i(TAG, "video getSize");
                byte[] first = fetch(0, CHUNK - 1);
                cache.put(0L, first);
            }
            return size;
        }

        @Override
        public synchronized void close() {
            closed = true;
            for (java.util.concurrent.Future<byte[]> f : pending.values()) f.cancel(true);
            pending.clear();
            pool.shutdownNow();
            cache.clear();
        }
    }
}
