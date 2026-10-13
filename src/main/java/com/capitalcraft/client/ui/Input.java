package com.capitalcraft.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Tiny text field that works inside the scaled UI (vanilla widgets don't). */
public final class Input {
    public String text = "";
    public boolean focused;
    private final int max;
    private final boolean numeric;

    public Input(int max, boolean numeric) {
        this.max = max;
        this.numeric = numeric;
    }

    public void draw(DrawContext c, TextRenderer tr, int x, int y, int w, int h, Text placeholder) {
        Ui.panel(c, x, y, w, h, Ui.BG, focused ? Ui.GOLD : Ui.BORDER);
        String s = text;
        int tw = tr.getWidth(s);
        while (tw > w - 10 && s.length() > 1) {
            s = s.substring(1);
            tw = tr.getWidth(s);
        }
        int ty = y + (h - 8) / 2;
        if (text.isEmpty() && placeholder != null && !focused) c.drawText(tr, placeholder, x + 4, ty, Ui.DIM, false);
        c.drawText(tr, s, x + 4, ty, Ui.TEXT, false);
        if (focused && (System.currentTimeMillis() / 450) % 2 == 0) c.fill(x + 5 + tw, y + 2, x + 6 + tw, y + h - 2, Ui.TEXT);
    }

    public boolean charTyped(char ch) {
        if (!focused) return false;
        if (text.length() >= max) return true;
        if (numeric) {
            if (!(Character.isDigit(ch) || ch == 'k' || ch == 'K' || ch == 'm' || ch == 'M')) return true;
        } else if (ch < 32) {
            return true;
        }
        text += ch;
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!focused) return false;
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            focused = false;
            return true;
        }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            String clip = MinecraftClient.getInstance().keyboard.getClipboard();
            for (char ch : clip.toCharArray()) charTyped(ch);
            return true;
        }
        return keyCode != GLFW.GLFW_KEY_ESCAPE;
    }

    public long value() {
        String s = text.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return 0;
        long mult = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k') {
            mult = 1000;
            s = s.substring(0, s.length() - 1);
        } else if (last == 'm') {
            mult = 1_000_000;
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) return 0;
        try {
            return Math.min(1_000_000_000_000L, Long.parseLong(s) * mult);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void set(long v) {
        text = v <= 0 ? "" : Long.toString(v);
    }
}
