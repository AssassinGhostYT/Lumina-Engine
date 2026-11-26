package com.lumina.engine.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public final class AvatarView extends TextView {
    private static final int[] COLORS = {
            0xFF5865F2, 0xFF3BA55D, 0xFFFAA61A, 0xFFED4245, 0xFFEB459E,
            0xFF00A8FC, 0xFF9B59B6, 0xFFE67E22, 0xFF1ABC9C, 0xFF7F8C8D
    };

    private int sizePx;
    private int fill;
    private boolean speaking;

    public AvatarView(Context c) {
        super(c);
        setGravity(Gravity.CENTER);
        setTypeface(Typeface.DEFAULT_BOLD);
        setTextColor(0xFFFFFFFF);
        setIncludeFontPadding(false);
        setSingleLine(true);
    }

    public void setProfile(String name, String id, int sizeDp) {
        Context c = getContext();
        sizePx = Ui.dp(c, sizeDp);
        fill = color(id, name);
        setText(initials(name, id));
        setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(12f, sizeDp * 0.36f));
        setContentDescription(name == null ? "" : name);
        setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
        paint();
    }

    public void setSpeaking(boolean value) {
        if (speaking == value) return;
        speaking = value;
        paint();
    }

    private void paint() {
        Context c = getContext();
        setBackground(Ui.rounded(fill, sizePx / 2f, speaking ? Ui.SUCCESS : 0, speaking ? Ui.dp(c, 2) : 0));
        setScaleX(speaking ? 1.04f : 1f);
        setScaleY(speaking ? 1.04f : 1f);
    }

    private static int color(String id, String name) {
        int h = id == null ? 0 : id.hashCode();
        h = h * 31 + (name == null ? 0 : name.hashCode());
        return COLORS[Math.floorMod(h, COLORS.length)];
    }

    private static String initials(String name, String id) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) {
            String fallback = id == null ? "" : id.trim();
            return fallback.isEmpty() ? "?" : fallback.substring(0, 1).toUpperCase(Locale.getDefault());
        }
        String[] parts = value.split("\\s+");
        String first = parts[0].substring(0, 1);
        if (parts.length > 1) first += parts[parts.length - 1].substring(0, 1);
        return first.toUpperCase(Locale.getDefault());
    }
}
