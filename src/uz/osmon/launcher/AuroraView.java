package uz.osmon.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Soft gradient background with three slowly drifting glows.
 * Drawn into a tiny bitmap (1/6 of the screen) and stretched up — the picture is blurry
 * by nature, so it looks the same, but costs almost nothing even on a weak TV chip.
 */
public class AuroraView extends View {
    private static final int SCALE = 6;
    private static final long FRAME_MS = 90; // ~11 fps is plenty for a slow drift
    private static final float[] SX = {0.050f, 0.037f, 0.061f};
    private static final float[] SY = {0.043f, 0.058f, 0.034f};
    private static final float[] PH = {0f, 2.1f, 4.3f};

    private final int[] base;
    private final int[] glow;
    private final boolean animate;
    private boolean running = true;
    private final Paint basePaint = new Paint();
    private final Paint[] blobs = new Paint[3];
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Rect dst = new Rect();
    private Bitmap small;
    private Canvas smallCanvas;
    private float radius;
    private final long t0 = SystemClock.uptimeMillis();

    public AuroraView(Context c, int[] base, int[] glow, boolean animate) {
        super(c);
        this.base = base;
        this.glow = glow;
        this.animate = animate;
        basePaint.setDither(true);
        setWillNotDraw(false);
    }

    public void setRunning(boolean r) {
        boolean was = running;
        running = r;
        if (r && !was) invalidate();
    }

    private static int alpha(int c, int a) {
        return (c & 0x00FFFFFF) | (a << 24);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w <= 0 || h <= 0) return;
        int sw = Math.max(16, w / SCALE), sh = Math.max(9, h / SCALE);
        if (small != null) small.recycle();
        small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        smallCanvas = new Canvas(small);
        dst.set(0, 0, w, h);
        basePaint.setShader(new LinearGradient(0, 0, sw, sh, base, null, Shader.TileMode.CLAMP));
        radius = Math.max(sw, sh) * 0.55f;
        for (int i = 0; i < 3; i++) {
            int c = glow[i];
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setDither(true);
            p.setShader(new RadialGradient(0, 0, radius,
                    new int[]{alpha(c, 150), alpha(c, 60), alpha(c, 0)},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            blobs[i] = p;
        }
        renderSmall();
    }

    private void renderSmall() {
        if (smallCanvas == null) return;
        int w = small.getWidth(), h = small.getHeight();
        smallCanvas.drawRect(0, 0, w, h, basePaint);
        float t = animate ? (SystemClock.uptimeMillis() - t0) / 1000f : 9f;
        for (int i = 0; i < 3; i++) {
            float x = w * (0.5f + 0.45f * (float) Math.sin(t * SX[i] * 2 + PH[i]));
            float y = h * (0.5f + 0.40f * (float) Math.cos(t * SY[i] * 2 + PH[i] * 1.3f));
            smallCanvas.save();
            smallCanvas.translate(x, y);
            smallCanvas.scale(1.35f, 0.75f);
            smallCanvas.drawCircle(0, 0, radius, blobs[i]);
            smallCanvas.restore();
        }
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (small == null) return;
        cv.drawBitmap(small, null, dst, blit);
        if (animate && running) {
            postDelayed(frame, FRAME_MS);
        }
    }

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!running || getVisibility() != VISIBLE) return;
            renderSmall();
            invalidate();
        }
    };

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        removeCallbacks(frame);
    }
}
