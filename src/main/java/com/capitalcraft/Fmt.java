package com.capitalcraft;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Locale;

public final class Fmt {
    private Fmt() {}

    public static MutableText t(String key, Object... args) {
        return Text.translatable("capitalcraft." + key, args);
    }

    public static String money(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    public static String price(double p) {
        if (p >= 100) return String.format(Locale.ROOT, "%,.2f", p);
        if (p >= 1) return String.format(Locale.ROOT, "%.3f", p);
        if (p >= 0.01) return String.format(Locale.ROOT, "%.4f", p);
        return String.format(Locale.ROOT, "%.6f", p);
    }

    public static String qty(double q) {
        if (q >= 1000) return String.format(Locale.ROOT, "%,.1f", q);
        if (q >= 1) return trim(String.format(Locale.ROOT, "%.3f", q));
        return trim(String.format(Locale.ROOT, "%.6f", q));
    }

    private static String trim(String s) {
        if (s.indexOf('.') < 0) return s;
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') end--;
        if (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }

    public static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1e9) return String.format(Locale.ROOT, "%.2fB", v / 1e9);
        if (a >= 1e6) return String.format(Locale.ROOT, "%.2fM", v / 1e6);
        if (a >= 1e4) return String.format(Locale.ROOT, "%.1fK", v / 1e3);
        return String.format(Locale.ROOT, "%,d", Math.round(v));
    }

    public static String pct(double f) {
        return String.format(Locale.ROOT, "%+.2f%%", f * 100.0);
    }

    public static String clock(int seconds) {
        seconds = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
}
