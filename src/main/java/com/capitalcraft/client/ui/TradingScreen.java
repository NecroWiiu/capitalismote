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

import java.util.Locale;

/** Stock & crypto exchange terminal. */
public class TradingScreen extends CapScreen {
    private static final int SW = 380, SH = 256;
    private static final int RX = 144, RW = 228;

    private int tab;          // 0 stocks, 1 crypto, 2 portfolio
    private int selKind, selIdx;
    private final Input amount = new Input(13, true);
    private Text status = Text.empty();
    private boolean statusOk;
    private long statusUntil;
    private int poll;

    public TradingScreen() {
        super(Fmt.t("trade.title"), SW, SH);
        amount.text = "1000";
        request();
    }

    private void request() {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeByte(selKind);
        b.writeVarInt(selIdx);
        ClientPlayNetworking.send(Net.MARKET_REQ, b);
    }

    public void onTradeResult(boolean ok, Text msg) {
        status = msg;
        statusOk = ok;
        statusUntil = System.currentTimeMillis() + 4000;
        play(ok ? ModSounds.CASH : ModSounds.ERROR, 1f);
    }

    @Override
    protected void tickScreen() {
        if (++poll >= 10) {
            poll = 0;
            request();
        }
    }

    // ---------------------------------------------------------------- accessors
    private ClientData.Market m() {
        return ClientData.market;
    }

    private int n(int kind) {
        return kind == 0 ? Assets.STOCKS.length : Assets.CRYPTOS.length;
    }

    private double price(int kind, int i) {
        return kind == 0 ? m().sPrice[i] : m().cPrice[i];
    }

    private double open(int kind, int i) {
        return kind == 0 ? m().sOpen[i] : m().cOpen[i];
    }

    private double qty(int kind, int i) {
        return kind == 0 ? m().sQty[i] : m().cQty[i];
    }

    private double cost(int kind, int i) {
        return kind == 0 ? m().sCost[i] : m().cCost[i];
    }

    private String sym(int kind, int i) {
        return kind == 0 ? Assets.STOCKS[i].symbol() : Assets.CRYPTOS[i].symbol();
    }

    private String name(int kind, int i) {
        return kind == 0 ? Assets.STOCKS[i].name() : Assets.CRYPTOS[i].name();
    }

    private int color(int kind, int i) {
        return kind == 0 ? Assets.STOCKS[i].color() : Assets.CRYPTOS[i].color();
    }

    private static int pnlColor(double v) {
        return v > 0.00005 ? Ui.GREEN : v < -0.00005 ? Ui.RED : Ui.MUTED;
    }

    // ---------------------------------------------------------------- drawing
    @Override
    protected void draw(DrawContext c, int mx, int my, float delta) {
        Ui.panel(c, 0, 0, SW, SH, Ui.BG, Ui.BORDER_HI);
        Ui.text(c, textRenderer, Fmt.t("trade.title"), 8, 7, Ui.TEXT, 1.3f, true);
        if (m() == null) {
            Ui.ctext(c, textRenderer, Fmt.t("trade.connecting"), SW / 2f, SH / 2f, Ui.MUTED, 1f);
            return;
        }
        double value = 0, invested = 0;
        for (int k = 0; k < 2; k++) {
            for (int i = 0; i < n(k); i++) {
                value += qty(k, i) * price(k, i);
                invested += cost(k, i);
            }
        }
        Ui.rtext(c, textRenderer, "$" + Fmt.money(m().balance), SW - 8, 6, Ui.GOLD, 1.2f);
        double pnl = value - invested;
        String line = Fmt.t("trade.portfolio", Fmt.money((long) value)).getString() + "  "
                + (pnl >= 0 ? "+" : "") + Fmt.money((long) pnl);
        Ui.rtext(c, textRenderer, line, SW - 8, 19, invested > 0 ? pnlColor(pnl) : Ui.MUTED, 0.9f);
        drawSentiment(c);

        String[] tabs = {"trade.tab.stocks", "trade.tab.crypto", "trade.tab.portfolio"};
        for (int i = 0; i < 3; i++) {
            int x = 8 + i * 60;
            Ui.chip(c, textRenderer, x, 32, 58, 15, Fmt.t(tabs[i]), Ui.TEXT, hit(mx, my, x, 32, 58, 15), tab == i);
        }
        drawNews(c);

        if (tab == 2) {
            drawPortfolio(c, mx, my);
            return;
        }
        drawList(c, mx, my);
        drawDetail(c, mx, my);
    }

    private void drawSentiment(DrawContext c) {
        int x = 8, y = 20, w = 70;
        Ui.rect(c, x, y + 3, w, 3, 0xFF1B2130);
        Ui.rect(c, x, y + 3, w / 2, 3, 0xFF5A2A2E);
        Ui.rect(c, x + w / 2, y + 3, w - w / 2, 3, 0xFF21493A);
        int mxp = x + Math.round((m().sentiment * 0.5f + 0.5f) * w);
        Ui.rect(c, mxp - 1, y + 1, 3, 7, Ui.TEXT);
        Ui.text(c, textRenderer, Fmt.t(m().sentiment > 0.2f ? "trade.mood.bull" : m().sentiment < -0.2f ? "trade.mood.bear" : "trade.mood.flat"),
                x + w + 6, y, m().sentiment > 0.2f ? Ui.GREEN : m().sentiment < -0.2f ? Ui.RED : Ui.MUTED, 0.85f, true);
    }

    private void drawNews(DrawContext c) {
        if (m().news.isEmpty()) return;
        int idx = (int) ((System.currentTimeMillis() / 5000) % m().news.size());
        ClientData.News nw = m().news.get(idx);
        Object arg = switch (nw.kind) {
            case 0 -> nw.idx < Assets.STOCKS.length ? Assets.STOCKS[nw.idx].name() : "";
            case 1 -> nw.idx < Assets.CRYPTOS.length ? Assets.CRYPTOS[nw.idx].name() : "";
            case 2 -> Fmt.t("sector." + Assets.SECTORS[Math.max(0, Math.min(Assets.SECTORS.length - 1, nw.idx))]);
            default -> "";
        };
        Text t = Fmt.t("news.t" + nw.template, arg);
        float fade = Math.min(1f, (System.currentTimeMillis() % 5000) / 400f);
        String s = textRenderer.trimToWidth(t.getString(), 170);
        Ui.text(c, textRenderer, Text.literal("\u25CF ").append(Text.literal(s)), 188, 36, Ui.alpha(Ui.YELLOW, 0.4f + 0.6f * fade), 0.9f, false);
    }

    private void drawList(DrawContext c, int mx, int my) {
        int kind = tab;
        for (int i = 0; i < n(kind); i++) {
            int y = 52 + i * 16;
            boolean sel = selKind == kind && selIdx == i;
            boolean hv = hit(mx, my, 8, y, 128, 15);
            Ui.panel(c, 8, y, 128, 15, hv ? Ui.PANEL3 : Ui.PANEL2, sel ? Ui.GOLD : Ui.BORDER);
            Ui.circle(c, 16, y + 7.5f, 3, color(kind, i));
            Ui.text(c, textRenderer, sym(kind, i), 23, y + 3.5f, Ui.TEXT, 0.85f, true);
            double ch = price(kind, i) / open(kind, i) - 1;
            Ui.rtext(c, textRenderer, Fmt.price(price(kind, i)), 100, y + 3.5f, Ui.TEXT, 0.8f);
            Ui.rtext(c, textRenderer, String.format(Locale.ROOT, "%+.1f%%", ch * 100), 133, y + 3.5f, pnlColor(ch), 0.8f);
        }
    }

    private void drawDetail(DrawContext c, int mx, int my) {
        int k = selKind, i = selIdx;
        if (k != tab) return;
        double p = price(k, i), o = open(k, i);
        double ch = p / o - 1;
        int up = pnlColor(ch);
        Ui.panel(c, RX, 52, RW, 26, Ui.PANEL, Ui.BORDER);
        Ui.coin(c, textRenderer, RX + 14, 65, 9, color(k, i), sym(k, i).substring(0, 1));
        Ui.text(c, textRenderer, name(k, i), RX + 28, 56, Ui.TEXT, 0.95f, true);
        String sub = sym(k, i) + (k == 0 ? " \u00B7 " + Fmt.t("sector." + Assets.SECTORS[Assets.STOCKS[i].sector()]).getString() : "");
        Ui.text(c, textRenderer, sub, RX + 28, 67, Ui.MUTED, 0.8f, true);
        Ui.rtext(c, textRenderer, "$" + Fmt.price(p), RX + RW - 6, 55, Ui.TEXT, 1.2f);
        Ui.rtext(c, textRenderer, Fmt.pct(ch), RX + RW - 6, 67, up, 0.9f);

        drawChart(c, mx, my, RX, 80, RW, 78, up);

        double q = qty(k, i), cs = cost(k, i);
        double val = q * p, pl = val - cs;
        Ui.panel(c, RX, 162, RW, 26, Ui.PANEL, Ui.BORDER);
        Ui.text(c, textRenderer, Fmt.t("trade.pos", Fmt.qty(q), sym(k, i), Fmt.money((long) val)), RX + 6, 166, Ui.TEXT, 0.85f, true);
        if (q > 0) {
            Ui.text(c, textRenderer, Fmt.t("trade.avg", Fmt.price(cs / q)), RX + 6, 177, Ui.MUTED, 0.8f, true);
            Ui.rtext(c, textRenderer, (pl >= 0 ? "+" : "") + Fmt.money((long) pl) + " (" + Fmt.pct(cs > 0 ? pl / cs : 0) + ")", RX + RW - 6, 177, pnlColor(pl), 0.85f);
        } else {
            Ui.text(c, textRenderer, Fmt.t("trade.nopos"), RX + 6, 177, Ui.DIM, 0.8f, true);
        }

        amount.draw(c, textRenderer, RX, 192, 100, 15, Fmt.t("trade.amount"));
        long amt = amount.value();
        String prev;
        if (amt <= 0) prev = "";
        else if (k == 0) prev = "\u2248 " + Fmt.qty(Math.floor(amt / (p * 1.005))) + " " + sym(k, i);
        else prev = "\u2248 " + Fmt.qty(amt * 0.995 / p) + " " + sym(k, i);
        Ui.text(c, textRenderer, prev, RX + 106, 196, Ui.MUTED, 0.8f, true);

        String[] chips = {"+100", "+1K", "+10K", "+100K", "MAX", "X"};
        for (int j = 0; j < 6; j++) {
            Ui.chip(c, textRenderer, RX + j * 38, 210, 34, 14, chips[j], j == 5 ? Ui.RED : Ui.TEXT, hit(mx, my, RX + j * 38, 210, 34, 14), false);
        }
        boolean canBuy = amt > 0 && amt <= m().balance;
        boolean canSell = q > 0 && amt > 0;
        Ui.solidButton(c, textRenderer, RX, 228, 72, 18, Fmt.t("trade.buy"), 0xFF1F9D55, hit(mx, my, RX, 228, 72, 18), canBuy);
        Ui.solidButton(c, textRenderer, RX + 76, 228, 72, 18, Fmt.t("trade.sell"), 0xFFC0392B, hit(mx, my, RX + 76, 228, 72, 18), canSell);
        Ui.solidButton(c, textRenderer, RX + 152, 228, 76, 18, Fmt.t("trade.sell_all"), 0xFF8E3B2E, hit(mx, my, RX + 152, 228, 76, 18), q > 0);
        if (System.currentTimeMillis() < statusUntil) {
            Ui.ctext(c, textRenderer, textRenderer.trimToWidth(status.getString(), 360), SW / 2f, 247, statusOk ? Ui.GREEN : Ui.RED, 0.85f);
        }
    }

    private void drawChart(DrawContext c, int mx, int my, int x, int y, int w, int h, int col) {
        Ui.panel(c, x, y, w, h, 0xFF0B0E15, Ui.BORDER);
        ClientData.Market mk = m();
        double p = price(selKind, selIdx);
        if (mk.selKind != selKind || mk.selIdx != selIdx || mk.hist.length < 2) {
            Ui.ctext(c, textRenderer, "...", x + w / 2f, y + h / 2f - 4, Ui.DIM, 1f);
            return;
        }
        int n = mk.hist.length + 1;
        double[] v = new double[n];
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            v[i] = i < n - 1 ? mk.hist[i] : p;
            lo = Math.min(lo, v[i]);
            hi = Math.max(hi, v[i]);
        }
        double pad = Math.max((hi - lo) * 0.12, hi * 1e-5);
        lo -= pad;
        hi += pad;
        int ix = x + 4, iy = y + 5, iw = w - 8, ih = h - 10;
        for (int g = 0; g < 3; g++) Ui.rect(c, ix, iy + ih * (g + 1) / 4, iw, 1, 0xFF161C29);
        double o = open(selKind, selIdx);
        if (o > lo && o < hi) {
            int oy = iy + (int) Math.round((hi - o) / (hi - lo) * ih);
            for (int dx = 0; dx < iw; dx += 6) Ui.rect(c, ix + dx, oy, 3, 1, Ui.DIM);
        }
        float px = 0, py = 0;
        for (int i = 0; i < n; i++) {
            float cx = ix + iw * i / (float) (n - 1);
            float cy = (float) (iy + (hi - v[i]) / (hi - lo) * ih);
            if (i > 0) Ui.line(c, px, py, cx, cy, 1.4f, col);
            px = cx;
            py = cy;
        }
        Ui.circle(c, px, py, 2.2f, col);
        Ui.text(c, textRenderer, Fmt.price(hi - pad), x + 5, y + 3, Ui.DIM, 0.7f, false);
        Ui.text(c, textRenderer, Fmt.price(lo + pad), x + 5, y + h - 10, Ui.DIM, 0.7f, false);
        if (hit(mx, my, ix, iy, iw, ih)) {
            int idx = Math.max(0, Math.min(n - 1, Math.round((mx - ix) / (float) iw * (n - 1))));
            float cx = ix + iw * idx / (float) (n - 1);
            Ui.rect(c, Math.round(cx), iy, 1, ih, 0x55FFFFFF);
            Ui.ctext(c, textRenderer, "$" + Fmt.price(v[idx]), Math.max(x + 30, Math.min(x + w - 30, cx)), y + 3, Ui.TEXT, 0.85f);
        }
    }

    private void drawPortfolio(DrawContext c, int mx, int my) {
        Ui.panel(c, 8, 52, SW - 16, SH - 60, Ui.PANEL, Ui.BORDER);
        int y = 58;
        Ui.text(c, textRenderer, Fmt.t("trade.col.asset"), 16, y, Ui.MUTED, 0.85f, true);
        Ui.rtext(c, textRenderer, Fmt.t("trade.col.qty").getString(), 160, y, Ui.MUTED, 0.85f);
        Ui.rtext(c, textRenderer, Fmt.t("trade.col.value").getString(), 235, y, Ui.MUTED, 0.85f);
        Ui.rtext(c, textRenderer, Fmt.t("trade.col.pnl").getString(), 310, y, Ui.MUTED, 0.85f);
        Ui.rtext(c, textRenderer, "%", SW - 16, y, Ui.MUTED, 0.85f);
        y += 12;
        int rows = 0;
        double total = 0, inv = 0;
        for (int k = 0; k < 2; k++) {
            for (int i = 0; i < n(k); i++) {
                double q = qty(k, i);
                if (q <= 0) continue;
                double val = q * price(k, i), cs = cost(k, i), pl = val - cs;
                total += val;
                inv += cs;
                if (rows < 12) {
                    boolean hv = hit(mx, my, 12, y - 2, SW - 24, 13);
                    if (hv) Ui.rect(c, 12, y - 2, SW - 24, 13, Ui.PANEL3);
                    Ui.circle(c, 20, y + 3, 3, color(k, i));
                    Ui.text(c, textRenderer, sym(k, i), 27, y, Ui.TEXT, 0.9f, true);
                    Ui.rtext(c, textRenderer, Fmt.qty(q), 160, y, Ui.TEXT, 0.85f);
                    Ui.rtext(c, textRenderer, "$" + Fmt.money((long) val), 235, y, Ui.TEXT, 0.85f);
                    Ui.rtext(c, textRenderer, (pl >= 0 ? "+" : "") + Fmt.money((long) pl), 310, y, pnlColor(pl), 0.85f);
                    Ui.rtext(c, textRenderer, Fmt.pct(cs > 0 ? pl / cs : 0), SW - 16, y, pnlColor(pl), 0.85f);
                    y += 14;
                }
                rows++;
            }
        }
        if (rows == 0) Ui.ctext(c, textRenderer, Fmt.t("trade.empty"), SW / 2f, 110, Ui.DIM, 1f);
        int fy = SH - 36;
        Ui.rect(c, 14, fy - 4, SW - 28, 1, Ui.BORDER);
        Ui.text(c, textRenderer, Fmt.t("trade.total", Fmt.money((long) total), Fmt.money((long) inv)), 16, fy, Ui.TEXT, 0.9f, true);
        Ui.text(c, textRenderer, Fmt.t("trade.realized", Fmt.money((long) m().realized), Fmt.money((long) m().dividends)), 16, fy + 11, Ui.MUTED, 0.85f, true);
    }

    // ---------------------------------------------------------------- input
    private void send(boolean buy, boolean all) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeByte(selKind);
        b.writeVarInt(selIdx);
        b.writeBoolean(buy);
        b.writeLong(amount.value());
        b.writeBoolean(all);
        ClientPlayNetworking.send(Net.TRADE, b);
        play(buy ? ModSounds.TRADE_BUY : ModSounds.TRADE_SELL, 1f);
    }

    @Override
    protected boolean click(double dmx, double dmy, int button) {
        if (m() == null) return false;
        int x = (int) dmx, y = (int) dmy;
        boolean wasFocus = amount.focused;
        amount.focused = false;
        for (int i = 0; i < 3; i++) {
            if (hit(x, y, 8 + i * 60, 32, 58, 15)) {
                tab = i;
                if (i < 2 && selKind != i) {
                    selKind = i;
                    selIdx = 0;
                    request();
                }
                play(ModSounds.UI_CLICK, 1f);
                return true;
            }
        }
        if (tab == 2) {
            int row = 0;
            int yy = 70;
            for (int k = 0; k < 2; k++) {
                for (int i = 0; i < n(k); i++) {
                    if (qty(k, i) <= 0) continue;
                    if (row < 12 && hit(x, y, 12, yy - 2, SW - 24, 13)) {
                        tab = k;
                        selKind = k;
                        selIdx = i;
                        request();
                        play(ModSounds.UI_CLICK, 1.2f);
                        return true;
                    }
                    if (row < 12) yy += 14;
                    row++;
                }
            }
            return false;
        }
        for (int i = 0; i < n(tab); i++) {
            if (hit(x, y, 8, 52 + i * 16, 128, 15)) {
                selKind = tab;
                selIdx = i;
                request();
                play(ModSounds.UI_CLICK, 1.2f);
                return true;
            }
        }
        if (hit(x, y, RX, 192, 100, 15)) {
            amount.focused = true;
            return true;
        }
        for (int j = 0; j < 6; j++) {
            if (hit(x, y, RX + j * 38, 210, 34, 14)) {
                long cur = amount.value();
                long[] add = {100, 1000, 10000, 100000};
                if (j < 4) amount.set(cur + add[j]);
                else if (j == 4) amount.set(Math.max(m().balance, 0));
                else amount.set(0);
                play(ModSounds.UI_CLICK, 1.1f);
                return true;
            }
        }
        double q = qty(selKind, selIdx);
        long amt = amount.value();
        if (hit(x, y, RX, 228, 72, 18) && amt > 0) {
            send(true, false);
            return true;
        }
        if (hit(x, y, RX + 76, 228, 72, 18) && q > 0 && amt > 0) {
            send(false, false);
            return true;
        }
        if (hit(x, y, RX + 152, 228, 76, 18) && q > 0) {
            send(false, true);
            return true;
        }
        return wasFocus;
    }

    @Override
    protected boolean typing() {
        return amount.focused;
    }

    @Override
    protected boolean typedChar(char chr) {
        return amount.charTyped(chr);
    }

    @Override
    protected boolean pressedKey(int keyCode) {
        return amount.keyPressed(keyCode);
    }
}
