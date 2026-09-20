package com.pattiz.wintablauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private FrameLayout root;
    private PopupWindow startMenu;
    private final Handler clockHandler = new Handler();
    private TextView clockText;
    private final ArrayList<AppEntry> allApps = new ArrayList<AppEntry>();
    private AppAdapter adapter;

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
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {
                        Color.rgb(5, 20, 45),
                        Color.rgb(8, 65, 125),
                        Color.rgb(5, 24, 58)
                });
        return g;
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
        getWindow().setStatusBarColor(Color.rgb(5, 20, 45));
        getWindow().setNavigationBarColor(Color.rgb(16, 19, 24));
        buildDesktop();
        loadApps();
    }

    private void buildDesktop() {
        root = new FrameLayout(this);
        root.setBackground(desktopGradient());

        TextView glow = text("WinTab 11", 32, Color.argb(85, 255, 255, 255));
        glow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(dp(300), dp(90));
        glowLp.gravity = Gravity.CENTER;
        root.addView(glow, glowLp);

        LinearLayout shortcuts = new LinearLayout(this);
        shortcuts.setOrientation(LinearLayout.VERTICAL);
        shortcuts.setPadding(dp(16), dp(16), 0, 0);
        shortcuts.addView(desktopShortcut("Apps", "▦", new View.OnClickListener() {
            @Override public void onClick(View v) { showStartMenu(); }
        }));
        shortcuts.addView(desktopShortcut("Settings", "⚙", new View.OnClickListener() {
            @Override public void onClick(View v) { openSettings(); }
        }));
        shortcuts.addView(desktopShortcut("Browser", "◎", new View.OnClickListener() {
            @Override public void onClick(View v) { openBrowser(); }
        }));
        shortcuts.addView(desktopShortcut("Files", "▰", new View.OnClickListener() {
            @Override public void onClick(View v) { openFiles(); }
        }));
        FrameLayout.LayoutParams shortcutsLp = new FrameLayout.LayoutParams(dp(120), dp(480));
        shortcutsLp.gravity = Gravity.TOP | Gravity.LEFT;
        root.addView(shortcuts, shortcutsLp);

        FrameLayout taskbar = new FrameLayout(this);
        taskbar.setBackgroundColor(Color.argb(242, 24, 28, 35));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.HORIZONTAL);
        center.setGravity(Gravity.CENTER);
        center.setPadding(dp(5), dp(5), dp(5), dp(5));

        TextView start = taskButton("⊞");
        start.setTextSize(28);
        start.setTextColor(Color.rgb(70, 170, 255));
        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleStartMenu(); }
        });
        center.addView(start, new LinearLayout.LayoutParams(dp(54), dp(48)));

        TextView search = taskButton(" Search ");
        search.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showStartMenu(); }
        });
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(dp(130), dp(42));
        searchLp.leftMargin = dp(6);
        center.addView(search, searchLp);

        TextView apps = taskButton("Apps");
        apps.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showStartMenu(); }
        });
        LinearLayout.LayoutParams appsLp = new LinearLayout.LayoutParams(dp(72), dp(44));
        appsLp.leftMargin = dp(6);
        center.addView(apps, appsLp);

        TextView settings = taskButton("⚙");
        settings.setTextSize(23);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSettings(); }
        });
        LinearLayout.LayoutParams setLp = new LinearLayout.LayoutParams(dp(52), dp(44));
        setLp.leftMargin = dp(6);
        center.addView(settings, setLp);

        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(58));
        centerLp.gravity = Gravity.CENTER;
        taskbar.addView(center, centerLp);

        clockText = text("", 13, Color.WHITE);
        FrameLayout.LayoutParams clockLp = new FrameLayout.LayoutParams(dp(115), dp(58));
        clockLp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        clockLp.rightMargin = dp(8);
        taskbar.addView(clockText, clockLp);
        startClock();

        FrameLayout.LayoutParams taskLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        taskLp.gravity = Gravity.BOTTOM;
        root.addView(taskbar, taskLp);

        setContentView(root);
    }

    private View desktopShortcut(String title, String symbol, View.OnClickListener listener) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        TextView icon = text(symbol, 30, Color.rgb(100, 190, 255));
        box.addView(icon, new LinearLayout.LayoutParams(dp(70), dp(50)));

        TextView label = text(title, 13, Color.WHITE);
        label.setShadowLayer(4, 0, 1, Color.BLACK);
        box.addView(label, new LinearLayout.LayoutParams(dp(105), dp(32)));

        box.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(110), dp(88));
        lp.bottomMargin = dp(4);
        box.setLayoutParams(lp);
        return box;
    }

    private TextView taskButton(String title) {
        TextView t = text(title, 14, Color.WHITE);
        t.setBackground(rounded(Color.argb(42, 255, 255, 255), 8));
        return t;
    }

    private void startClock() {
        clockHandler.post(new Runnable() {
            @Override public void run() {
                if (clockText != null) {
                    clockText.setText(new SimpleDateFormat(
                            "HH:mm\ndd.MM.yyyy", Locale.getDefault()).format(new Date()));
                }
                clockHandler.postDelayed(this, 30000);
            }
        });
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
            @Override public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
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
        panel.setBackground(rounded(Color.argb(250, 31, 36, 46), 16));

        TextView title = text("Start", 22, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        final EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.rgb(175, 180, 188));
        search.setHint("Search apps");
        search.setTextSize(15);
        search.setPadding(dp(15), 0, dp(15), 0);
        search.setBackground(rounded(Color.rgb(48, 54, 66), 9));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        searchLp.bottomMargin = dp(12);
        panel.addView(search, searchLp);

        GridView grid = new GridView(this);
        grid.setNumColumns(5);
        grid.setVerticalSpacing(dp(8));
        grid.setHorizontalSpacing(dp(8));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));

        adapter = new AppAdapter(this, new ArrayList<AppEntry>(allApps));
        grid.setAdapter(adapter);
        panel.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        TextView settings = taskButton("Settings");
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (startMenu != null) startMenu.dismiss();
                openSettings();
            }
        });
        bottom.addView(settings, new LinearLayout.LayoutParams(dp(95), dp(40)));

        TextView close = taskButton("Close");
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (startMenu != null) startMenu.dismiss();
            }
        });
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(80), dp(40));
        closeLp.leftMargin = dp(8);
        bottom.addView(close, closeLp);
        panel.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        startMenu = new PopupWindow(panel, dp(640), dp(555), true);
        startMenu.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        startMenu.setOutsideTouchable(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) startMenu.setElevation(dp(16));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                if (adapter != null) adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable e) {}
        });

        startMenu.showAtLocation(root, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(68));
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Settings could not be opened", Toast.LENGTH_SHORT).show();
        }
    }

    private void openBrowser() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No browser installed", Toast.LENGTH_SHORT).show();
        }
    }

    private void openFiles() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivity(Intent.createChooser(i, "Files"));
        } catch (Exception e) {
            Toast.makeText(this, "No file manager installed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (startMenu != null && startMenu.isShowing()) startMenu.dismiss();
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
            String needle = q == null ? "" : q.trim().toLowerCase(Locale.getDefault());
            for (AppEntry e : original) {
                if (needle.length() == 0 ||
                        e.label.toLowerCase(Locale.getDefault()).contains(needle)) {
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
            cell.setBackground(rounded(Color.argb(18, 255, 255, 255), 8));

            AppEntry app = shown.get(position);
            ImageView icon = new ImageView(context);
            icon.setImageDrawable(app.icon);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            cell.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

            TextView label = text(app.label, 11, Color.WHITE);
            label.setMaxLines(2);
            cell.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    AppEntry chosen = shown.get(position);
                    Intent launch = new Intent();
                    launch.setClassName(chosen.packageName, chosen.className);
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(launch);
                        if (startMenu != null) startMenu.dismiss();
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this,
                                "Could not open " + chosen.label,
                                Toast.LENGTH_SHORT).show();
                    }
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
    }
}
