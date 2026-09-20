package com.pattiz.wintablauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS = "wintab_prefs";
    private static final String PREF_DESKTOP = "desktop_apps_v3";
    private static final String PREF_TASKBAR = "taskbar_apps_v3";

    private final Locale tr = new Locale("tr", "TR");
    private final Handler handler = new Handler();
    private final ArrayList<AppEntry> allApps = new ArrayList<AppEntry>();

    private SharedPreferences prefs;
    private FrameLayout root;
    private LinearLayout desktopHost;
    private LinearLayout taskbarPinned;
    private TextView clockText;
    private TextView flyoutTimeText;
    private PopupWindow startMenu;
    private PopupWindow calendarPopup;
    private AppAdapter startAdapter;
    private Calendar shownMonth = Calendar.getInstance();

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable desktopGradient() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {
                        Color.rgb(4, 18, 42),
                        Color.rgb(7, 73, 145),
                        Color.rgb(5, 28, 68)
                });
    }

    private TextView text(String value, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(6), dp(8), dp(6));
        return t;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(4, 18, 42));
        getWindow().setNavigationBarColor(Color.rgb(16, 19, 24));

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadApps();
        initializeSelections();
        buildDesktop();
        startClockTicker();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs == null) return;
        loadApps();
        pruneSelections();
        rebuildDesktopIcons();
        rebuildTaskbar();
    }

    private void loadApps() {
        allApps.clear();
        PackageManager pm = getPackageManager();

        Intent query = new Intent(Intent.ACTION_MAIN, null);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(query, 0);

        for (ResolveInfo ri : list) {
            if (ri.activityInfo == null) continue;
            if (getPackageName().equals(ri.activityInfo.packageName)) continue;

            AppEntry e = new AppEntry();
            e.label = ri.loadLabel(pm).toString();
            e.icon = ri.loadIcon(pm);
            e.packageName = ri.activityInfo.packageName;
            e.className = ri.activityInfo.name;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(e.packageName, 0);
                e.systemApp = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        || (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            } catch (Exception ignored) {
                e.systemApp = false;
            }
            allApps.add(e);
        }

        Collections.sort(allApps, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
    }

    private void initializeSelections() {
        if (!prefs.contains(PREF_DESKTOP)) {
            ArrayList<String> defaults = chooseDefaults(7);
            saveKeys(PREF_DESKTOP, defaults);
        }

        if (!prefs.contains(PREF_TASKBAR)) {
            ArrayList<String> desktop = getKeys(PREF_DESKTOP);
            ArrayList<String> taskbar = new ArrayList<String>();
            for (String key : desktop) {
                if (taskbar.size() >= 5) break;
                taskbar.add(key);
            }
            saveKeys(PREF_TASKBAR, taskbar);
        }

        pruneSelections();
    }

    private ArrayList<String> chooseDefaults(int limit) {
        ArrayList<String> result = new ArrayList<String>();
        String[] preferred = {
                "ayar", "settings", "chrome", "browser", "dosya", "file",
                "kamera", "camera", "galeri", "gallery", "youtube", "play"
        };

        for (String needle : preferred) {
            for (AppEntry app : allApps) {
                if (result.size() >= limit) break;
                if (app.label.toLowerCase(tr).contains(needle)
                        && !result.contains(app.key())) {
                    result.add(app.key());
                    break;
                }
            }
        }

        for (AppEntry app : allApps) {
            if (result.size() >= limit) break;
            if (!result.contains(app.key())) result.add(app.key());
        }
        return result;
    }

    private ArrayList<String> getKeys(String prefName) {
        ArrayList<String> result = new ArrayList<String>();
        String raw = prefs.getString(prefName, "");
        if (raw == null || raw.length() == 0) return result;

        String[] parts = raw.split("\\n");
        for (String part : parts) {
            if (part.length() > 0) result.add(part);
        }
        return result;
    }

    private void saveKeys(String prefName, ArrayList<String> keys) {
        StringBuilder out = new StringBuilder();
        for (String key : keys) {
            if (out.length() > 0) out.append("\n");
            out.append(key);
        }
        prefs.edit().putString(prefName, out.toString()).apply();
    }

    private void pruneSelections() {
        pruneOne(PREF_DESKTOP);
        pruneOne(PREF_TASKBAR);
    }

    private void pruneOne(String prefName) {
        ArrayList<String> old = getKeys(prefName);
        ArrayList<String> clean = new ArrayList<String>();
        for (String key : old) {
            if (findByKey(key) != null && !clean.contains(key)) clean.add(key);
        }
        saveKeys(prefName, clean);
    }

    private AppEntry findByKey(String key) {
        for (AppEntry app : allApps) {
            if (app.key().equals(key)) return app;
        }
        return null;
    }

    private ArrayList<AppEntry> selectedApps(String prefName) {
        ArrayList<AppEntry> result = new ArrayList<AppEntry>();
        for (String key : getKeys(prefName)) {
            AppEntry app = findByKey(key);
            if (app != null) result.add(app);
        }
        return result;
    }

    private boolean isSelected(String prefName, AppEntry app) {
        return getKeys(prefName).contains(app.key());
    }

    private void setSelected(String prefName, AppEntry app, boolean selected) {
        ArrayList<String> keys = getKeys(prefName);
        if (selected) {
            if (!keys.contains(app.key())) keys.add(app.key());
        } else {
            keys.remove(app.key());
        }
        saveKeys(prefName, keys);

        if (PREF_DESKTOP.equals(prefName)) rebuildDesktopIcons();
        if (PREF_TASKBAR.equals(prefName)) rebuildTaskbar();
    }

    private void buildDesktop() {
        root = new FrameLayout(this);
        root.setBackground(desktopGradient());

        TextView watermark = text("WinTab 11", 33, Color.argb(70, 255, 255, 255));
        watermark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        FrameLayout.LayoutParams wmLp = new FrameLayout.LayoutParams(dp(320), dp(100));
        wmLp.gravity = Gravity.CENTER;
        root.addView(watermark, wmLp);

        desktopHost = new LinearLayout(this);
        desktopHost.setOrientation(LinearLayout.HORIZONTAL);
        desktopHost.setGravity(Gravity.TOP | Gravity.LEFT);

        FrameLayout.LayoutParams desktopLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        desktopLp.gravity = Gravity.TOP | Gravity.LEFT;
        desktopLp.leftMargin = dp(8);
        desktopLp.topMargin = dp(8);
        desktopLp.bottomMargin = dp(68);
        desktopLp.rightMargin = dp(8);
        root.addView(desktopHost, desktopLp);

        buildTaskbarShell();
        setContentView(root);
        rebuildDesktopIcons();
        rebuildTaskbar();
    }

    private void rebuildDesktopIcons() {
        if (desktopHost == null) return;
        desktopHost.removeAllViews();

        ArrayList<AppEntry> apps = selectedApps(PREF_DESKTOP);

        int heightPx = getResources().getDisplayMetrics().heightPixels;
        int usablePx = heightPx - dp(86);
        int rowHeight = dp(94);
        int rowsPerColumn = Math.max(1, usablePx / rowHeight);

        LinearLayout column = null;

        for (int i = 0; i < apps.size(); i++) {
            if (i % rowsPerColumn == 0) {
                column = new LinearLayout(this);
                column.setOrientation(LinearLayout.VERTICAL);
                column.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

                LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                        dp(116), ViewGroup.LayoutParams.MATCH_PARENT);
                colLp.rightMargin = dp(4);
                desktopHost.addView(column, colLp);
            }

            final AppEntry app = apps.get(i);
            View icon = desktopIcon(app);
            column.addView(icon, new LinearLayout.LayoutParams(dp(112), dp(92)));
        }
    }

    private View desktopIcon(final AppEntry app) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(3), dp(4), dp(3), dp(4));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        cell.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

        TextView label = text(app.label, 12, Color.WHITE);
        label.setMaxLines(2);
        label.setShadowLayer(4, 0, 1, Color.BLACK);
        cell.addView(label, new LinearLayout.LayoutParams(dp(106), dp(36)));

        cell.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launchApp(app); }
        });

        cell.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                showDesktopMenu(app);
                return true;
            }
        });

        return cell;
    }

    private void showDesktopMenu(final AppEntry app) {
        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(new String[] {"Aç", "Ana ekrandan kaldır"},
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) launchApp(app);
                                if (which == 1) setSelected(PREF_DESKTOP, app, false);
                            }
                        })
                .show();
    }

    private void buildTaskbarShell() {
        FrameLayout taskbar = new FrameLayout(this);
        taskbar.setBackgroundColor(Color.argb(247, 22, 26, 33));

        LinearLayout centerArea = new LinearLayout(this);
        centerArea.setOrientation(LinearLayout.HORIZONTAL);
        centerArea.setGravity(Gravity.CENTER_VERTICAL);

        TextView start = text("⊞", 28, Color.rgb(66, 170, 255));
        start.setBackground(rounded(Color.argb(28, 255, 255, 255), 8));
        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleStartMenu(); }
        });
        centerArea.addView(start, new LinearLayout.LayoutParams(dp(52), dp(48)));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);

        taskbarPinned = new LinearLayout(this);
        taskbarPinned.setOrientation(LinearLayout.HORIZONTAL);
        taskbarPinned.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(taskbarPinned, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(0, dp(52), 1);
        scrollLp.leftMargin = dp(6);
        centerArea.addView(scroll, scrollLp);

        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        centerLp.gravity = Gravity.CENTER_VERTICAL;
        centerLp.leftMargin = dp(120);
        centerLp.rightMargin = dp(138);
        taskbar.addView(centerArea, centerLp);

        clockText = text("", 13, Color.WHITE);
        clockText.setBackground(rounded(Color.argb(18, 255, 255, 255), 7));
        clockText.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleCalendar(); }
        });

        FrameLayout.LayoutParams clockLp = new FrameLayout.LayoutParams(dp(122), dp(54));
        clockLp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        clockLp.rightMargin = dp(8);
        taskbar.addView(clockText, clockLp);

        FrameLayout.LayoutParams taskLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        taskLp.gravity = Gravity.BOTTOM;
        root.addView(taskbar, taskLp);
    }

    private void rebuildTaskbar() {
        if (taskbarPinned == null) return;
        taskbarPinned.removeAllViews();

        for (final AppEntry app : selectedApps(PREF_TASKBAR)) {
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.icon);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setPadding(dp(8), dp(8), dp(8), dp(8));
            icon.setBackground(rounded(Color.argb(18, 255, 255, 255), 8));
            icon.setContentDescription(app.label);

            icon.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { launchApp(app); }
            });

            icon.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(app.label)
                            .setItems(new String[] {"Aç", "Görev çubuğundan kaldır"},
                                    new DialogInterface.OnClickListener() {
                                        @Override public void onClick(DialogInterface dialog, int which) {
                                            if (which == 0) launchApp(app);
                                            if (which == 1) setSelected(PREF_TASKBAR, app, false);
                                        }
                                    })
                            .show();
                    return true;
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(50), dp(48));
            lp.rightMargin = dp(5);
            taskbarPinned.addView(icon, lp);
        }
    }

    private void startClockTicker() {
        handler.post(new Runnable() {
            @Override public void run() {
                Date now = new Date();
                if (clockText != null) {
                    clockText.setText(new SimpleDateFormat("HH:mm\ndd.MM.yyyy", tr).format(now));
                }
                if (flyoutTimeText != null && calendarPopup != null && calendarPopup.isShowing()) {
                    flyoutTimeText.setText(new SimpleDateFormat("HH:mm:ss", tr).format(now));
                }
                handler.postDelayed(this, 1000);
            }
        });
    }

    private void toggleStartMenu() {
        if (startMenu != null && startMenu.isShowing()) startMenu.dismiss();
        else showStartMenu();
    }

    private void showStartMenu() {
        loadApps();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(16));
        panel.setBackground(rounded(Color.argb(250, 30, 35, 45), 16));

        TextView title = text("Başlat", 22, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        final EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.rgb(170, 176, 186));
        search.setHint("Uygulama ara");
        search.setTextSize(15);
        search.setPadding(dp(15), 0, dp(15), 0);
        search.setBackground(rounded(Color.rgb(47, 53, 65), 9));

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        searchLp.bottomMargin = dp(12);
        panel.addView(search, searchLp);

        TextView all = text("Tüm uygulamalar", 14, Color.rgb(220, 224, 230));
        all.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        panel.addView(all, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));

        GridView grid = new GridView(this);
        grid.setNumColumns(5);
        grid.setVerticalSpacing(dp(8));
        grid.setHorizontalSpacing(dp(8));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));

        startAdapter = new AppAdapter(new ArrayList<AppEntry>(allApps));
        grid.setAdapter(startAdapter);
        panel.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        TextView settings = taskButton("Ayarlar");
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startMenu.dismiss();
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        bottom.addView(settings, new LinearLayout.LayoutParams(dp(95), dp(40)));

        panel.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        startMenu = new PopupWindow(panel, dp(650), dp(560), true);
        startMenu.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        startMenu.setOutsideTouchable(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) startMenu.setElevation(dp(16));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                if (startAdapter != null) startAdapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable e) {}
        });

        search.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    InputMethodManager imm = (InputMethodManager)
                            getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        startMenu.showAtLocation(root, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(68));
    }

    private TextView taskButton(String value) {
        TextView t = text(value, 14, Color.WHITE);
        t.setBackground(rounded(Color.argb(38, 255, 255, 255), 8));
        return t;
    }

    private void showStartAppMenu(final AppEntry app) {
        final boolean onDesktop = isSelected(PREF_DESKTOP, app);
        final boolean onTaskbar = isSelected(PREF_TASKBAR, app);

        ArrayList<String> labels = new ArrayList<String>();
        labels.add("Aç");
        labels.add(onDesktop ? "Ana ekrandan kaldır" : "Ana ekrana ekle");
        labels.add(onTaskbar ? "Görev çubuğundan kaldır" : "Görev çubuğuna sabitle");
        labels.add(app.systemApp ? "Sistem uygulaması" : "Uygulamayı kaldır");

        final String[] items = labels.toArray(new String[labels.size()]);

        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) launchApp(app);
                        if (which == 1) setSelected(PREF_DESKTOP, app, !onDesktop);
                        if (which == 2) setSelected(PREF_TASKBAR, app, !onTaskbar);
                        if (which == 3) {
                            if (app.systemApp) {
                                Toast.makeText(MainActivity.this,
                                        "Sistem uygulamaları WinTab içinden kaldırılamaz.",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                uninstallApp(app);
                            }
                        }
                    }
                })
                .show();
    }

    private void uninstallApp(AppEntry app) {
        try {
            Intent uninstall = new Intent(Intent.ACTION_DELETE);
            uninstall.setData(Uri.parse("package:" + app.packageName));
            uninstall.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(uninstall);
        } catch (Exception e) {
            Toast.makeText(this, "Kaldırma ekranı açılamadı.", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleCalendar() {
        if (calendarPopup != null && calendarPopup.isShowing()) {
            calendarPopup.dismiss();
        } else {
            shownMonth = Calendar.getInstance();
            showCalendar();
        }
    }

    private void showCalendar() {
        final LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(14), dp(18), dp(14));
        panel.setBackground(rounded(Color.argb(252, 27, 29, 33), 10));

        flyoutTimeText = text("", 38, Color.rgb(225, 225, 225));
        flyoutTimeText.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        panel.addView(flyoutTimeText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        TextView fullDate = text(
                new SimpleDateFormat("EEEE, d MMMM", tr).format(new Date()),
                14,
                Color.rgb(205, 205, 205));
        fullDate.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        panel.addView(fullDate, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

        final LinearLayout monthHost = new LinearLayout(this);
        monthHost.setOrientation(LinearLayout.VERTICAL);
        panel.addView(monthHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));

        rebuildMonth(monthHost);

        TextView today = text(
                "Bugün • " + new SimpleDateFormat("d MMMM", tr).format(new Date()),
                14,
                Color.rgb(220, 220, 220));
        today.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        panel.addView(today, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        TextView reminder = text("Bir etkinlik veya anımsatıcı ekle", 13, Color.rgb(160, 160, 160));
        reminder.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        reminder.setBackground(rounded(Color.rgb(37, 39, 43), 3));
        panel.addView(reminder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(45)));

        calendarPopup = new PopupWindow(panel, dp(365), dp(575), true);
        calendarPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        calendarPopup.setOutsideTouchable(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) calendarPopup.setElevation(dp(16));
        calendarPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override public void onDismiss() { flyoutTimeText = null; }
        });

        calendarPopup.showAtLocation(root, Gravity.BOTTOM | Gravity.RIGHT, dp(8), dp(68));
    }

    private void rebuildMonth(final LinearLayout host) {
        host.removeAllViews();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView monthTitle = text(
                new SimpleDateFormat("MMMM yyyy", tr).format(shownMonth.getTime()),
                16,
                Color.rgb(210, 210, 210));
        monthTitle.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(monthTitle, new LinearLayout.LayoutParams(0, dp(48), 1));

        TextView prev = text("‹", 26, Color.rgb(220, 220, 220));
        prev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                shownMonth.add(Calendar.MONTH, -1);
                rebuildMonth(host);
            }
        });
        header.addView(prev, new LinearLayout.LayoutParams(dp(45), dp(45)));

        TextView next = text("›", 26, Color.rgb(220, 220, 220));
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                shownMonth.add(Calendar.MONTH, 1);
                rebuildMonth(host);
            }
        });
        header.addView(next, new LinearLayout.LayoutParams(dp(45), dp(45)));

        host.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(7);
        grid.setRowCount(7);

        String[] week = {"Pt","Sa","Ça","Pe","Cu","Ct","Pa"};
        for (String w : week) {
            TextView dayName = text(w, 11, Color.rgb(205, 205, 205));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(46);
            lp.height = dp(39);
            grid.addView(dayName, lp);
        }

        Calendar first = (Calendar) shownMonth.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int mondayOffset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        first.add(Calendar.DAY_OF_MONTH, -mondayOffset);

        Calendar today = Calendar.getInstance();

        for (int i = 0; i < 42; i++) {
            Calendar day = (Calendar) first.clone();
            day.add(Calendar.DAY_OF_MONTH, i);

            boolean inMonth = day.get(Calendar.MONTH) == shownMonth.get(Calendar.MONTH)
                    && day.get(Calendar.YEAR) == shownMonth.get(Calendar.YEAR);

            boolean isToday = day.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    && day.get(Calendar.MONTH) == today.get(Calendar.MONTH)
                    && day.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH);

            TextView cell = text(
                    String.valueOf(day.get(Calendar.DAY_OF_MONTH)),
                    14,
                    inMonth ? Color.rgb(220, 220, 220) : Color.rgb(95, 98, 103));

            if (isToday) {
                cell.setBackground(rounded(Color.rgb(0, 120, 215), 2));
                cell.setTextColor(Color.WHITE);
                cell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            }

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(46);
            lp.height = dp(43);
            lp.setMargins(dp(1), dp(1), dp(1), dp(1));
            grid.addView(cell, lp);
        }

        host.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(310)));
    }

    private void launchApp(AppEntry app) {
        Intent launch = new Intent();
        launch.setClassName(app.packageName, app.className);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(launch);
            if (startMenu != null && startMenu.isShowing()) startMenu.dismiss();
            if (calendarPopup != null && calendarPopup.isShowing()) calendarPopup.dismiss();
        } catch (Exception e) {
            Toast.makeText(this, app.label + " açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (calendarPopup != null && calendarPopup.isShowing()) {
            calendarPopup.dismiss();
        } else if (startMenu != null && startMenu.isShowing()) {
            startMenu.dismiss();
        }
    }

    private class AppAdapter extends BaseAdapter {
        private final ArrayList<AppEntry> original;
        private final ArrayList<AppEntry> shown;

        AppAdapter(ArrayList<AppEntry> entries) {
            original = new ArrayList<AppEntry>(entries);
            shown = new ArrayList<AppEntry>(entries);
        }

        void filter(String q) {
            shown.clear();
            String needle = q == null ? "" : q.trim().toLowerCase(tr);
            for (AppEntry app : original) {
                if (needle.length() == 0 || app.label.toLowerCase(tr).contains(needle)) {
                    shown.add(app);
                }
            }
            notifyDataSetChanged();
        }

        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LinearLayout cell = new LinearLayout(MainActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(6), dp(6), dp(6), dp(6));
            cell.setBackground(rounded(Color.argb(16, 255, 255, 255), 8));

            final AppEntry app = shown.get(position);

            ImageView icon = new ImageView(MainActivity.this);
            icon.setImageDrawable(app.icon);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            cell.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

            TextView label = text(app.label, 11, Color.WHITE);
            label.setMaxLines(2);
            cell.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { launchApp(app); }
            });

            cell.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    showStartAppMenu(app);
                    return true;
                }
            });

            return cell;
        }
    }

    private static class AppEntry {
        String label;
        android.graphics.drawable.Drawable icon;
        String packageName;
        String className;
        boolean systemApp;

        String key() {
            return packageName + "|" + className;
        }
    }
}
