package uz.osmon.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final String[] PREFERRED = {
            "top.rootu.lampa", "com.google.android.youtube.tv", "mobi.zona", "ru.kinopoisk.tv",
            "de.benzac.msx", "org.videolan.vlc", "com.android.vending"};

    Prefs prefs;
    int appliedRev = Integer.MIN_VALUE;
    float dp;
    int screenW, screenH;
    final Handler h = new Handler(Looper.getMainLooper());

    FrameLayout root, bgLayer;
    AuroraView aurora;
    ImageView imgA, imgB;
    BgVideo video;
    ScrollView scroll;
    LinearLayout favRow, inputsRow, grid;
    HorizontalScrollView favScroll, inputsScroll;
    TextView favTitle, inputsTitle, allTitle, clockTv, dateTv, greetTv;
    View settingsChip;

    Theme theme;
    int accent;
    List<AppInfo> apps = new ArrayList<AppInfo>();
    List<File> slides = new ArrayList<File>();
    int slideIdx;
    boolean showingA, resumed, initialFocus;
    String focusPkg;
    boolean focusInFav;
    BroadcastReceiver pkgReceiver;

    final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
    SimpleDateFormat dateFmt;
    String langApplied;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        langApplied = Lang.current(this);
        dateFmt = new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(
                getResources().getConfiguration().locale, "EEEEdMMMM"), getResources().getConfiguration().locale);
        dp = getResources().getDisplayMetrics().density;
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        pkgReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                loadApps();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);

        askStorage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (!Lang.current(this).equals(langApplied)) { // language changed in settings
            recreate();
            return;
        }
        if (prefs.rev() != appliedRev) rebuild();
        else resumeBackground();
        h.removeCallbacks(tick);
        tick.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        pauseBackground();
        h.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(pkgReceiver);
        } catch (Exception ignored) {
        }
        stopBackground();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        goTop();
    }

    @Override
    public void onBackPressed() {
        goTop(); // a launcher has nowhere to go back to
    }

    void goTop() {
        if (scroll != null) scroll.smoothScrollTo(0, 0);
        if (favScroll != null) favScroll.smoothScrollTo(0, 0);
        View first = firstCard();
        if (first != null) first.requestFocus();
    }

    // ------------------------------------------------------------------ storage permission

    void askStorage() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7);
        } else {
            ensureDirs();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        ensureDirs();
        appliedRev = Integer.MIN_VALUE;
        if (resumed) rebuild();
    }

    void ensureDirs() {
        try {
            new File(Theme.root(), "themes").mkdirs();
            new File(Theme.root(), "wallpapers").mkdirs();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ build

    void rebuild() {
        appliedRev = prefs.rev();
        stopBackground();
        root.removeAllViews();
        DisplayMetrics m = getResources().getDisplayMetrics();
        screenW = m.widthPixels;
        screenH = m.heightPixels;
        theme = Theme.find(this, prefs.str(Prefs.THEME, "tvos"));
        accent = theme.accent;
        initialFocus = true;
        buildBackground();
        buildContent();
        if (!apps.isEmpty()) fillRows();
        loadApps();
    }

    void buildBackground() {
        bgLayer = new FrameLayout(this);
        root.addView(bgLayer, new FrameLayout.LayoutParams(MATCH, MATCH));
        aurora = new AuroraView(this, theme.colors, theme.glow, prefs.flag(Prefs.ANIM, true));
        bgLayer.addView(aurora, new FrameLayout.LayoutParams(MATCH, MATCH));
        imgA = newImage();
        imgB = newImage();
        bgLayer.addView(imgA, new FrameLayout.LayoutParams(MATCH, MATCH));
        bgLayer.addView(imgB, new FrameLayout.LayoutParams(MATCH, MATCH));

        String mode = prefs.str(Prefs.WP_MODE, "theme");
        String path = prefs.str(Prefs.WP_PATH, "");
        File file = null, slideDir = null;
        if ("image".equals(mode) || "video".equals(mode)) {
            File f = new File(path);
            if (f.isFile()) file = f;
        } else if ("slideshow".equals(mode)) {
            slideDir = new File(path);
        } else if ("aerial".equals(mode) || theme.aerial) {
            startAerial();
        } else if (theme.slideshow && theme.dir != null) {
            slideDir = theme.dir;
        } else {
            file = theme.wallpaperFile();
        }
        if (slideDir != null) startSlideshow(slideDir);
        else if (Media.isVideo(file)) startVideo(file);
        else if (file != null) showImage(file, imgA);

        int dim = prefs.num(Prefs.DIM, -1);
        if (dim < 0) dim = theme.dim;
        View dimV = new View(this);
        dimV.setBackgroundColor(Color.argb(dim * 255 / 100, 0, 0, 0));
        root.addView(dimV, new FrameLayout.LayoutParams(MATCH, MATCH));

        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x1A000000, 0x99000000}));
        root.addView(bottom, new FrameLayout.LayoutParams(MATCH, MATCH));

        View top = new View(this);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x55000000, 0x00000000}));
        root.addView(top, new FrameLayout.LayoutParams(MATCH, dpi(150), Gravity.TOP));
    }

    ImageView newImage() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setFocusable(false);
        return v;
    }

    void buildContent() {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        root.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(dpi(44), dpi(28), dpi(44), dpi(64));
        scroll.addView(col, new ScrollView.LayoutParams(MATCH, WRAP));

        // top bar: greeting · settings · clock
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dpi(12), 0, dpi(12), 0);
        bar.setClipChildren(false);
        col.addView(bar, new LinearLayout.LayoutParams(MATCH, WRAP));

        greetTv = text(22, 0xE6FFFFFF, "sans-serif-light");
        bar.addView(greetTv, new LinearLayout.LayoutParams(0, WRAP, 1f));

        settingsChip = chip(getString(R.string.settings_chip));
        settingsChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(WRAP, WRAP);
        clp.rightMargin = dpi(26);
        bar.addView(settingsChip, clp);

        LinearLayout clockCol = new LinearLayout(this);
        clockCol.setOrientation(LinearLayout.VERTICAL);
        clockCol.setGravity(Gravity.END);
        clockTv = text(44, Color.WHITE, "sans-serif-light");
        clockTv.setIncludeFontPadding(false);
        dateTv = text(14, 0xCCFFFFFF, "sans-serif");
        clockCol.addView(clockTv);
        clockCol.addView(dateTv);
        if (!prefs.flag(Prefs.CLOCK, true)) clockCol.setVisibility(View.GONE);
        bar.addView(clockCol);

        // empty sky so the wallpaper can breathe, like on Apple TV
        col.addView(new Space(this), new LinearLayout.LayoutParams(1, (int) (screenH * 0.22f)));

        favTitle = title(getString(R.string.row_favorites));
        col.addView(favTitle);
        favScroll = hScroll();
        favRow = row();
        favScroll.addView(favRow);
        col.addView(favScroll, new LinearLayout.LayoutParams(MATCH, WRAP));

        inputsTitle = title(getString(R.string.row_inputs));
        col.addView(inputsTitle);
        inputsScroll = hScroll();
        inputsRow = row();
        inputsScroll.addView(inputsRow);
        col.addView(inputsScroll, new LinearLayout.LayoutParams(MATCH, WRAP));

        allTitle = title(getString(R.string.row_all));
        col.addView(allTitle);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        col.addView(grid, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    // ------------------------------------------------------------------ apps

    void loadApps() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppInfo> l = AppInfo.load(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        apps = l;
                        fillRows();
                    }
                });
            }
        }).start();
    }

    List<String> favorites() {
        List<String> f = prefs.list(Prefs.FAV);
        if (f != null) return f;
        f = new ArrayList<String>();
        Map<String, AppInfo> by = byPkg();
        for (String p : PREFERRED) if (by.containsKey(p)) f.add(p);
        for (AppInfo a : apps) {
            if (f.size() >= 6) break;
            if (!f.contains(a.pkg)) f.add(a.pkg);
        }
        return f;
    }

    Map<String, AppInfo> byPkg() {
        Map<String, AppInfo> m = new HashMap<String, AppInfo>();
        for (AppInfo a : apps) m.put(a.pkg, a);
        return m;
    }

    void fillRows() {
        if (favRow == null) return;
        int size = Math.max(0, Math.min(2, prefs.num(Prefs.CARD, 1)));
        int[] favW = {170, 200, 236};
        int[] allW = {124, 150, 180};
        int fw = dpi(favW[size]), fh = fw * 9 / 16;
        int aw = dpi(allW[size]), ah = aw * 9 / 16;
        int labels = prefs.num(Prefs.LABELS, 1);
        int gap = dpi(22);
        List<String> hidden = prefs.listOrEmpty(Prefs.HIDDEN);
        Map<String, AppInfo> by = byPkg();

        favRow.removeAllViews();
        for (String p : favorites()) {
            AppInfo a = by.get(p);
            if (a == null) continue;
            favRow.addView(appCard(a, fw, fh, labels, true), marginRight(gap));
        }
        int favVis = favRow.getChildCount() > 0 ? View.VISIBLE : View.GONE;
        favTitle.setVisibility(favVis);
        favScroll.setVisibility(favVis);

        inputsRow.removeAllViews();
        if (prefs.flag(Prefs.INPUTS, true)) {
            for (TvInputInfo ti : hdmiInputs()) inputsRow.addView(inputCard(ti, aw, ah, labels), marginRight(gap));
        }
        int inVis = inputsRow.getChildCount() > 0 ? View.VISIBLE : View.GONE;
        inputsTitle.setVisibility(inVis);
        inputsScroll.setVisibility(inVis);

        grid.removeAllViews();
        int avail = screenW - dpi(44) * 2 - dpi(24);
        int cols = Math.max(3, (avail + gap) / (aw + gap));
        LinearLayout cur = null;
        int n = 0;
        for (AppInfo a : apps) {
            if (hidden.contains(a.pkg)) continue;
            if (n % cols == 0) {
                cur = row();
                cur.setPadding(dpi(12), dpi(12), dpi(12), dpi(12));
                grid.addView(cur, new LinearLayout.LayoutParams(MATCH, WRAP));
            }
            cur.addView(appCard(a, aw, ah, labels, false), marginRight(gap));
            n++;
        }

        restoreFocus();
    }

    void restoreFocus() {
        View target = null;
        if (focusPkg != null) {
            target = findCard(focusInFav ? favRow : grid, focusPkg);
            if (target == null) target = findCard(focusInFav ? grid : favRow, focusPkg);
            focusPkg = null;
        }
        if (target == null && (initialFocus || getCurrentFocus() == null)) target = firstCard();
        if (target != null) {
            initialFocus = false;
            final View t = target;
            t.post(new Runnable() {
                @Override
                public void run() {
                    t.requestFocus();
                }
            });
        }
    }

    View findCard(ViewGroup g, String pkg) {
        if (g == null) return null;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (pkg.equals(c.getTag())) return c;
            if (c instanceof ViewGroup && c.getTag() == null) {
                View r = findCard((ViewGroup) c, pkg);
                if (r != null) return r;
            }
        }
        return null;
    }

    View firstCard() {
        if (favRow != null && favRow.getChildCount() > 0) return favRow.getChildAt(0);
        if (grid != null && grid.getChildCount() > 0) {
            ViewGroup r = (ViewGroup) grid.getChildAt(0);
            if (r.getChildCount() > 0) return r.getChildAt(0);
        }
        return settingsChip;
    }

    void launch(AppInfo a) {
        try {
            startActivity(a.intent);
        } catch (Throwable t) {
            try {
                Intent i = getPackageManager().getLaunchIntentForPackage(a.pkg);
                if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                else toast(getString(R.string.err_open_app, a.label));
            } catch (Throwable t2) {
                toast(getString(R.string.err_open_app, a.label));
            }
        }
    }

    void options(final AppInfo a, final boolean inFav) {
        final List<String> fav = new ArrayList<String>(favorites());
        final boolean isFav = fav.contains(a.pkg);
        final List<String> labels = new ArrayList<String>();
        final List<Integer> acts = new ArrayList<Integer>();
        labels.add(getString(R.string.act_open));
        acts.add(0);
        if (isFav) {
            labels.add(getString(R.string.act_unfav));
            acts.add(2);
        } else {
            labels.add(getString(R.string.act_fav));
            acts.add(1);
        }
        if (isFav && inFav) {
            labels.add(getString(R.string.act_left));
            acts.add(3);
            labels.add(getString(R.string.act_right));
            acts.add(4);
        }
        labels.add(getString(R.string.act_hide));
        acts.add(5);
        labels.add(getString(R.string.act_info));
        acts.add(6);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(a.label)
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        doAction(acts.get(which), a, fav, inFav);
                    }
                })
                .show();
    }

    void doAction(int act, AppInfo a, List<String> fav, boolean inFav) {
        int i = fav.indexOf(a.pkg);
        switch (act) {
            case 0:
                launch(a);
                return;
            case 1:
                fav.add(a.pkg);
                prefs.setList(Prefs.FAV, fav);
                toast(getString(R.string.toast_faved));
                break;
            case 2:
                fav.remove(a.pkg);
                prefs.setList(Prefs.FAV, fav);
                break;
            case 3:
                if (i > 0) Collections.swap(fav, i, i - 1);
                prefs.setList(Prefs.FAV, fav);
                break;
            case 4:
                if (i >= 0 && i < fav.size() - 1) Collections.swap(fav, i, i + 1);
                prefs.setList(Prefs.FAV, fav);
                break;
            case 5:
                List<String> hid = prefs.listOrEmpty(Prefs.HIDDEN);
                if (!hid.contains(a.pkg)) hid.add(a.pkg);
                prefs.setList(Prefs.HIDDEN, hid);
                fav.remove(a.pkg);
                prefs.setList(Prefs.FAV, fav);
                toast(getString(R.string.toast_hidden));
                break;
            case 6:
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable t) {
                    toast(getString(R.string.err_info));
                }
                return;
        }
        appliedRev = prefs.rev();
        focusPkg = a.pkg;
        focusInFav = inFav && (act == 3 || act == 4);
        fillRows();
    }

    // ------------------------------------------------------------------ HDMI inputs

    List<TvInputInfo> hdmiInputs() {
        List<TvInputInfo> out = new ArrayList<TvInputInfo>();
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return out;
            for (TvInputInfo ti : tim.getTvInputList()) {
                if (!ti.isPassthroughInput() || ti.getParentId() != null) continue;
                try { // TvInputInfo.isHidden(Context) appeared in API 24
                    Object hidden = TvInputInfo.class.getMethod("isHidden", Context.class).invoke(ti, this);
                    if (Boolean.TRUE.equals(hidden)) continue;
                } catch (Throwable ignored) {
                }
                out.add(ti);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    void openInput(TvInputInfo ti) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(ti.getId()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            toast(getString(R.string.err_input));
        }
    }

    // ------------------------------------------------------------------ cards

    View appCard(final AppInfo a, int w, int hgt, int labels, final boolean inFav) {
        FrameLayout card = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        int bg;
        if (a.banner != null) {
            iv.setImageDrawable(a.fresh(a.banner));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
            bg = 0xFF1E2028;
        } else {
            iv.setImageDrawable(a.fresh(a.icon));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int s = (int) (hgt * 0.56f);
            card.addView(iv, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
            bg = a.tile;
        }
        View box = cardBox(card, bg, w, hgt, a.label, labels);
        box.setTag(a.pkg);
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launch(a);
            }
        });
        box.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                options(a, inFav);
                return true;
            }
        });
        box.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int code, KeyEvent e) {
                if (code == KeyEvent.KEYCODE_MENU) {
                    if (e.getAction() == KeyEvent.ACTION_UP) options(a, inFav);
                    return true;
                }
                return false;
            }
        });
        return box;
    }

    View inputCard(final TvInputInfo ti, int w, int hgt, int labels) {
        String name;
        try {
            name = ti.loadLabel(this).toString();
        } catch (Throwable t) {
            name = getString(R.string.input_default);
        }
        FrameLayout card = new FrameLayout(this);
        TextView tv = text(17, Color.WHITE, "sans-serif-medium");
        tv.setText(name);
        tv.setGravity(Gravity.CENTER);
        card.addView(tv, new FrameLayout.LayoutParams(MATCH, MATCH));
        View box = cardBox(card, 0xFF2A2D36, w, hgt, name, labels == 1 ? 2 : labels);
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openInput(ti);
            }
        });
        return box;
    }

    /** Rounded card + label underneath, with the tvOS-style "lift" on focus. */
    View cardBox(final FrameLayout card, int bgColor, int w, int hgt, String labelText, final int labels) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setFocusable(true);
        box.setClipChildren(false);
        box.setClipToPadding(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(14 * dp);
        bg.setColor(bgColor);
        card.setBackground(bg);
        card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        card.setClipToOutline(true);
        // no shadows at rest: dozens of shadows redrawn every frame is what makes a weak TV lag
        box.addView(card, new LinearLayout.LayoutParams(w, hgt));

        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(14 * dp);
        ring.setColor(0x14FFFFFF);
        ring.setStroke(Math.max(2, (int) (2.5f * dp)), accent);

        final TextView label = text(13, Color.WHITE, "sans-serif-medium");
        label.setText(labelText);
        label.setShadowLayer(0, 0, 0, 0);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(w, WRAP);
        llp.topMargin = dpi(10);
        box.addView(label, llp);
        if (labels == 2) label.setVisibility(View.INVISIBLE);
        else if (labels == 1) label.setAlpha(0f);
        else label.setAlpha(0.8f);

        box.setPivotX(w / 2f);
        box.setPivotY(hgt / 2f);
        box.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                float s = has ? 1.12f : 1f;
                box.animate().scaleX(s).scaleY(s).setDuration(170).start();
                card.setForeground(has ? ring : null);
                card.setElevation(has ? 12 * dp : 0);
                box.setZ(has ? 10 : 0);
                if (labels == 1) label.animate().alpha(has ? 1f : 0f).setDuration(150).start();
                else if (labels == 0) label.setAlpha(has ? 1f : 0.8f);
            }
        });
        return box;
    }

    View chip(String s) {
        final TextView t = text(15, Color.WHITE, "sans-serif-medium");
        t.setText(s);
        t.setFocusable(true);
        t.setPadding(dpi(18), dpi(9), dpi(18), dpi(9));
        t.setShadowLayer(0, 0, 0, 0);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(22 * dp);
        bg.setColor(0x33FFFFFF);
        t.setBackground(bg);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                bg.setColor(has ? Color.WHITE : 0x33FFFFFF);
                t.setTextColor(has ? Color.BLACK : Color.WHITE);
                float s = has ? 1.08f : 1f;
                t.animate().scaleX(s).scaleY(s).setDuration(150).start();
            }
        });
        return t;
    }

    TextView title(String s) {
        TextView t = text(18, 0xEEFFFFFF, "sans-serif-medium");
        t.setText(s);
        t.setPadding(dpi(12), dpi(16), 0, 0);
        return t;
    }

    HorizontalScrollView hScroll() {
        HorizontalScrollView s = new HorizontalScrollView(this);
        s.setHorizontalScrollBarEnabled(false);
        s.setOverScrollMode(View.OVER_SCROLL_NEVER);
        s.setClipChildren(false);
        s.setClipToPadding(false);
        s.setPadding(0, dpi(14), 0, dpi(14));
        return s;
    }

    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        r.setPadding(dpi(12), 0, dpi(12), 0);
        return r;
    }

    LinearLayout.LayoutParams marginRight(int m) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.rightMargin = m;
        return lp;
    }

    TextView text(float sp, int color, String font) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(font, Typeface.NORMAL));
        t.setShadowLayer(6 * dp, 0, dp, 0x66000000);
        return t;
    }

    int dpi(float v) {
        return (int) (v * dp + 0.5f);
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ wallpaper

    // Video sits UNDER the aurora layer: the aurora covers the screen while a clip loads,
    // fades away on the first frame and fades back in between clips.

    List<Aerial.Clip> aerialClips;
    int aerialIdx, aerialErrors, bgGen;
    boolean aerialMode;

    BgVideo makeVideo() {
        BgVideo v = new BgVideo(this, new BgVideo.Listener() {
            @Override
            public void onFirstFrame() {
                aerialErrors = 0;
                if (aurora != null) {
                    aurora.animate().alpha(0f).setDuration(1200).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (aurora != null && aurora.getAlpha() == 0f) aurora.setRunning(false);
                        }
                    }).start();
                }
            }

            @Override
            public void onEnded() {
                if (!aerialMode || aurora == null) return;
                aurora.setRunning(true);
                aurora.animate().alpha(1f).setDuration(700).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        nextAerial();
                    }
                }).start();
            }

            @Override
            public void onFailed(int what, int extra) {
                android.util.Log.w("Osmon", "video failed what=" + what + " extra=" + extra);
                if (aurora != null) {
                    aurora.setAlpha(1f);
                    aurora.setRunning(true);
                }
                if (!aerialMode) {
                    toast(getString(R.string.err_video_file));
                    return;
                }
                aerialErrors++;
                if (aerialErrors >= 5) {
                    toast(getString(R.string.err_aerial, what + "/" + extra));
                    return;
                }
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        nextAerial();
                    }
                }, 1500);
            }
        });
        bgLayer.addView(v, 0, new FrameLayout.LayoutParams(MATCH, MATCH));
        return v;
    }

    void startVideo(File f) {
        aerialMode = false;
        video = makeVideo();
        video.play(f.getAbsolutePath(), true);
    }

    void startAerial() {
        aerialMode = true;
        video = makeVideo();
        final BgVideo v = video;
        final int gen = bgGen;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Aerial.Clip> clips = Aerial.load(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != bgGen || video != v) return; // background was rebuilt meanwhile
                        if (clips.isEmpty()) {
                            toast(getString(R.string.err_aerial_empty));
                            return;
                        }
                        aerialClips = clips;
                        aerialIdx = 0;
                        nextAerial();
                    }
                });
            }
        }).start();
    }

    void nextAerial() {
        if (video == null || aerialClips == null || aerialClips.isEmpty()) return;
        Aerial.Clip c = aerialClips.get(aerialIdx % aerialClips.size());
        aerialIdx++;
        android.util.Log.i("Osmon", "aerial play " + c.label + " " + c.url);
        video.play(c.url, false);
    }

    void showImage(final File f, final ImageView target) {
        final int w = screenW, hh = screenH;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bm = Media.decode(f, w, hh);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (target.getParent() == null || target.getParent() != bgLayer) return;
                        if (bm == null) {
                            toast(getString(R.string.err_image, f.getName()));
                            return;
                        }
                        target.setImageBitmap(bm);
                        target.bringToFront();
                        target.setAlpha(0f);
                        target.animate().alpha(1f).setDuration(1200).start();
                        final ImageView other = target == imgA ? imgB : imgA;
                        other.animate().alpha(0f).setDuration(1200).withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                if (other.getAlpha() == 0f) other.setImageDrawable(null);
                            }
                        }).start();
                        if (aurora != null) aurora.setRunning(false);
                    }
                });
            }
        }).start();
    }

    void startSlideshow(File dir) {
        slides = Media.images(dir);
        if (slides.isEmpty()) {
            toast(getString(R.string.err_no_pictures, dir.getName()));
            return;
        }
        Collections.shuffle(slides);
        slideIdx = 0;
        showingA = true;
        showImage(slides.get(0), imgA);
        h.removeCallbacks(slideTick);
        h.postDelayed(slideTick, slideDelay());
    }

    long slideDelay() {
        return Math.max(1, prefs.num(Prefs.SLIDE_MIN, 5)) * 60000L;
    }

    final Runnable slideTick = new Runnable() {
        @Override
        public void run() {
            if (slides.size() < 2) return;
            slideIdx = (slideIdx + 1) % slides.size();
            showingA = !showingA;
            showImage(slides.get(slideIdx), showingA ? imgA : imgB);
            h.postDelayed(this, slideDelay());
        }
    };

    boolean hasStillImage() {
        return (imgA != null && imgA.getDrawable() != null) || (imgB != null && imgB.getDrawable() != null);
    }

    void pauseBackground() {
        if (aurora != null) aurora.setRunning(false);
        if (video != null) video.setPlaying(false);
        h.removeCallbacks(slideTick);
    }

    void resumeBackground() {
        if (video != null) video.setPlaying(true);
        if (aurora != null && aurora.getAlpha() > 0f && !hasStillImage()) aurora.setRunning(true);
        if (slides.size() > 1) {
            h.removeCallbacks(slideTick);
            h.postDelayed(slideTick, slideDelay());
        }
    }

    void stopBackground() {
        bgGen++;
        aerialClips = null;
        h.removeCallbacks(slideTick);
        if (video != null) {
            video.release();
            video = null;
        }
        slides = new ArrayList<File>();
        if (aurora != null) aurora.setRunning(false);
    }

    // ------------------------------------------------------------------ clock

    final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (clockTv != null) {
                Date d = new Date();
                clockTv.setText(timeFmt.format(d));
                String ds = dateFmt.format(d);
                if (ds.length() > 0) ds = Character.toUpperCase(ds.charAt(0)) + ds.substring(1);
                dateTv.setText(ds);
                greetTv.setText(greeting());
            }
            h.postDelayed(this, 10000);
        }
    };

    String greeting() {
        int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hr >= 5 && hr < 12) return getString(R.string.greet_morning);
        if (hr >= 12 && hr < 17) return getString(R.string.greet_day);
        if (hr >= 17 && hr < 23) return getString(R.string.greet_evening);
        return getString(R.string.greet_night);
    }
}
