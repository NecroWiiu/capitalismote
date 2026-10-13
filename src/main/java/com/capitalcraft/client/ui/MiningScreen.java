package com.capitalcraft.client.ui;

import com.capitalcraft.Fmt;
import com.capitalcraft.client.ClientData;
import com.capitalcraft.market.Assets;
import com.capitalcraft.net.Net;
import com.capitalcraft.registry.ModSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;

/** GPU block menu: pick a coin, overclock, cool it down, cash out. */
public class MiningScreen extends CapScreen {
    private static final int SW = 300, SH = 262;
    private final BlockPos pos;
    private int poll;
    private float fan;
    private long lastNs = System.nanoTime();
    private Text status = Text.empty();
    private boolean statusOk;
    private long statusUntil;

    public MiningScreen(BlockPos pos) {
        super(Fmt.t("gpu.title"), SW, SH);
        this.pos = pos;
    }

    public void onTradeResult(boolean ok, Text msg) {
        status = msg;
        statusOk = ok;
        statusUntil = System.currentTimeMillis() + 3500;
        play(ok ? ModSounds.CASH : ModSounds.ERROR, 1f);
    }

    private void request() {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeBlockPos(pos);
        ClientPlayNetworking.send(Net.GPU_REQ, b);
    }

    private void act(int action, int arg) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeBlockPos(pos);
        b.writeByte(action);
        b.writeVarInt(arg);
        ClientPlayNetworking.send(Net.GPU_ACT, b);
        play(ModSounds.UI_CLICK, 1f);
    }

    @Override
    protected void tickScreen() {
        if (++poll >= 10) {
            poll = 0;
            request();
        }
    }

    private ClientData.Gpu g() {
        ClientData.Gpu g = ClientData.gpu;
        return g != null && g.pos.equals(pos) ? g : null;
    }

    @Override
    protected void draw(DrawContext c, int mx, int my, float delta) {
        Ui.panel(c, 0, 0, SW, SH, Ui.BG, Ui.BORDER_HI);
        ClientData.Gpu g = g();
        if (g == null) {
            Ui.ctext(c, textRenderer, Fmt.t("trade.connecting"), SW / 2f, SH / 2f, Ui.MUTED, 1f);
            return;
        }
        long nowNs = System.nanoTime();
        float dt = (nowNs - lastNs) / 1e9f;
        lastNs = nowNs;
        boolean running = g.power && !g.throttled && !g.noFunds;
        fan += dt * (running ? 7f + g.overclock * 3f : g.power ? 1.5f : 0f);

        Ui.text(c, textRenderer, Fmt.t("gpu.name." + g.tier), 10, 8, Ui.TEXT, 1.3f, true);
        String key = !g.power ? "gpu.status.off" : g.noFunds ? "gpu.status.nofunds" : g.throttled ? "gpu.status.hot" : "gpu.status.on";
        int scol = !g.power ? Ui.MUTED : g.noFunds ? Ui.ORANGE : g.throttled ? Ui.RED : Ui.GREEN;
        Text st = Fmt.t(key);
        int sw = textRenderer.getWidth(st) + 12;
        Ui.panel(c, SW - 10 - sw, 7, sw, 14, Ui.PANEL2, scol);
        Ui.ctext(c, textRenderer, st, SW - 10 - sw / 2f, 10, scol, 1f);

        // fan
        Ui.panel(c, 10, 28, 116, 78, Ui.PANEL, Ui.BORDER);
        Ui.hex(c, 12, 30, 112, 74);
        float fx = 68, fy = 67;
        Ui.circle(c, fx, fy, 30, 0xFF0B0E15);
        Ui.arc(c, fx, fy, 29, 31, 0f, Ui.TAU, running ? Ui.CYAN : Ui.BORDER_HI, running ? Ui.BLUE : Ui.BORDER_HI);
        for (int i = 0; i < 7; i++) {
            float a = fan + i * Ui.TAU / 7f;
            Ui.radial(c, fx, fy, 5, 26, a, 4.5f, running ? 0xFF3B4A6B : 0xFF232A3A);
            Ui.radial(c, fx, fy, 6, 25, a + 0.12f, 1.2f, running ? 0xFF7FA0D8 : 0xFF2F384D);
        }
        Ui.circle(c, fx, fy, 5, Ui.GOLD_D);
        Ui.circle(c, fx, fy, 3, Ui.GOLD);

        // temperature
        Ui.text(c, textRenderer, Fmt.t("gpu.temp", String.format(Locale.ROOT, "%.0f", g.temp)), 10, 112, Ui.MUTED, 0.9f, true);
        for (int i = 0; i < 116; i++) {
            float t = i / 115f;
            int col = t < 0.5f ? Ui.lerp(0xFF3B82F6, 0xFFFACC15, t * 2f) : Ui.lerp(0xFFFACC15, 0xFFEF4444, (t - 0.5f) * 2f);
            Ui.rect(c, 10 + i, 124, 1, 7, col);
        }
        Ui.rect(c, 10, 124, 116, 7, 0x00000000);
        Ui.frame(c, 9, 123, 118, 9, Ui.BORDER);
        int mk = 10 + Math.round(Math.max(0f, Math.min(1f, (g.temp - 25f) / 75f)) * 115f);
        Ui.rect(c, mk - 1, 121, 3, 13, Ui.TEXT);
        Ui.text(c, textRenderer, Fmt.t(g.coolant ? "gpu.cooled" : "gpu.nocool"), 10, 135, g.coolant ? Ui.CYAN : Ui.DIM, 0.85f, true);

        // overclock + power
        Ui.text(c, textRenderer, Fmt.t("gpu.oc"), 10, 148, Ui.MUTED, 0.9f, true);
        String[] oc = {"x1.0", "x1.4", "x1.8"};
        int[] occ = {Ui.TEXT, Ui.YELLOW, Ui.RED};
        for (int i = 0; i < 3; i++) {
            int x = 10 + i * 40;
            boolean h = hit(mx, my, x, 159, 36, 16);
            Ui.chip(c, textRenderer, x, 159, 36, 16, oc[i], occ[i], h, g.overclock == i);
            if (h) tip(Fmt.t("gpu.oc.tip." + i));
        }
        boolean ph = hit(mx, my, 10, 181, 116, 20);
        Ui.solidButton(c, textRenderer, 10, 181, 116, 20, Fmt.t(g.power ? "gpu.power.off" : "gpu.power.on"), g.power ? 0xFFB03A3A : 0xFF1F9D55, ph, true);

        // coin list
        Ui.text(c, textRenderer, Fmt.t("gpu.coin"), 134, 30, Ui.MUTED, 0.9f, true);
        Ui.rtext(c, textRenderer, Fmt.t("gpu.perhour").getString(), SW - 10, 30, Ui.MUTED, 0.8f);
        int row = 0;
        for (int i = 0; i < Assets.CRYPTOS.length; i++) {
            Assets.Crypto cr = Assets.CRYPTOS[i];
            if (!cr.minable()) continue;
            int y = 42 + row * 14;
            boolean sel = g.coin == i;
            boolean hv = hit(mx, my, 134, y, 156, 13);
            Ui.panel(c, 134, y, 156, 13, hv ? Ui.PANEL3 : Ui.PANEL2, sel ? Ui.GOLD : Ui.BORDER);
            Ui.circle(c, 142, y + 6.5f, 3, cr.color());
            Ui.text(c, textRenderer, cr.symbol(), 148, y + 3, Ui.TEXT, 0.85f, true);
            Ui.rtext(c, textRenderer, Fmt.price(g.price[i]), 214, y + 3, Ui.MUTED, 0.75f);
            Ui.rtext(c, textRenderer, "$" + Fmt.compact(g.est[i] * 3600), 286, y + 3, Ui.GREEN, 0.8f);
            row++;
        }
        Ui.text(c, textRenderer, Fmt.t("gpu.diff", String.format(Locale.ROOT, "%.2f", g.diff[g.coin])), 134, 42 + row * 14 + 3, Ui.DIM, 0.8f, true);

        // stats
        double net = g.usdPerSec - g.powerCost;
        Ui.panel(c, 10, 208, SW - 20, 26, Ui.PANEL, Ui.BORDER);
        Ui.text(c, textRenderer, Fmt.t("gpu.stats1", String.format(Locale.ROOT, "%.2f", g.usdPerSec), String.format(Locale.ROOT, "%.2f", g.powerCost)), 16, 212, Ui.TEXT, 0.85f, true);
        Ui.rtext(c, textRenderer, (net >= 0 ? "+" : "") + String.format(Locale.ROOT, "%.2f", net) + "/s", SW - 16, 212, net >= 0 ? Ui.GREEN : Ui.RED, 0.9f);
        Ui.text(c, textRenderer, Fmt.t("gpu.stats2", Fmt.money((long) g.session), Fmt.money((long) g.total)), 16, 223, Ui.MUTED, 0.8f, true);

        // sell
        Assets.Crypto cur = Assets.CRYPTOS[g.coin];
        double hold = g.holdings[g.coin];
        Ui.text(c, textRenderer, Fmt.t("gpu.hold", Fmt.qty(hold), cur.symbol(), Fmt.money((long) (hold * g.price[g.coin]))), 10, 240, Ui.TEXT, 0.85f, true);
        boolean any = false;
        for (double h : g.holdings) any |= h > 0;
        Ui.solidButton(c, textRenderer, 160, 238, 62, 16, Fmt.t("gpu.sell", cur.symbol()), 0xFF1F7A8C, hit(mx, my, 160, 238, 62, 16), hold > 0);
        Ui.solidButton(c, textRenderer, 226, 238, 64, 16, Fmt.t("gpu.sell_all"), 0xFF8E3B2E, hit(mx, my, 226, 238, 64, 16), any);
        if (System.currentTimeMillis() < statusUntil) {
            Ui.ctext(c, textRenderer, textRenderer.trimToWidth(status.getString(), 280), SW / 2f, 253, statusOk ? Ui.GREEN : Ui.RED, 0.85f);
        } else {
            Ui.rtext(c, textRenderer, "$" + Fmt.money(g.balance), SW - 10, 8 + 14, Ui.GOLD, 0.8f);
        }
    }

    @Override
    protected boolean click(double dmx, double dmy, int button) {
        ClientData.Gpu g = g();
        if (g == null) return false;
        int x = (int) dmx, y = (int) dmy;
        for (int i = 0; i < 3; i++) {
            if (hit(x, y, 10 + i * 40, 159, 36, 16)) {
                act(2, i);
                return true;
            }
        }
        if (hit(x, y, 10, 181, 116, 20)) {
            act(0, 0);
            return true;
        }
        int row = 0;
        for (int i = 0; i < Assets.CRYPTOS.length; i++) {
            if (!Assets.CRYPTOS[i].minable()) continue;
            if (hit(x, y, 134, 42 + row * 14, 156, 13)) {
                act(1, i);
                return true;
            }
            row++;
        }
        if (hit(x, y, 160, 238, 62, 16) && g.holdings[g.coin] > 0) {
            act(3, g.coin);
            return true;
        }
        if (hit(x, y, 226, 238, 64, 16)) {
            act(4, 0);
            return true;
        }
        return false;
    }
}
