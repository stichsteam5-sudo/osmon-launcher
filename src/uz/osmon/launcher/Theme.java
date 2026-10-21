package uz.osmon.launcher;

import android.content.Context;
import android.graphics.Color;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A theme = background colours + glow colours + accent, and optionally a wallpaper.
 * Built-in themes live in code; free theme packs are folders in /sdcard/Osmon/themes.
 */
public class Theme {
    public String id;
    public String name;
    public int[] colors;
    public int[] glow;
    public int accent = Color.WHITE;
    public int dim = 15;
    public File dir;
    public String wallpaper;
    public boolean slideshow;
    public boolean pack;
    public boolean aerial;

    private static Theme make(String id, String name, String c1, String c2, String c3,
                              String g1, String g2, String g3, int dim) {
        Theme t = new Theme();
        t.id = id;
        t.name = name;
        t.colors = new int[]{Color.parseColor(c1), Color.parseColor(c2), Color.parseColor(c3)};
        t.glow = new int[]{Color.parseColor(g1), Color.parseColor(g2), Color.parseColor(g3)};
        t.dim = dim;
        return t;
    }

    public static List<Theme> builtIns(Context c) {
        List<Theme> l = new ArrayList<Theme>();
        Theme air = make("aerial", c.getString(R.string.theme_aerial), "#0E0F13", "#1A1C24", "#0E0F13",
                "#3A4060", "#2B3A58", "#4A3B66", 10);
        air.aerial = true;
        l.add(air);
        l.add(make("tvos", c.getString(R.string.theme_classic), "#0E0F13", "#1A1C24", "#0E0F13", "#3A4060", "#2B3A58", "#4A3B66", 0));
        l.add(make("aurora", c.getString(R.string.theme_aurora), "#04121A", "#062A2E", "#0B1530", "#10B981", "#22D3EE", "#6366F1", 5));
        l.add(make("sunset", c.getString(R.string.theme_sunset), "#1A0B1E", "#3A1030", "#4A1A12", "#F97316", "#EC4899", "#A855F7", 10));
        l.add(make("ocean", c.getString(R.string.theme_ocean), "#031322", "#06263F", "#0A3550", "#0EA5E9", "#14B8A6", "#3B82F6", 5));
        l.add(make("midnight", c.getString(R.string.theme_midnight), "#05060F", "#0D1030", "#1A0F35", "#4F46E5", "#7C3AED", "#2563EB", 5));
        l.add(make("forest", c.getString(R.string.theme_forest), "#06130C", "#0C2416", "#132D1C", "#22C55E", "#84CC16", "#14B8A6", 10));
        l.add(make("desert", c.getString(R.string.theme_desert), "#1C1206", "#2D1B08", "#241008", "#F59E0B", "#FBBF24", "#EA580C", 15));
        l.add(make("neon", c.getString(R.string.theme_neon), "#0A0014", "#14002A", "#00141F", "#F0ABFC", "#22D3EE", "#A3E635", 10));
        l.add(make("graphite", c.getString(R.string.theme_graphite), "#101012", "#1C1C1E", "#2A2A2E", "#4B4B52", "#3A3A40", "#5A5A62", 0));
        l.add(make("rose", c.getString(R.string.theme_rose), "#1A0E14", "#2A1420", "#1E1024", "#FDA4AF", "#F9A8D4", "#C4B5FD", 10));
        return l;
    }

    public static File root() {
        return new File(Environment.getExternalStorageDirectory(), "Osmon");
    }

    public static List<File> themeDirs(Context c) {
        List<File> l = new ArrayList<File>();
        l.add(new File(root(), "themes"));
        // "adb push folder /sdcard/Osmon/" sometimes drops the theme folders straight into Osmon/
        l.add(root());
        try {
            File ext = c.getExternalFilesDir("themes");
            if (ext != null) l.add(ext);
        } catch (Throwable ignored) {
        }
        return l;
    }

    public static List<Theme> all(Context c) {
        List<Theme> l = builtIns(c);
        for (File d : themeDirs(c)) {
            File[] subs = d.listFiles();
            if (subs == null) continue;
            Arrays.sort(subs);
            for (File s : subs) {
                if (!s.isDirectory()) continue;
                String n = s.getName();
                if (d.equals(root()) && (n.equals("themes") || n.equals("wallpapers"))) continue;
                Theme t = fromDir(s);
                if (t != null) l.add(t);
            }
        }
        return l;
    }

    public static Theme find(Context c, String id) {
        List<Theme> l = all(c);
        for (Theme t : l) if (t.id.equals(id)) return t;
        return l.get(0);
    }

    /** Reads a theme pack folder. A folder with only pictures in it is also a valid theme. */
    static Theme fromDir(File d) {
        File j = new File(d, "theme.json");
        List<File> imgs = Media.images(d);
        List<File> vids = Media.videos(d);
        if (!j.exists() && imgs.isEmpty() && vids.isEmpty()) return null;

        Theme t = new Theme();
        t.id = "pack:" + d.getAbsolutePath();
        t.name = d.getName();
        t.dir = d;
        t.pack = true;
        t.colors = new int[]{0xFF0E0F13, 0xFF1A1C24, 0xFF0E0F13};
        t.glow = new int[]{0xFF3A4060, 0xFF2B3A58, 0xFF4A3B66};
        if (j.exists()) {
            try {
                JSONObject o = new JSONObject(readText(j));
                t.name = o.optString("name", t.name);
                JSONObject names = o.optJSONObject("names"); // {"en": "...", "ru": "..."}
                if (names != null) {
                    java.util.Locale loc = java.util.Locale.getDefault();
                    String tag = loc.getLanguage() + "-" + loc.getCountry();
                    String byLang = names.optString(tag, names.optString(loc.getLanguage(), ""));
                    if (byLang.length() == 0 && loc.getLanguage().equals("in")) byLang = names.optString("id", "");
                    if (byLang.length() == 0) byLang = names.optString("en", "");
                    if (byLang.length() > 0) t.name = byLang;
                }
                int[] c = colorsOf(o.optJSONArray("colors"));
                if (c != null) t.colors = c;
                int[] g = colorsOf(o.optJSONArray("glow"));
                if (g != null) t.glow = g;
                if (o.has("accent")) t.accent = parse(o.optString("accent"), Color.WHITE);
                t.dim = Math.max(0, Math.min(90, o.optInt("dim", t.dim)));
                if (o.has("wallpaper")) t.wallpaper = o.optString("wallpaper");
                t.slideshow = o.optBoolean("slideshow", false);
            } catch (Exception ignored) {
                // a broken theme.json just falls back to defaults
            }
        }
        if (t.wallpaperFile() == null && !t.slideshow) {
            t.wallpaper = null;
            if (!vids.isEmpty()) t.wallpaper = vids.get(0).getName();
            else if (imgs.size() == 1) t.wallpaper = imgs.get(0).getName();
            else if (imgs.size() > 1) t.slideshow = true;
        }
        return t;
    }

    public File wallpaperFile() {
        if (dir == null || wallpaper == null || wallpaper.length() == 0) return null;
        File f = new File(dir, wallpaper);
        return f.isFile() ? f : null;
    }

    private static int[] colorsOf(JSONArray a) {
        if (a == null || a.length() == 0) return null;
        int[] r = new int[3];
        for (int i = 0; i < 3; i++) r[i] = parse(a.optString(Math.min(i, a.length() - 1)), 0xFF000000);
        return r;
    }

    private static int parse(String s, int d) {
        try {
            return Color.parseColor(s.trim());
        } catch (Exception e) {
            return d;
        }
    }

    private static String readText(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
