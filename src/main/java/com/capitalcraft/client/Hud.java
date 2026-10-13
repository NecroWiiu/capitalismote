package com.capitalcraft.client;

import com.capitalcraft.Fmt;
import com.capitalcraft.client.ui.CapScreen;
import com.capitalcraft.client.ui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public final class Hud {
    private Hud() {}

    public static void render(DrawContext c, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!ClientData.hudVisible || mc.player == null || mc.options.hudHidden || mc.options.debugEnabled) return;
        if (mc.currentScreen instanceof CapScreen) return;
        TextRenderer tr = mc.textRenderer;
        ClientData.JobInfo j = ClientData.job;
        int w = 150;
        int rows = j == null ? 0 : j.progress.length;
        int h = 18 + (j == null ? 0 : 22 + rows * 16);
        int x = 6, y = 6;
        Ui.panel(c, x, y, w, h, 0xB0101520, 0xB02A3246);
        c.drawText(tr, "$ " + Fmt.money(ClientData.balance), x + 6, y + 5, Ui.GOLD, true);
        if (j == null) return;
        int ty = y + 20;
        c.drawText(tr, Fmt.t("job." + j.id), x + 6, ty, Ui.TEXT, true);
        String t = Fmt.clock(j.secondsNow());
        c.drawText(tr, t, x + w - 6 - tr.getWidth(t), ty, j.secondsNow() < 60 ? Ui.RED : Ui.MUTED, true);
        ty += 12;
        for (int i = 0; i < rows; i++) {
            float f = Math.min(1f, j.progress[i] / (float) Math.max(1, j.target[i]));
            c.drawText(tr, tr.trimToWidth(Fmt.t("job." + j.id + ".o" + i).getString(), w - 52), x + 6, ty, Ui.MUTED, false);
            String pr = j.progress[i] + "/" + j.target[i];
            c.drawText(tr, pr, x + w - 6 - tr.getWidth(pr), ty, f >= 1f ? Ui.GREEN : Ui.TEXT, false);
            c.fill(x + 6, ty + 10, x + w - 6, ty + 12, 0xFF1B2130);
            c.fill(x + 6, ty + 10, x + 6 + Math.round((w - 12) * f), ty + 12, f >= 1f ? Ui.GREEN : Ui.GOLD);
            ty += 16;
        }
    }
}
