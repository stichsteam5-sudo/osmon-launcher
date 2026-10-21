package uz.osmon.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Finding wallpaper files on the TV and decoding them at screen size. */
public class Media {
    private static final String[] IMG = {".jpg", ".jpeg", ".png", ".webp"};
    private static final String[] VID = {".mp4", ".mkv", ".webm", ".mov", ".m4v"};

    private static boolean ends(File f, String[] ext) {
        String n = f.getName().toLowerCase();
        if (n.startsWith(".")) return false;
        for (String e : ext) if (n.endsWith(e)) return true;
        return false;
    }

    public static boolean isImage(File f) { return f != null && ends(f, IMG); }

    public static boolean isVideo(File f) { return f != null && ends(f, VID); }

    private static List<File> list(File dir, boolean video) {
        List<File> out = new ArrayList<File>();
        File[] fs = dir == null ? null : dir.listFiles();
        if (fs == null) return out;
        Arrays.sort(fs);
        for (File f : fs) {
            if (!f.isFile()) continue;
            if (video ? isVideo(f) : isImage(f)) out.add(f);
        }
        return out;
    }

    public static List<File> images(File dir) { return list(dir, false); }

    public static List<File> videos(File dir) { return list(dir, true); }

    public static File sd() { return Environment.getExternalStorageDirectory(); }

    /** Folders we look in for user wallpapers. */
    public static List<File> roots() {
        File sd = sd();
        List<File> l = new ArrayList<File>();
        l.add(new File(Theme.root(), "wallpapers"));
        l.add(new File(sd, "Pictures"));
        l.add(new File(sd, "Movies"));
        l.add(new File(sd, "Download"));
        l.add(new File(sd, "DCIM"));
        return l;
    }

    /** Media files in the root folders and one level of sub-folders. */
    public static List<File> scan(boolean video) {
        List<File> out = new ArrayList<File>();
        for (File r : roots()) {
            out.addAll(list(r, video));
            File[] subs = r.listFiles();
            if (subs == null) continue;
            Arrays.sort(subs);
            for (File s : subs) if (s.isDirectory()) out.addAll(list(s, video));
        }
        return out;
    }

    /** Folders that contain at least two pictures (good for a slideshow). */
    public static List<File> slideDirs() {
        List<File> out = new ArrayList<File>();
        for (File r : roots()) {
            if (images(r).size() >= 2) out.add(r);
            File[] subs = r.listFiles();
            if (subs == null) continue;
            Arrays.sort(subs);
            for (File s : subs) if (s.isDirectory() && images(s).size() >= 2) out.add(s);
        }
        return out;
    }

    public static String shortName(File f) {
        String sd = sd().getAbsolutePath();
        String p = f.getAbsolutePath();
        return p.startsWith(sd) ? p.substring(sd.length() + 1) : p;
    }

    /** Decodes a picture no bigger than needed for a w x h screen. */
    public static Bitmap decode(File f, int w, int h) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (o.outWidth <= 0) return null;
            int s = 1;
            while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = s;
            d.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), d);
        } catch (Throwable t) {
            return null;
        }
    }
}
