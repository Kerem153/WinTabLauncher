package com.pattiz.wintablauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.GridView;
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

    private final Locale tr = new Locale("tr", "TR");
    private final Handler handler = new Handler();

    private FrameLayout root;
    private TextView clockText;
    private TextView flyoutTimeText;

    private PopupWindow startMenu;
    private PopupWindow calendarPopup;
    private PopupWindow keyboardPopup;

    private EditText activeEditText;
    private AppAdapter startAdapter;

    private final ArrayList<AppEntry> allApps = new ArrayList<AppEntry>();
    private final ArrayList<AppEntry> desktopApps = new ArrayList<AppEntry>();
    private final ArrayList<AppEntry> taskbarApps = new ArrayList<AppEntry>();

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

        loadApps();
        chooseVisibleApps();
        buildDesktop();
        startClockTicker();
    }

    private void loadApps() {
        allApps.clear();

        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
        for (ResolveInfo ri : list) {
            if (ri.activityInfo == null) continue;
            if (getPackageName().equals(ri.activityInfo.packageName)) continue;

            AppEntry e = new AppEntry();
            e.label = ri.loadLabel(pm).toString();
            e.icon = ri.loadIcon(pm);
            e.packageName = ri.activityInfo.packageName;
            e.className = ri.activityInfo.name;
            allApps.add(e);
        }

        Collections.sort(allApps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
    }

    private void chooseVisibleApps() {
        desktopApps.clear();
        taskbarApps.clear();

        for (int i = 0; i < allApps.size() && desktopApps.size() < 12; i++) {
            desktopApps.add(allApps.get(i));
        }

        ArrayList<String> wanted = new ArrayList<String>();
        wanted.add("chrome");
        wanted.add("browser");
        wanted.add("youtube");
        wanted.add("camera");
        wanted.add("gallery");
        wanted.add("files");
        wanted.add("settings");
        wanted.add("play");

        for (String key : wanted) {
            for (AppEntry app : allApps) {
                if (taskbarApps.size() >= 5) break;
                String label = app.label.toLowerCase(tr);
                if (label.contains(key) && !containsPackage(taskbarApps, app.packageName)) {
                    taskbarApps.add(app);
                    break;
                }
            }
        }

        for (AppEntry app : allApps) {
            if (taskbarApps.size() >= 5) break;
            if (!containsPackage(taskbarApps, app.packageName)) taskbarApps.add(app);
        }
    }

    private boolean containsPackage(ArrayList<AppEntry> list, String pkg) {
        for (AppEntry app : list) {
            if (app.packageName.equals(pkg)) return true;
        }
        return false;
    }

    private void buildDesktop() {
        root = new FrameLayout(this);
        root.setBackground(desktopGradient());

        TextView watermark = text("WinTab 11", 33, Color.argb(70, 255, 255, 255));
        watermark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        FrameLayout.LayoutParams wmLp = new FrameLayout.LayoutParams(dp(320), dp(100));
        wmLp.gravity = Gravity.CENTER;
        root.addView(watermark, wmLp);

        GridView desktopGrid = new GridView(this);
        desktopGrid.setNumColumns(2);
        desktopGrid.setVerticalSpacing(dp(4));
        desktopGrid.setHorizontalSpacing(dp(4));
        desktopGrid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        desktopGrid.setAdapter(new DesktopAdapter(this, desktopApps));

        FrameLayout.LayoutParams desktopLp = new FrameLayout.LayoutParams(dp(230), dp(620));
        desktopLp.gravity = Gravity.TOP | Gravity.LEFT;
        desktopLp.leftMargin = dp(8);
        desktopLp.topMargin = dp(8);
        desktopLp.bottomMargin = dp(70);
        root.addView(desktopGrid, desktopLp);

        buildTaskbar();
        setContentView(root);
    }

    private void buildTaskbar() {
        FrameLayout taskbar = new FrameLayout(this);
        taskbar.setBackgroundColor(Color.argb(246, 22, 26, 33));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.HORIZONTAL);
        center.setGravity(Gravity.CENTER);
        center.setPadding(dp(5), dp(5), dp(5), dp(5));

        TextView start = text("⊞", 28, Color.rgb(66, 170, 255));
        start.setBackground(rounded(Color.argb(28, 255, 255, 255), 8));
        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleStartMenu(); }
        });
        center.addView(start, new LinearLayout.LayoutParams(dp(52), dp(48)));

        for (final AppEntry app : taskbarApps) {
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.icon);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setPadding(dp(8), dp(8), dp(8), dp(8));
            icon.setBackground(rounded(Color.argb(18, 255, 255, 255), 8));
            icon.setContentDescription(app.label);
            icon.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { launchApp(app); }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(50), dp(48));
            lp.leftMargin = dp(5);
            center.addView(icon, lp);
        }

        TextView keyboard = text("⌨", 22, Color.WHITE);
        keyboard.setBackground(rounded(Color.argb(28, 255, 255, 255), 8));
        keyboard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showKeyboardForSearch(); }
        });
        LinearLayout.LayoutParams keyboardLp = new LinearLayout.LayoutParams(dp(52), dp(48));
        keyboardLp.leftMargin = dp(5);
        center.addView(keyboard, keyboardLp);

        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(58));
        centerLp.gravity = Gravity.CENTER;
        taskbar.addView(center, centerLp);

        clockText = text("", 13, Color.WHITE);
        clockText.setBackground(rounded(Color.argb(18, 255, 255, 255), 7));
        clockText.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleCalendar(); }
        });

        FrameLayout.LayoutParams clockLp = new FrameLayout.LayoutParams(dp(118), dp(54));
        clockLp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        clockLp.rightMargin = dp(8);
        taskbar.addView(clockText, clockLp);

        FrameLayout.LayoutParams taskLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        taskLp.gravity = Gravity.BOTTOM;
        root.addView(taskbar, taskLp);
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
        if (startMenu != null && startMenu.isShowing()) {
            startMenu.dismiss();
        } else {
            showStartMenu(false);
        }
    }

    private void showStartMenu(boolean focusSearch) {
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
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.rgb(170, 176, 186));
        search.setHint("Uygulama ara");
        search.setTextSize(15);
        search.setPadding(dp(15), 0, dp(15), 0);
        search.setBackground(rounded(Color.rgb(47, 53, 65), 9));
        search.setShowSoftInputOnFocus(false);
        activeEditText = search;

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        searchLp.bottomMargin = dp(12);
        panel.addView(search, searchLp);

        TextView pinned = text("Tüm uygulamalar", 14, Color.rgb(220, 224, 230));
        pinned.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        panel.addView(pinned, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));

        GridView grid = new GridView(this);
        grid.setNumColumns(5);
        grid.setVerticalSpacing(dp(8));
        grid.setHorizontalSpacing(dp(8));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));

        startAdapter = new AppAdapter(this, new ArrayList<AppEntry>(allApps));
        grid.setAdapter(startAdapter);
        panel.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        TextView settings = taskButton("Ayarlar");
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (startMenu != null) startMenu.dismiss();
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        bottom.addView(settings, new LinearLayout.LayoutParams(dp(95), dp(40)));

        TextView keyboard = taskButton("Klavye");
        keyboard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                activeEditText = search;
                search.requestFocus();
                showKeyboard();
            }
        });
        LinearLayout.LayoutParams kbLp = new LinearLayout.LayoutParams(dp(95), dp(40));
        kbLp.leftMargin = dp(8);
        bottom.addView(keyboard, kbLp);

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

        startMenu.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override public void onDismiss() {
                if (keyboardPopup != null && keyboardPopup.isShowing()) keyboardPopup.dismiss();
                activeEditText = null;
            }
        });

        startMenu.showAtLocation(root, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(68));

        if (focusSearch) {
            search.requestFocus();
            handler.postDelayed(new Runnable() {
                @Override public void run() { showKeyboard(); }
            }, 160);
        }
    }

    private TextView taskButton(String value) {
        TextView t = text(value, 14, Color.WHITE);
        t.setBackground(rounded(Color.argb(38, 255, 255, 255), 8));
        return t;
    }

    private void showKeyboardForSearch() {
        if (startMenu == null || !startMenu.isShowing()) {
            showStartMenu(true);
        } else {
            showKeyboard();
        }
    }

    private void showKeyboard() {
        if (activeEditText == null) return;

        if (keyboardPopup != null && keyboardPopup.isShowing()) {
            keyboardPopup.dismiss();
            return;
        }

        LinearLayout board = new LinearLayout(this);
        board.setOrientation(LinearLayout.VERTICAL);
        board.setPadding(dp(10), dp(10), dp(10), dp(10));
        board.setBackground(rounded(Color.argb(252, 28, 32, 40), 12));

        addKeyboardRow(board, new String[]{"Q","W","E","R","T","Y","U","I","O","P","Ğ","Ü"});
        addKeyboardRow(board, new String[]{"A","S","D","F","G","H","J","K","L","Ş","İ"});
        addKeyboardRow(board, new String[]{"Z","X","C","V","B","N","M","Ö","Ç","⌫"});

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);

        TextView clear = keyboardKey("Temizle");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (activeEditText != null) activeEditText.setText("");
            }
        });
        bottom.addView(clear, new LinearLayout.LayoutParams(dp(90), dp(45)));

        TextView space = keyboardKey("Boşluk");
        space.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { insertText(" "); }
        });
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(dp(260), dp(45));
        spLp.leftMargin = dp(6);
        bottom.addView(space, spLp);

        TextView close = keyboardKey("Kapat");
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (keyboardPopup != null) keyboardPopup.dismiss();
            }
        });
        LinearLayout.LayoutParams clLp = new LinearLayout.LayoutParams(dp(90), dp(45));
        clLp.leftMargin = dp(6);
        bottom.addView(close, clLp);

        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(49));
        bottomLp.topMargin = dp(4);
        board.addView(bottom, bottomLp);

        keyboardPopup = new PopupWindow(board, dp(720), dp(230), false);
        keyboardPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        keyboardPopup.setOutsideTouchable(false);
        if (android.os.Build.VERSION.SDK_INT >= 21) keyboardPopup.setElevation(dp(18));
        keyboardPopup.showAtLocation(root, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(66));
    }

    private void addKeyboardRow(LinearLayout board, String[] keys) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);

        for (final String key : keys) {
            TextView b = keyboardKey(key);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if ("⌫".equals(key)) {
                        backspace();
                    } else {
                        insertText(key.toLowerCase(tr));
                    }
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(45));
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            row.addView(b, lp);
        }

        board.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(49)));
    }

    private TextView keyboardKey(String value) {
        TextView key = text(value, 14, Color.WHITE);
        key.setBackground(rounded(Color.rgb(48, 54, 65), 6));
        return key;
    }

    private void insertText(String value) {
        if (activeEditText == null) return;
        int start = Math.max(activeEditText.getSelectionStart(), 0);
        int end = Math.max(activeEditText.getSelectionEnd(), 0);
        int min = Math.min(start, end);
        int max = Math.max(start, end);
        activeEditText.getText().replace(min, max, value);
    }

    private void backspace() {
        if (activeEditText == null) return;
        int start = activeEditText.getSelectionStart();
        int end = activeEditText.getSelectionEnd();

        if (start != end && start >= 0 && end >= 0) {
            activeEditText.getText().delete(Math.min(start, end), Math.max(start, end));
        } else if (start > 0) {
            activeEditText.getText().delete(start - 1, start);
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
            final Calendar day = (Calendar) first.clone();
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
            if (keyboardPopup != null && keyboardPopup.isShowing()) keyboardPopup.dismiss();
        } catch (Exception e) {
            Toast.makeText(this, app.label + " açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (keyboardPopup != null && keyboardPopup.isShowing()) {
            keyboardPopup.dismiss();
        } else if (calendarPopup != null && calendarPopup.isShowing()) {
            calendarPopup.dismiss();
        } else if (startMenu != null && startMenu.isShowing()) {
            startMenu.dismiss();
        }
    }

    private class DesktopAdapter extends BaseAdapter {
        private final Context context;
        private final ArrayList<AppEntry> apps;

        DesktopAdapter(Context context, ArrayList<AppEntry> apps) {
            this.context = context;
            this.apps = apps;
        }

        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int position) { return apps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(4), dp(5), dp(4), dp(5));

            final AppEntry app = apps.get(position);

            ImageView icon = new ImageView(context);
            icon.setImageDrawable(app.icon);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            cell.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

            TextView label = text(app.label, 12, Color.WHITE);
            label.setMaxLines(2);
            label.setShadowLayer(4, 0, 1, Color.BLACK);
            cell.addView(label, new LinearLayout.LayoutParams(dp(100), dp(38)));

            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { launchApp(app); }
            });
            return cell;
        }
    }

    private class AppAdapter extends BaseAdapter {
        private final Context context;
        private final ArrayList<AppEntry> original;
        private final ArrayList<AppEntry> shown;

        AppAdapter(Context context, ArrayList<AppEntry> entries) {
            this.context = context;
            this.original = new ArrayList<AppEntry>(entries);
            this.shown = new ArrayList<AppEntry>(entries);
        }

        void filter(String q) {
            shown.clear();
            String needle = q == null ? "" : q.trim().toLowerCase(tr);

            for (AppEntry e : original) {
                if (needle.length() == 0 || e.label.toLowerCase(tr).contains(needle)) {
                    shown.add(e);
                }
            }
            notifyDataSetChanged();
        }

        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(6), dp(6), dp(6), dp(6));
            cell.setBackground(rounded(Color.argb(16, 255, 255, 255), 8));

            final AppEntry app = shown.get(position);

            ImageView icon = new ImageView(context);
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

            return cell;
        }
    }

    private static class AppEntry {
        String label;
        android.graphics.drawable.Drawable icon;
        String packageName;
        String className;
    }
}
