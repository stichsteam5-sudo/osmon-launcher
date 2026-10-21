package uz.osmon.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    interface Pick {
        void on(int which);
    }

    abstract class Item {
        final String title;

        Item(String t) {
            title = t;
        }

        abstract String value();

        abstract void click();
    }

    Prefs prefs;
    float dp;
    LinearLayout list;
    int focusIndex = 0;
    final List<Item> items = new ArrayList<Item>();

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "";
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        dp = getResources().getDisplayMetrics().density;
        Theme t = Theme.find(this, prefs.str(Prefs.THEME, "tvos"));

        FrameLayout root = new FrameLayout(this);
        root.addView(new AuroraView(this, t.colors, t.glow, false), new FrameLayout.LayoutParams(MATCH, MATCH));
        View dim = new View(this);
        dim.setBackgroundColor(0x99000000);
        root.addView(dim, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int side = (int) (getResources().getDisplayMetrics().widthPixels * 0.16f);
        panel.setPadding(side, dpi(36), side, 0);
        root.addView(panel, new FrameLayout.LayoutParams(MATCH, MATCH));

        TextView title = text(30, Color.WHITE, "sans-serif-light");
        title.setText(getString(R.string.settings_title));
        panel.addView(title);
        TextView sub = text(14, 0xAAFFFFFF, "sans-serif");
        sub.setText(getString(R.string.settings_sub));
        sub.setPadding(0, dpi(2), 0, dpi(18));
        panel.addView(sub);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setPadding(dpi(8), dpi(6), dpi(8), dpi(40));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list, new ScrollView.LayoutParams(MATCH, WRAP));
        panel.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        setContentView(root);

        buildItems();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    // ------------------------------------------------------------------ items

    void buildItems() {
        items.add(new Item(getString(R.string.s_language)) {
            String value() {
                String cur = Lang.current(SettingsActivity.this);
                for (String[] l : Lang.LIST) if (l[0].equals(cur) && l[1] != null) return l[1];
                return getString(R.string.lang_system);
            }

            void click() {
                String cur = Lang.current(SettingsActivity.this);
                String[] o = new String[Lang.LIST.length];
                int checked = 0;
                for (int i = 0; i < Lang.LIST.length; i++) {
                    o[i] = Lang.LIST[i][1] == null ? getString(R.string.lang_system) : Lang.LIST[i][1];
                    if (Lang.LIST[i][0].equals(cur)) checked = i;
                }
                choose(title, o, checked, new Pick() {
                    public void on(int w) {
                        prefs.set(Lang.KEY, Lang.LIST[w][0]);
                        focusIndex = 0;
                        recreate();
                    }
                });
            }
        });
        items.add(new Item(getString(R.string.s_theme)) {
            String value() {
                return Theme.find(SettingsActivity.this, prefs.str(Prefs.THEME, "tvos")).name;
            }

            void click() {
                chooseTheme();
            }
        });
        items.add(new Item(getString(R.string.s_wallpaper)) {
            String value() {
                String mode = prefs.str(Prefs.WP_MODE, "theme");
                String name = new File(prefs.str(Prefs.WP_PATH, "")).getName();
                if ("image".equals(mode)) return getString(R.string.wp_image, name);
                if ("video".equals(mode)) return getString(R.string.wp_video, name);
                if ("slideshow".equals(mode)) return getString(R.string.wp_slideshow, name);
                if ("aerial".equals(mode)) return getString(R.string.wp_aerial);
                return getString(R.string.wp_theme);
            }

            void click() {
                chooseWallpaper();
            }
        });
        items.add(new Item(getString(R.string.s_slide)) {
            final int[] v = {1, 3, 5, 10, 30, 60};

            String value() {
                return getString(R.string.slide_every, prefs.num(Prefs.SLIDE_MIN, 5));
            }

            void click() {
                String[] o = new String[v.length];
                int cur = 2;
                for (int i = 0; i < v.length; i++) {
                    o[i] = getString(R.string.minutes, v[i]);
                    if (v[i] == prefs.num(Prefs.SLIDE_MIN, 5)) cur = i;
                }
                choose(title, o, cur, new Pick() {
                    public void on(int w) {
                        prefs.set(Prefs.SLIDE_MIN, v[w]);
                        render();
                    }
                });
            }
        });
        items.add(new Item(getString(R.string.s_dim)) {
            String value() {
                int d = prefs.num(Prefs.DIM, -1);
                return d < 0 ? getString(R.string.dim_theme) : d + "%";
            }

            void click() {
                final int[] v = {-1, 0, 10, 20, 30, 40, 50, 60, 70};
                String[] o = new String[v.length];
                int cur = 0;
                for (int i = 0; i < v.length; i++) {
                    o[i] = v[i] < 0 ? getString(R.string.dim_theme) : v[i] + "%";
                    if (v[i] == prefs.num(Prefs.DIM, -1)) cur = i;
                }
                choose(title, o, cur, new Pick() {
                    public void on(int w) {
                        prefs.set(Prefs.DIM, v[w]);
                        render();
                    }
                });
            }
        });
        items.add(toggle(getString(R.string.s_anim), Prefs.ANIM, true));
        items.add(new Item(getString(R.string.s_cards)) {
            final String[] o = {getString(R.string.card_small), getString(R.string.card_medium), getString(R.string.card_large)};

            String value() {
                return o[Math.max(0, Math.min(2, prefs.num(Prefs.CARD, 1)))];
            }

            void click() {
                choose(title, o, prefs.num(Prefs.CARD, 1), new Pick() {
                    public void on(int w) {
                        prefs.set(Prefs.CARD, w);
                        render();
                    }
                });
            }
        });
        items.add(new Item(getString(R.string.s_labels)) {
            final String[] o = {getString(R.string.labels_always), getString(R.string.labels_focused), getString(R.string.labels_never)};

            String value() {
                return o[Math.max(0, Math.min(2, prefs.num(Prefs.LABELS, 1)))];
            }

            void click() {
                choose(title, o, prefs.num(Prefs.LABELS, 1), new Pick() {
                    public void on(int w) {
                        prefs.set(Prefs.LABELS, w);
                        render();
                    }
                });
            }
        });
        items.add(toggle(getString(R.string.s_clock), Prefs.CLOCK, true));
        items.add(toggle(getString(R.string.s_inputs), Prefs.INPUTS, true));
        items.add(new Item(getString(R.string.s_hidden)) {
            String value() {
                int n = prefs.listOrEmpty(Prefs.HIDDEN).size();
                return n == 0 ? getString(R.string.none) : String.valueOf(n);
            }

            void click() {
                chooseHidden();
            }
        });
        items.add(new Item(getString(R.string.s_reset_fav)) {
            String value() {
                return "";
            }

            void click() {
                new AlertDialog.Builder(SettingsActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(getString(R.string.reset_fav_q))
                        .setMessage(getString(R.string.reset_fav_msg))
                        .setPositiveButton(getString(R.string.reset), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                prefs.remove(Prefs.FAV);
                                render();
                            }
                        })
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show();
            }
        });
        items.add(new Item(getString(R.string.s_howto)) {
            String value() {
                return "";
            }

            void click() {
                info(getString(R.string.howto_title), getString(R.string.howto_text));
            }
        });
        items.add(new Item(getString(R.string.s_android)) {
            String value() {
                return "";
            }

            void click() {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Throwable t) {
                    toast(getString(R.string.err_settings));
                }
            }
        });
        items.add(new Item(getString(R.string.s_about)) {
            String value() {
                return "Osmon " + version();
            }

            void click() {
                info("Osmon " + version(), getString(R.string.about_text));
            }
        });
    }

    Item toggle(String t, final String key, final boolean def) {
        return new Item(t) {
            String value() {
                return prefs.flag(key, def) ? getString(R.string.on) : getString(R.string.off);
            }

            void click() {
                prefs.set(key, !prefs.flag(key, def));
                render();
            }
        };
    }

    // ------------------------------------------------------------------ dialogs

    void chooseTheme() {
        final List<Theme> ts = Theme.all(this);
        String cur = prefs.str(Prefs.THEME, "tvos");
        String[] names = new String[ts.size()];
        int checked = 0;
        for (int i = 0; i < ts.size(); i++) {
            Theme t = ts.get(i);
            names[i] = t.name + (t.pack ? "   " + getString(R.string.from_folder) : "");
            if (t.id.equals(cur)) checked = i;
        }
        choose(getString(R.string.s_theme), names, checked, new Pick() {
            public void on(int w) {
                Theme t = ts.get(w);
                prefs.set(Prefs.THEME, t.id);
                prefs.set(Prefs.WP_MODE, "theme");
                toast(getString(R.string.theme_applied, t.name));
                render();
            }
        });
    }

    void chooseWallpaper() {
        final String[] o = {getString(R.string.wp_theme), getString(R.string.wp_opt_aerial), getString(R.string.wp_opt_image), getString(R.string.wp_opt_video),
                getString(R.string.wp_opt_slideshow)};
        String mode = prefs.str(Prefs.WP_MODE, "theme");
        int cur = "aerial".equals(mode) ? 1 : "image".equals(mode) ? 2 : "video".equals(mode) ? 3
                : "slideshow".equals(mode) ? 4 : 0;
        choose(getString(R.string.s_wallpaper), o, cur, new Pick() {
            public void on(int w) {
                if (w == 0 || w == 1) {
                    prefs.set(Prefs.WP_MODE, w == 0 ? "theme" : "aerial");
                    render();
                    return;
                }
                if (!storageOk()) return;
                if (w == 2 || w == 3) pickFile(w == 3);
                else pickSlideDir();
            }
        });
    }

    void pickFile(final boolean video) {
        final List<File> files = Media.scan(video);
        if (files.isEmpty()) {
            info(getString(R.string.files_not_found), getString(video ? R.string.no_videos : R.string.no_images));
            return;
        }
        String[] names = new String[files.size()];
        for (int i = 0; i < files.size(); i++) names[i] = Media.shortName(files.get(i));
        choose(getString(video ? R.string.pick_video : R.string.pick_image), names, -1, new Pick() {
            public void on(int w) {
                prefs.set(Prefs.WP_PATH, files.get(w).getAbsolutePath());
                prefs.set(Prefs.WP_MODE, video ? "video" : "image");
                render();
            }
        });
    }

    void pickSlideDir() {
        final List<File> dirs = Media.slideDirs();
        if (dirs.isEmpty()) {
            info(getString(R.string.dirs_not_found), getString(R.string.dirs_msg));
            return;
        }
        String[] names = new String[dirs.size()];
        for (int i = 0; i < dirs.size(); i++) {
            names[i] = Media.shortName(dirs.get(i)) + "  (" + Media.images(dirs.get(i)).size() + ")";
        }
        choose(getString(R.string.pick_folder), names, -1, new Pick() {
            public void on(int w) {
                prefs.set(Prefs.WP_PATH, dirs.get(w).getAbsolutePath());
                prefs.set(Prefs.WP_MODE, "slideshow");
                render();
            }
        });
    }

    void chooseHidden() {
        toast(getString(R.string.loading_list));
        new Thread(new Runnable() {
            public void run() {
                final List<AppInfo> apps = AppInfo.load(SettingsActivity.this);
                runOnUiThread(new Runnable() {
                    public void run() {
                        showHidden(apps);
                    }
                });
            }
        }).start();
    }

    void showHidden(final List<AppInfo> apps) {
        final List<String> hidden = prefs.listOrEmpty(Prefs.HIDDEN);
        String[] names = new String[apps.size()];
        final boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            names[i] = apps.get(i).label;
            checked[i] = hidden.contains(apps.get(i).pkg);
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.hide_title))
                .setMultiChoiceItems(names, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface d, int w, boolean on) {
                        checked[w] = on;
                    }
                })
                .setPositiveButton(getString(R.string.done), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        List<String> out = new ArrayList<String>();
                        for (int i = 0; i < apps.size(); i++) if (checked[i]) out.add(apps.get(i).pkg);
                        prefs.setList(Prefs.HIDDEN, out);
                        render();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    void choose(String title, String[] opts, int checked, final Pick p) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setSingleChoiceItems(opts, checked, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        d.dismiss();
                        p.on(w);
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    void info(String title, String msg) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(getString(R.string.got_it), null)
                .show();
    }

    boolean storageOk() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE}, 8);
        toast(getString(R.string.perm_needed));
        return false;
    }

    // ------------------------------------------------------------------ list rendering

    void render() {
        if (list == null) return;
        list.removeAllViews();
        View toFocus = null;
        for (int i = 0; i < items.size(); i++) {
            View r = rowView(items.get(i), i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            lp.bottomMargin = dpi(8);
            list.addView(r, lp);
            if (i == focusIndex) toFocus = r;
        }
        if (toFocus != null) {
            final View f = toFocus;
            f.post(new Runnable() {
                public void run() {
                    f.requestFocus();
                }
            });
        }
    }

    View rowView(final Item it, final int index) {
        final LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setPadding(dpi(22), dpi(14), dpi(22), dpi(14));
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12 * dp);
        bg.setColor(0x1FFFFFFF);
        r.setBackground(bg);

        final TextView t = text(17, Color.WHITE, "sans-serif-medium");
        t.setText(it.title);
        r.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        final TextView v = text(15, 0xBBFFFFFF, "sans-serif");
        v.setText(it.value());
        v.setGravity(Gravity.END);
        r.addView(v, new LinearLayout.LayoutParams(WRAP, WRAP));

        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View view, boolean has) {
                bg.setColor(has ? 0xF2FFFFFF : 0x1FFFFFFF);
                t.setTextColor(has ? Color.BLACK : Color.WHITE);
                v.setTextColor(has ? 0xCC000000 : 0xBBFFFFFF);
                float s = has ? 1.02f : 1f;
                r.animate().scaleX(s).scaleY(s).setDuration(140).start();
                if (has) focusIndex = index;
            }
        });
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                focusIndex = index;
                it.click();
            }
        });
        return r;
    }

    TextView text(float sp, int color, String font) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(font, Typeface.NORMAL));
        return t;
    }

    int dpi(float v) {
        return (int) (v * dp + 0.5f);
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
