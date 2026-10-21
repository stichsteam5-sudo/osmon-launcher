package uz.osmon.launcher;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Apple TV aerial videos, streamed straight from Apple's servers.
 * The clip list comes from Apple's current tvOS feed (bundled as assets/aerial.json).
 * A newer entries.json dropped into /sdcard/Osmon/aerial.json overrides it — no app update needed.
 */
public class Aerial {
    public static class Clip {
        public final String label, url;

        Clip(String label, String url) {
            this.label = label;
            this.url = url;
        }
    }

    /** Blocking — call from a background thread. Returns a shuffled list (may be empty). */
    public static List<Clip> load(Context c) {
        List<Clip> out = new ArrayList<Clip>();
        File override = new File(Theme.root(), "aerial.json");
        try {
            if (override.isFile()) walk(new JSONTokener(read(new FileInputStream(override))).nextValue(), out);
        } catch (Throwable ignored) {
        }
        if (out.isEmpty()) {
            try {
                walk(new JSONTokener(read(c.getAssets().open("aerial.json"))).nextValue(), out);
            } catch (Throwable ignored) {
            }
        }
        android.util.Log.i("Osmon", "aerial clips: " + out.size());
        Collections.shuffle(out);
        return out;
    }

    private static void walk(Object o, List<Clip> out) {
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            for (int i = 0; i < a.length(); i++) walk(a.opt(i), out);
        } else if (o instanceof JSONObject) {
            JSONObject j = (JSONObject) o;
            String url = pick(j);
            if (url != null) {
                out.add(new Clip(j.optString("l", j.optString("accessibilityLabel", j.optString("shotID", ""))), url));
                return;
            }
            Iterator<String> it = j.keys();
            while (it.hasNext()) walk(j.opt(it.next()), out);
        }
    }

    /** Prefer 1080p H.264 — 4K/HDR would choke the TV and isn't needed on a 43" screen. */
    private static String pick(JSONObject j) {
        String[] keys = {"u", "url-1080-H264", "url-1080-SDR"};
        for (String k : keys) {
            String u = j.optString(k, "");
            String l = u.toLowerCase();
            if (u.startsWith("http") && (l.endsWith(".mov") || l.endsWith(".mp4"))) return u;
        }
        return null;
    }

    private static String read(InputStream in) throws java.io.IOException {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
