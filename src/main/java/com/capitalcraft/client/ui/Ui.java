package com.capitalcraft.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public final class Ui {
    private Ui() {}

    public static final int BG = 0xFF0E121A, PANEL = 0xFF141925, PANEL2 = 0xFF1A2030, PANEL3 = 0xFF252E44;
    public static final int BORDER = 0xFF2A3246, BORDER_HI = 0xFF3A4560;
    public static final int GOLD = 0xFFE9B949, GOLD_D = 0xFFB88A24, ORANGE = 0xFFE8803A;
    public static final int TEXT = 0xFFE8ECF4, MUTED = 0xFF8791A8, DIM = 0xFF5B6479;
    public static final int RED = 0xFFEF5350, GREEN = 0xFF4ADE80, YELLOW = 0xFFFFC94A, BLUE = 0xFF5AA9FF, CYAN = 0xFF4DD0E1;
    public static final float TAU = (float) (Math.PI * 2);
    public static final Identifier HEX = new Identifier("capitalcraft", "textures/gui/hex.png");

    // ------------------------------------------------------------ colours
    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) (((a >>> 24) & 255) + (((b >>> 24) & 255) - ((a >>> 24) & 255)) * t);
        int ar = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int ag = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int ab = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    public static int alpha(int color, float f) {
        int al = (int) (((color >>> 24) & 255) * Math.max(0f, Math.min(1f, f)));
        return (al << 24) | (color & 0xFFFFFF);
    }

    // ------------------------------------------------------------ boxes
    public static void rect(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x, y, x + w, y + h, col);
    }

    public static void frame(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x, y, x + w, y + 1, col);
        c.fill(x, y + h - 1, x + w, y + h, col);
        c.fill(x, y + 1, x + 1, y + h - 1, col);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, col);
    }

    public static void panel(DrawContext c, int x, int y, int w, int h, int fill, int border) {
        c.fill(x + 1, y, x + w - 1, y + h, border);
        c.fill(x, y + 1, x + w, y + h - 1, border);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    public static void hex(DrawContext c, int x, int y, int w, int h) {
        final int tw = 30, th = 16;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int ty = 0; ty < h; ty += th) {
            int hh = Math.min(th, h - ty);
            for (int tx = 0; tx < w; tx += tw) {
                int ww = Math.min(tw, w - tx);
                c.drawTexture(HEX, x + tx, y + ty, 0f, 0f, ww, hh, tw, th);
            }
        }
    }

    // ------------------------------------------------------------ text
    public static void text(DrawContext c, TextRenderer tr, Text t, float x, float y, int color, float s, boolean shadow) {
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x, y, 0f);
        m.scale(s, s, 1f);
        c.drawText(tr, t, 0, 0, color, shadow);
        m.pop();
    }

    public static void text(DrawContext c, TextRenderer tr, String t, float x, float y, int color, float s) {
        text(c, tr, Text.literal(t), x, y, color, s, true);
    }

    public static void ctext(DrawContext c, TextRenderer tr, Text t, float cx, float y, int color, float s) {
        text(c, tr, t, cx - tr.getWidth(t) * s / 2f, y, color, s, true);
    }

    public static void ctext(DrawContext c, TextRenderer tr, String t, float cx, float y, int color, float s) {
        ctext(c, tr, Text.literal(t), cx, y, color, s);
    }

    public static void rtext(DrawContext c, TextRenderer tr, String t, float right, float y, int color, float s) {
        text(c, tr, Text.literal(t), right - tr.getWidth(t) * s, y, color, s, true);
    }

    // ------------------------------------------------------------ buttons
    public static void goldButton(DrawContext c, TextRenderer tr, int x, int y, int w, int h, Text label, boolean hover, boolean enabled) {
        int top = enabled ? (hover ? 0xFFF3CB63 : GOLD) : 0xFF3A4054;
        int bot = enabled ? (hover ? 0xFFC9981F : GOLD_D) : 0xFF2C3142;
        panel(c, x, y, w, h, 0, enabled ? 0xFF7A5A12 : 0xFF232838);
        c.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, top, bot);
        float cx = x + w / 2f;
        ctext(c, tr, label, cx + 6, y + (h - 8) / 2f, enabled ? 0xFF2A1D05 : DIM, 1f);
        int lw = tr.getWidth(label);
        float ix = cx + 6 - lw / 2f - 12;
        int col = enabled ? 0xFF2A1D05 : DIM;
        line(c, ix, y + h / 2f + 1, ix + 4, y + h / 2f - 3, 1.6f, col);
        line(c, ix + 4, y + h / 2f - 3, ix + 8, y + h / 2f + 1, 1.6f, col);
        line(c, ix, y + h / 2f + 5, ix + 4, y + h / 2f + 1, 1.6f, col);
        line(c, ix + 4, y + h / 2f + 1, ix + 8, y + h / 2f + 5, 1.6f, col);
    }

    public static void chip(DrawContext c, TextRenderer tr, int x, int y, int w, int h, String label, int color, boolean hover, boolean active) {
        panel(c, x, y, w, h, hover ? PANEL3 : PANEL2, active ? GOLD : BORDER);
        ctext(c, tr, label, x + w / 2f, y + (h - 8) / 2f + (h > 14 ? 0 : 0), color, 1f);
    }

    public static void chip(DrawContext c, TextRenderer tr, int x, int y, int w, int h, Text label, int color, boolean hover, boolean active) {
        panel(c, x, y, w, h, hover ? PANEL3 : PANEL2, active ? GOLD : BORDER);
        ctext(c, tr, label, x + w / 2f, y + (h - 8) / 2f, color, 1f);
    }

    public static void solidButton(DrawContext c, TextRenderer tr, int x, int y, int w, int h, Text label, int color, boolean hover, boolean enabled) {
        int base = enabled ? color : 0xFF2C3142;
        panel(c, x, y, w, h, hover && enabled ? lerp(base, 0xFFFFFFFF, 0.18f) : base, enabled ? lerp(base, 0xFF000000, 0.45f) : 0xFF232838);
        ctext(c, tr, label, x + w / 2f, y + (h - 8) / 2f, enabled ? 0xFFFFFFFF : DIM, 1f);
    }

    // ------------------------------------------------------------ geometry
    private static void prep() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
    }

    private static void done() {
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    private static void v(BufferBuilder b, Matrix4f m, float x, float y, int argb) {
        b.vertex(m, x, y, 0f).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255).next();
    }

    public static void line(DrawContext c, float x0, float y0, float x1, float y1, float th, int col) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return;
        float nx = -dy / len * th / 2f, ny = dx / len * th / 2f;
        Matrix4f m = c.getMatrices().peek().getPositionMatrix();
        prep();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        v(b, m, x0 + nx, y0 + ny, col);
        v(b, m, x0 - nx, y0 - ny, col);
        v(b, m, x1 + nx, y1 + ny, col);
        v(b, m, x1 - nx, y1 - ny, col);
        Tessellator.getInstance().draw();
        done();
    }

    /** Angles in radians, 0 = 12 o'clock, clockwise. */
    public static void arc(DrawContext c, float cx, float cy, float r0, float r1, float a0, float a1, int colA, int colB) {
        float span = Math.abs(a1 - a0);
        if (span < 1e-4f) return;
        int segs = Math.max(2, (int) Math.ceil(span / (Math.PI / 90.0)));
        Matrix4f m = c.getMatrices().peek().getPositionMatrix();
        prep();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segs; i++) {
            float t = i / (float) segs;
            float a = a0 + (a1 - a0) * t;
            float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
            int col = lerp(colA, colB, t);
            v(b, m, cx + sx * r1, cy + sy * r1, col);
            v(b, m, cx + sx * r0, cy + sy * r0, col);
        }
        Tessellator.getInstance().draw();
        done();
    }

    public static void radial(DrawContext c, float cx, float cy, float r0, float r1, float angle, float th, int col) {
        float sx = (float) Math.sin(angle), sy = (float) -Math.cos(angle);
        line(c, cx + sx * r0, cy + sy * r0, cx + sx * r1, cy + sy * r1, th, col);
    }

    public static void circle(DrawContext c, float cx, float cy, float r, int col) {
        Matrix4f m = c.getMatrices().peek().getPositionMatrix();
        prep();
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        v(b, m, cx, cy, col);
        int segs = Math.max(14, (int) (r * 3));
        for (int i = 0; i <= segs; i++) {
            double a = Math.PI * 2 * i / segs;
            v(b, m, (float) (cx + Math.cos(a) * r), (float) (cy + Math.sin(a) * r), col);
        }
        Tessellator.getInstance().draw();
        done();
    }

    public static void coin(DrawContext c, TextRenderer tr, float cx, float cy, float r, int color, String letter) {
        circle(c, cx, cy, r, lerp(color, 0xFF000000, 0.45f));
        circle(c, cx, cy, r - 1.5f, color);
        circle(c, cx, cy, r - 3.5f, lerp(color, 0xFFFFFFFF, 0.18f));
        float s = r >= 9 ? 1f : 0.7f;
        ctext(c, tr, letter, cx, cy - 4 * s, lerp(color, 0xFF000000, 0.6f), s);
    }
}
