package com.pattiz.wintabkeyboard;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class WinTabInputMethodService extends InputMethodService {

    private final Locale tr = new Locale("tr", "TR");
    private LinearLayout root;
    private boolean shift = false;
    private boolean symbols = false;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable bg(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.setBackgroundColor(Color.rgb(24, 28, 35));
        rebuild();
        return root;
    }

    private void rebuild() {
        if (root == null) return;
        root.removeAllViews();

        if (symbols) {
            addRow(new String[]{"1","2","3","4","5","6","7","8","9","0"});
            addRow(new String[]{"@","#","₺","_","&","-","+","(",")","/"});
            addRow(new String[]{"ABC",";",":","!","?","'","\"","⌫"});
        } else {
            addRow(new String[]{"q","w","e","r","t","y","u","ı","o","p","ğ","ü"});
            addRow(new String[]{"a","s","d","f","g","h","j","k","l","ş","i"});
            addRow(new String[]{"⇧","z","x","c","v","b","n","m","ö","ç","⌫"});
        }

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);

        String mode = symbols ? "ABC" : "123";
        bottom.addView(key(mode), weighted(1.0f));

        TextView comma = key(",");
        comma.setOnClickListener(new KeyClick(","));
        bottom.addView(comma, weighted(0.8f));

        TextView space = key("boşluk");
        space.setOnClickListener(new KeyClick(" "));
        bottom.addView(space, weighted(4.5f));

        TextView dot = key(".");
        dot.setOnClickListener(new KeyClick("."));
        bottom.addView(dot, weighted(0.8f));

        TextView enter = key("↵");
        enter.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendEnter(); }
        });
        bottom.addView(enter, weighted(1.1f));

        ((TextView) bottom.getChildAt(0)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                symbols = !symbols;
                shift = false;
                rebuild();
            }
        });

        root.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
    }

    private LinearLayout.LayoutParams weighted(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), weight);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        return lp;
    }

    private void addRow(String[] values) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);

        for (final String value : values) {
            TextView key = key(display(value));
            key.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if ("⌫".equals(value)) {
                        backspace();
                    } else if ("⇧".equals(value)) {
                        shift = !shift;
                        rebuild();
                    } else if ("ABC".equals(value)) {
                        symbols = false;
                        shift = false;
                        rebuild();
                    } else {
                        commit(transform(value));
                    }
                }
            });
            row.addView(key, weighted(1.0f));
        }

        root.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
    }

    private TextView key(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(Color.rgb(48, 54, 65), 7));
        return t;
    }

    private String display(String value) {
        if (!symbols && shift && value.length() == 1 && Character.isLetter(value.charAt(0))) {
            return value.toUpperCase(tr);
        }
        return value;
    }

    private String transform(String value) {
        if (!symbols && shift && value.length() == 1 && Character.isLetter(value.charAt(0))) {
            return value.toUpperCase(tr);
        }
        return value;
    }

    private void commit(String value) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(value, 1);
        if (shift && !symbols && value.length() == 1) {
            shift = false;
            rebuild();
        }
    }

    private void backspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.deleteSurroundingText(1, 0);
    }

    private void sendEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        }
    }

    private class KeyClick implements View.OnClickListener {
        private final String value;
        KeyClick(String value) { this.value = value; }
        @Override public void onClick(View v) { commit(value); }
    }
}
