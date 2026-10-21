package uz.osmon.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AppInfo {
    public String pkg;
    public String label;
    public Drawable banner;
    public Drawable icon;
    public int tile;
    public Intent intent;

    public Drawable fresh(Drawable d) {
        if (d == null) return null;
        Drawable.ConstantState cs = d.getConstantState();
        return cs != null ? cs.newDrawable().mutate() : d;
    }

    /** All launchable apps: TV apps first (with their wide banners), then phone-style apps. */
    public static List<AppInfo> load(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, AppInfo> map = new LinkedHashMap<String, AppInfo>();
        String self = c.getPackageName();
        String[] cats = {"android.intent.category.LEANBACK_LAUNCHER", Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            List<ResolveInfo> res;
            try {
                res = pm.queryIntentActivities(q, 0);
            } catch (Throwable t) {
                continue;
            }
            for (ResolveInfo ri : res) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(self) || map.containsKey(pkg)) continue;
                AppInfo a = new AppInfo();
                a.pkg = pkg;
                try {
                    a.label = ri.loadLabel(pm).toString();
                } catch (Throwable t) {
                    a.label = pkg;
                }
                try {
                    a.banner = ri.activityInfo.loadBanner(pm);
                    if (a.banner == null) a.banner = pm.getApplicationBanner(pkg);
                } catch (Throwable ignored) {
                }
                try {
                    a.icon = ri.loadIcon(pm);
                } catch (Throwable ignored) {
                }
                a.tile = tileColor(a.icon);
                a.intent = new Intent(Intent.ACTION_MAIN).addCategory(cat)
                        .setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                map.put(pkg, a);
            }
        }
        List<AppInfo> out = new ArrayList<AppInfo>(map.values());
        final Collator col = Collator.getInstance(new Locale("ru"));
        Collections.sort(out, new Comparator<AppInfo>() {
            public int compare(AppInfo x, AppInfo y) {
                return col.compare(x.label, y.label);
            }
        });
        return out;
    }

    /** A deep colour taken from the icon, for tvOS-style tiles behind small icons. */
    static int tileColor(Drawable d) {
        int fallback = 0xFF2A2D36;
        if (d == null) return fallback;
        try {
            Drawable dd = d.getConstantState() != null ? d.getConstantState().newDrawable() : d;
            Bitmap b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            dd.setBounds(0, 0, 24, 24);
            dd.draw(cv);
            long r = 0, g = 0, bl = 0, n = 0;
            for (int y = 0; y < 24; y++) {
                for (int x = 0; x < 24; x++) {
                    int p = b.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    r += Color.red(p);
                    g += Color.green(p);
                    bl += Color.blue(p);
                    n++;
                }
            }
            b.recycle();
            if (n == 0) return fallback;
            float[] hsv = new float[3];
            Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (bl / n), hsv);
            hsv[1] = Math.min(hsv[1], 0.75f);
            hsv[2] = Math.min(Math.max(hsv[2] * 0.55f, 0.16f), 0.42f);
            return Color.HSVToColor(hsv);
        } catch (Throwable t) {
            return fallback;
        }
    }
}
