package uz.osmon.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Small wrapper around SharedPreferences. Every change bumps "rev" so the home screen knows to rebuild. */
public class Prefs {
    public static final String THEME = "theme";
    public static final String WP_MODE = "wpMode";      // theme | image | video | slideshow
    public static final String WP_PATH = "wpPath";
    public static final String SLIDE_MIN = "slideMin";
    public static final String DIM = "dim";             // -1 = as in theme
    public static final String ANIM = "anim";
    public static final String CARD = "card";           // 0 small, 1 medium, 2 large
    public static final String LABELS = "labels";       // 0 always, 1 on focus, 2 never
    public static final String CLOCK = "clock";
    public static final String INPUTS = "inputs";
    public static final String FAV = "fav";
    public static final String HIDDEN = "hidden";

    private final SharedPreferences p;

    public Prefs(Context c) {
        p = c.getSharedPreferences("osmon", Context.MODE_PRIVATE);
    }

    public int rev() { return p.getInt("rev", 0); }

    public String str(String k, String d) { return p.getString(k, d); }

    public int num(String k, int d) { return p.getInt(k, d); }

    public boolean flag(String k, boolean d) { return p.getBoolean(k, d); }

    private SharedPreferences.Editor edit() { return p.edit().putInt("rev", rev() + 1); }

    public void set(String k, String v) { edit().putString(k, v).apply(); }

    public void set(String k, int v) { edit().putInt(k, v).apply(); }

    public void set(String k, boolean v) { edit().putBoolean(k, v).apply(); }

    public void remove(String k) { edit().remove(k).apply(); }

    /** Returns null when the list was never saved. */
    public List<String> list(String k) {
        String s = p.getString(k, null);
        if (s == null) return null;
        List<String> out = new ArrayList<String>();
        for (String x : s.split("\\|")) if (x.length() > 0) out.add(x);
        return out;
    }

    public List<String> listOrEmpty(String k) {
        List<String> l = list(k);
        return l == null ? new ArrayList<String>() : l;
    }

    public void setList(String k, List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String x : l) {
            if (sb.length() > 0) sb.append('|');
            sb.append(x);
        }
        edit().putString(k, sb.toString()).apply();
    }
}
