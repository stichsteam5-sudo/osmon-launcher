package uz.osmon.launcher;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** In-app language switch: wraps each activity's context with the chosen locale. */
public class Lang {
    public static final String KEY = "lang";

    /** Language tag + its own name. Empty tag = follow the TV's system language. */
    public static final String[][] LIST = {
            {"", null},
            {"en", "English"},
            {"ru", "Русский"},
            {"uz", "Oʻzbekcha"},
            {"kk", "Қазақша"},
            {"ky", "Кыргызча"},
            {"tg", "Тоҷикӣ"},
            {"uk", "Українська"},
            {"az", "Azərbaycan"},
            {"tr", "Türkçe"},
            {"de", "Deutsch"},
            {"fr", "Français"},
            {"es", "Español"},
            {"pt", "Português"},
            {"it", "Italiano"},
            {"pl", "Polski"},
            {"ar", "العربية"},
            {"hi", "हिन्दी"},
            {"id", "Bahasa Indonesia"},
            {"vi", "Tiếng Việt"},
            {"zh-CN", "中文（简体）"},
            {"ja", "日本語"},
            {"ko", "한국어"},
    };

    public static String current(Context c) {
        return c.getSharedPreferences("osmon", Context.MODE_PRIVATE).getString(KEY, "");
    }

    public static Context wrap(Context base) {
        String tag = current(base);
        if (tag.length() == 0) return base;
        Locale loc = Locale.forLanguageTag(tag);
        Locale.setDefault(loc);
        Configuration conf = new Configuration(base.getResources().getConfiguration());
        conf.setLocale(loc);
        return base.createConfigurationContext(conf);
    }
}
