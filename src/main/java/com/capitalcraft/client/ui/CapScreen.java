package com.capitalcraft.client.ui;

import com.capitalcraft.Fmt;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Base for the three menus: fixed design size, auto-scaled to the window, mouse mapped to design space. */
public abstract class CapScreen extends Screen {
    protected final int W, H;
    protected float scale = 1f;
    protected int ox, oy;
    protected int mx, my;
    private List<Text> tipLines;

    protected CapScreen(Text title, int w, int h) {
        super(title);
        this.W = w;
        this.H = h;
    }

    protected abstract void draw(DrawContext ctx, int mx, int my, float delta);

    protected boolean click(double mx, double my, int button) {
        return false;
    }

    protected boolean scroll(double mx, double my, double amount) {
        return false;
    }

    protected void tickScreen() {
    }

    protected boolean typing() {
        return false;
    }

    protected boolean typedChar(char chr) {
        return false;
    }

    protected boolean pressedKey(int keyCode) {
        return false;
    }

    protected static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    protected void play(SoundEvent ev, float pitch) {
        client.getSoundManager().play(PositionedSoundInstance.master(ev, pitch, 0.7f));
    }

    protected void tip(Text... lines) {
        tipLines = new ArrayList<>(List.of(lines));
    }

    protected void tipItem(ItemStack s, long value) {
        tipLines = new ArrayList<>(getTooltipFromItem(client, s));
        tipLines.add(Fmt.t("tip.value", Fmt.money(value)).formatted(Formatting.GOLD));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        scale = Math.max(0.5f, Math.min(1f, Math.min((width - 6f) / W, (height - 6f) / H)));
        ox = Math.round((width - W * scale) / 2f);
        oy = Math.round((height - H * scale) / 2f);
        mx = Math.round((mouseX - ox) / scale);
        my = Math.round((mouseY - oy) / scale);
        tipLines = null;
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(ox, oy, 0f);
        ms.scale(scale, scale, 1f);
        draw(ctx, mx, my, delta);
        ms.pop();
        if (tipLines != null) ctx.drawTooltip(textRenderer, tipLines, mouseX, mouseY);
    }

    @Override
    public void tick() {
        tickScreen();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return click((x - ox) / scale, (y - oy) / scale, button) || super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        return scroll((x - ox) / scale, (y - oy) / scale, amount) || super.mouseScrolled(x, y, amount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return typedChar(chr) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pressedKey(keyCode)) return true;
        if (!typing() && client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
