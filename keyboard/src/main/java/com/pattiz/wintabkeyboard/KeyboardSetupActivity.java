package com.pattiz.wintabkeyboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class KeyboardSetupActivity extends Activity {

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable buttonBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(0, 120, 215));
        g.setCornerRadius(dp(8));
        return g;
    }

    private TextView button(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setGravity(Gravity.CENTER);
        t.setBackground(buttonBg());
        return t;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(Color.rgb(22, 26, 33));

        TextView title = new TextView(this);
        title.setText("WinTab Keyboard");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));

        TextView info = new TextView(this);
        info.setText("1. Klavye ayarlarını aç.\n2. WinTab Keyboard'u etkinleştir.\n3. Klavye seçicisinden WinTab Keyboard'u seç.");
        info.setTextColor(Color.rgb(220, 220, 220));
        info.setTextSize(17);
        info.setLineSpacing(0, 1.25f);
        root.addView(info, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(150)));

        TextView enable = button("Klavye ayarlarını aç");
        enable.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        });
        root.addView(enable, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(55)));

        TextView choose = button("Klavye seç");
        LinearLayout.LayoutParams chooseLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(55));
        chooseLp.topMargin = dp(14);
        root.addView(choose, chooseLp);

        choose.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }
        });

        setContentView(root);
    }
}
