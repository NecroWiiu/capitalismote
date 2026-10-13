package com.capitalcraft.client.ui;

import com.capitalcraft.Fmt;
import com.capitalcraft.client.ClientData;
import com.capitalcraft.econ.ValueTable;
import com.capitalcraft.item.MenuItem;
import com.capitalcraft.job.Jobs;
import com.capitalcraft.net.Net;
import com.capitalcraft.registry.ModSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The "Item Upgrader": stake an item (or coins), pick a target, spin the wheel. */
public class CasinoScreen extends CapScreen {
    private static final int SW = 320, SH = 266;
    private static final float TAU = Ui.TAU;
    private static final long SPIN_MS = 4300;
    private static final int LX = 10, PY = 24, PW = 92, PH = 108, RX = SW - 102;
    private static final int GX = 70, GY = 174;

    private enum Bottom { INVENTORY, STAKE_COINS, CATALOG, TARGET_COINS }

    private int stakeKind, stakeSlot = -1, stakeCount;
    private long stakeCoins;
    private int targetKind;
    private Item targetItem;
    private long targetCoins;
    private Bottom bottom = Bottom.INVENTORY;
    private final Input coinInput = new Input(13, true);
    private final Input search = new Input(20, false);
    private List<ClientData.Entry> filtered = new ArrayList<>();
    private String lastSearch = null;
    private int page;

    private float pointer;
    private boolean waiting, spinning;
    private long waitStart, spinStart;
    private float spinFrom, spinTo;
    private int lastTickIdx = -1;
    private boolean pendingWin;
    private double lockedChance = -1;
    private ItemStack pendingReward = ItemStack.EMPTY;
    private long pendingCoins;
    private int resultState;
    private long resultAt;
    private Text resultText = Text.empty();
    private ItemStack[] frozen;
    private long frozenBalance;

    public CasinoScreen() {
        super(Fmt.t("casino.title"), SW, SH);
    }

    // ------------------------------------------------------------------ model
    private ItemStack inv(int slot) {
        if (frozen != null) return frozen[slot];
        return client.player.getInventory().main.get(slot);
    }

    private long balance() {
        return frozen != null ? frozenBalance : ClientData.balance;
    }

    private boolean locked(ItemStack s) {
        return Jobs.isKit(s) || s.getItem() instanceof MenuItem;
    }

    private long stakeValue() {
        if (stakeKind == 1) return stakeCoins;
        if (stakeSlot < 0 || stakeCount <= 0) return 0;
        ItemStack s = inv(stakeSlot);
        if (s.isEmpty()) return 0;
        return ValueTable.unitValue(s, ClientData::unit) * Math.min(stakeCount, s.getCount());
    }

    private long targetValue() {
        if (targetKind == 1) return targetCoins;
        return targetItem == null ? 0 : ClientData.unit(targetItem);
    }

    private double rawChance() {
        long s = stakeValue(), t = targetValue();
        if (s <= 0 || t <= 0) return 0;
        return (double) s / t * (1.0 - ClientData.houseEdge);
    }

    private double chance() {
        return Math.min(rawChance(), ClientData.maxChance);
    }

    private Text problem() {
        long sv = stakeValue(), tv = targetValue();
        if (sv <= 0) return Fmt.t("casino.p.stake");
        if (tv <= 0) return Fmt.t("casino.p.target");
        if (stakeKind == 1 && stakeCoins > ClientData.balance) return Fmt.t("casino.err.funds");
        if (stakeKind == 0 && targetKind == 0 && targetItem != null && inv(stakeSlot).isOf(targetItem)) return Fmt.t("casino.p.same");
        if (rawChance() < ClientData.minChance) return Fmt.t("casino.err.chance");
        return null;
    }

    private void normalize() {
        if (frozen != null || client == null || client.player == null) return;
        if (stakeKind == 0) {
            if (stakeSlot >= 0) {
                ItemStack s = inv(stakeSlot);
                if (s.isEmpty() || locked(s)) {
                    stakeSlot = -1;
                    stakeCount = 0;
                } else {
                    stakeCount = Math.max(1, Math.min(stakeCount, s.getCount()));
                }
            }
        } else if (bottom == Bottom.STAKE_COINS) {
            stakeCoins = Math.min(coinInput.value(), ClientData.balance);
        }
        if (bottom == Bottom.TARGET_COINS) targetCoins = coinInput.value();
    }

    private void refreshCatalog() {
        String q = search.text.trim().toLowerCase(Locale.ROOT);
        if (q.equals(lastSearch)) return;
        lastSearch = q;
        filtered = new ArrayList<>();
        for (ClientData.Entry e : ClientData.catalog) if (q.isEmpty() || e.search().contains(q)) filtered.add(e);
        page = 0;
    }

    // ------------------------------------------------------------------ result / wheel
    public void onResult(boolean ok, Text msg, boolean win, float roll, float chance, ItemStack reward, long coins) {
        long now = System.currentTimeMillis();
        if (!ok) {
            waiting = false;
            frozen = null;
            lockedChance = -1;
            resultState = 3;
            resultText = msg;
            resultAt = now;
            play(ModSounds.ERROR, 1f);
            return;
        }
        pendingWin = win;
        pendingReward = reward;
        pendingCoins = coins;
        lockedChance = chance;
        float from = pointer % TAU;
        float finalAngle = roll * TAU;
        float delta = finalAngle - from;
        while (delta <= 0.2f) delta += TAU;
        spinFrom = from;
        spinTo = from + delta + TAU * 6f;
        spinStart = now;
        spinning = true;
        lastTickIdx = -1;
    }

    private void updateWheel(long now) {
        if (waiting && !spinning && now - waitStart > 6000) {
            waiting = false;
            frozen = null;
            lockedChance = -1;
        }
        if (spinning) {
            float t = Math.min(1f, (now - spinStart) / (float) SPIN_MS);
            float e = 1f - (float) Math.pow(1f - t, 4);
            pointer = spinFrom + (spinTo - spinFrom) * e;
            int idx = (int) Math.floor(pointer / (TAU / 60f));
            if (idx != lastTickIdx) {
                lastTickIdx = idx;
                play(ModSounds.SPIN_TICK, 0.9f + 0.6f * (1f - t));
            }
            if (t >= 1f) {
                spinning = false;
                waiting = false;
                pointer = pointer % TAU;
                frozen = null;
                resultState = pendingWin ? 1 : 2;
                resultAt = now;
                if (pendingWin) {
                    play(lockedChance < 0.12 ? ModSounds.JACKPOT : ModSounds.WIN, 1f);
                    play(ModSounds.COIN, 1f);
                } else {
                    play(ModSounds.LOSE, 1f);
                }
            }
        }
        if (resultState != 0 && !spinning && !waiting && now - resultAt > 3800) {
            resultState = 0;
            lockedChance = -1;
        }
    }

    private void send() {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeByte(stakeKind);
        b.writeInt(stakeKind == 0 ? stakeSlot : -1);
        b.writeInt(stakeCount);
        b.writeLong(stakeCoins);
        b.writeByte(targetKind);
        b.writeIdentifier(targetKind == 0 && targetItem != null ? Registries.ITEM.getId(targetItem) : new Identifier("minecraft", "air"));
        b.writeLong(targetCoins);
        frozen = new ItemStack[36];
        for (int i = 0; i < 36; i++) frozen[i] = client.player.getInventory().main.get(i).copy();
        frozenBalance = ClientData.balance;
        lockedChance = chance();
        waiting = true;
        waitStart = System.currentTimeMillis();
        resultState = 0;
        ClientPlayNetworking.send(Net.CASINO_PLAY, b);
        play(ModSounds.SPIN_START, 1f);
    }

    // ------------------------------------------------------------------ drawing
    @Override
    protected void draw(DrawContext c, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        updateWheel(now);
        normalize();
        refreshCatalog();

        Ui.panel(c, 0, 0, SW, SH, Ui.BG, Ui.BORDER_HI);
        Ui.ctext(c, textRenderer, Fmt.t("casino.title"), SW / 2f, 7, Ui.TEXT, 1.5f);
        drawSide(c, mx, my, true);
        drawSide(c, mx, my, false);
        drawWheel(c, now);
        drawButtons(c, mx, my);
        drawBottom(c, mx, my);
    }

    private void drawItemScaled(DrawContext c, ItemStack s, int x, int y, float sc) {
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x, y, 0f);
        m.scale(sc, sc, 1f);
        c.drawItem(s, 0, 0);
        m.pop();
    }

    private void drawSide(DrawContext c, int mx, int my, boolean left) {
        int x = left ? LX : RX;
        boolean hover = hit(mx, my, x, PY, PW, PH - 24);
        Ui.panel(c, x, PY, PW, PH, Ui.PANEL, Ui.BORDER);
        Ui.hex(c, x + 2, PY + 2, PW - 4, PH - 4);
        int sx = x + PW / 2 - 15, sy = PY + 8;
        Ui.panel(c, sx, sy, 30, 30, Ui.BG, hover ? Ui.GOLD : Ui.BORDER);
        String name = "";
        if (left) {
            if (stakeKind == 0) {
                ItemStack s = stakeSlot >= 0 ? inv(stakeSlot) : ItemStack.EMPTY;
                if (!s.isEmpty() && stakeCount > 0) {
                    drawItemScaled(c, s, sx + 3, sy + 3, 1.5f);
                    Ui.rtext(c, textRenderer, "x" + stakeCount, sx + 29, sy + 21, Ui.TEXT, 0.8f);
                    name = s.getName().getString();
                } else {
                    Ui.ctext(c, textRenderer, "?", sx + 15, sy + 11, Ui.DIM, 1.5f);
                }
            } else {
                Ui.coin(c, textRenderer, sx + 15, sy + 15, 11, Ui.GOLD, "$");
            }
        } else {
            if (targetKind == 0) {
                if (targetItem != null) {
                    drawItemScaled(c, new ItemStack(targetItem), sx + 3, sy + 3, 1.5f);
                    name = targetItem.getName().getString();
                } else {
                    Ui.ctext(c, textRenderer, "?", sx + 15, sy + 11, Ui.DIM, 1.5f);
                }
            } else {
                Ui.coin(c, textRenderer, sx + 15, sy + 15, 11, Ui.GREEN, "$");
            }
        }
        long value = left ? stakeValue() : targetValue();
        Ui.ctext(c, textRenderer, Fmt.t("casino.value", Fmt.money(value)), x + PW / 2f, PY + 44, Ui.MUTED, 1f);
        if (!name.isEmpty()) Ui.ctext(c, textRenderer, textRenderer.trimToWidth(name, PW - 8), x + PW / 2f, PY + 56, Ui.DIM, 0.9f);
        int kind = left ? stakeKind : targetKind;
        int ty = PY + PH - 22;
        Ui.chip(c, textRenderer, x + 6, ty, 38, 16, Fmt.t("casino.tab.item"), Ui.TEXT, hit(mx, my, x + 6, ty, 38, 16), kind == 0);
        Ui.chip(c, textRenderer, x + 48, ty, 38, 16, Fmt.t("casino.tab.coins"), Ui.TEXT, hit(mx, my, x + 48, ty, 38, 16), kind == 1);
    }

    private void drawWheel(DrawContext c, long now) {
        float cx = SW / 2f, cy = 80f;
        final float R0 = 34, R1 = 46;
        for (int i = 0; i < 60; i++) {
            float a = TAU * i / 60f;
            boolean major = i % 5 == 0;
            Ui.radial(c, cx, cy, R1 + 3, R1 + (major ? 8 : 5), a, major ? 1.6f : 1.0f, major ? 0xFF8B94AE : 0xFF3B4459);
        }
        Ui.arc(c, cx, cy, R0, R1, 0f, TAU, 0xFF20283A, 0xFF20283A);
        double ch = (waiting || spinning || (resultState == 1 || resultState == 2)) && lockedChance >= 0 ? lockedChance : chance();
        int colA = 0xFFFFD25A, colB = 0xFFF08A2B;
        if (resultState == 1) {
            colA = 0xFF8CF2A5;
            colB = 0xFF2DBE6C;
        }
        if (ch > 0) Ui.arc(c, cx, cy, R0 + 1, R1 - 1, 0f, (float) (ch * TAU), colA, colB);
        Ui.circle(c, cx, cy, R0 - 2, 0xFF11151F);
        // gold marker at 12 o'clock
        int my2 = (int) (cy - R1 - 12);
        for (int i = 0; i < 5; i++) c.fill((int) cx - 4 + i, my2 + i, (int) cx + 5 - i, my2 + i + 1, Ui.GOLD);
        Ui.radial(c, cx, cy, R0, R1, 0f, 1.5f, 0xFFFFFFFF);
        float pa = pointer;
        Ui.radial(c, cx, cy, R0 - 10, R1 + 4, pa, 2.2f, resultState == 2 ? Ui.RED : 0xFFFFFFFF);
        float px = cx + (float) Math.sin(pa) * (R1 + 4), py = cy - (float) Math.cos(pa) * (R1 + 4);
        Ui.circle(c, px, py, 2.6f, resultState == 2 ? Ui.RED : resultState == 1 ? Ui.GREEN : Ui.GOLD);

        if (resultState == 1) {
            Ui.ctext(c, textRenderer, Fmt.t("casino.win"), cx, cy - 12, Ui.GREEN, 1.3f);
            Text sub = pendingReward.isEmpty() ? Fmt.t("casino.win.coins", Fmt.money(pendingCoins)) : pendingReward.getName();
            Ui.ctext(c, textRenderer, textRenderer.trimToWidth(sub.getString(), 58), cx, cy + 4, Ui.TEXT, 0.9f);
        } else if (resultState == 2) {
            Ui.ctext(c, textRenderer, Fmt.t("casino.lose"), cx, cy - 8, Ui.RED, 1.3f);
        } else if (resultState == 3) {
            Ui.ctext(c, textRenderer, textRenderer.trimToWidth(resultText.getString(), 62), cx, cy - 4, Ui.RED, 0.8f);
        } else if (waiting && !spinning) {
            Ui.ctext(c, textRenderer, "...", cx, cy - 6, Ui.MUTED, 1.5f);
        } else {
            int col = ch < 0.2 ? 0xFFE0573A : ch < 0.5 ? Ui.ORANGE : ch < 0.75 ? Ui.YELLOW : Ui.GREEN;
            Ui.ctext(c, textRenderer, String.format(Locale.ROOT, "%.2f%%", ch * 100), cx, cy - 12, col, 1.5f);
            Ui.ctext(c, textRenderer, Fmt.t("casino.chance"), cx, cy + 6, Ui.MUTED, 0.85f);
        }
    }

    private static final String[] MULT_LABELS = {"x2", "x4", "x8", "30%", "50%", "70%"};
    private static final int[] MULT_COLORS = {Ui.TEXT, Ui.TEXT, Ui.TEXT, Ui.RED, Ui.YELLOW, Ui.GREEN};

    private void drawButtons(DrawContext c, int mx, int my) {
        int by = 138;
        Text prob = problem();
        boolean can = prob == null && !waiting && !spinning;
        boolean hv = hit(mx, my, 10, by, 150, 20);
        Ui.goldButton(c, textRenderer, 10, by, 150, 20, Fmt.t("casino.upgrade"), hv, can);
        if (hv && prob != null && !waiting && !spinning) tip(prob.copy().formatted(Formatting.RED));
        for (int i = 0; i < 6; i++) {
            int x = 168 + i * 24;
            boolean h = hit(mx, my, x, by, 22, 20);
            Ui.chip(c, textRenderer, x, by, 22, 20, MULT_LABELS[i], MULT_COLORS[i], h, false);
            if (h) tip(Fmt.t(i < 3 ? "casino.mult" : "casino.pct", MULT_LABELS[i]));
        }
    }

    private void cell(DrawContext c, int mx, int my, int x, int y, int slot) {
        Ui.rect(c, x, y, 20, 20, 0xFF0B0E15);
        Ui.frame(c, x, y, 20, 20, slot == stakeSlot ? Ui.GOLD : Ui.BORDER);
        ItemStack s = inv(slot);
        if (s.isEmpty()) return;
        c.drawItemInSlot(textRenderer, s, x + 2, y + 2);
        if (locked(s)) Ui.rect(c, x + 1, y + 1, 18, 18, 0x99000000);
        if (slot == stakeSlot) Ui.rect(c, x + 1, y + 1, 18, 18, 0x33E9B949);
        if (hit(mx, my, x, y, 20, 20)) {
            Ui.rect(c, x + 1, y + 1, 18, 18, 0x22FFFFFF);
            tipItem(s, ValueTable.unitValue(s, ClientData::unit));
        }
    }

    private void drawBottom(DrawContext c, int mx, int my) {
        // balance + stats in the margins
        Ui.panel(c, 10, 170, 54, 36, Ui.PANEL, Ui.BORDER);
        Ui.ctext(c, textRenderer, Fmt.t("casino.balance"), 37, 174, Ui.MUTED, 0.8f);
        Ui.ctext(c, textRenderer, "$" + Fmt.compact(balance()), 37, 187, Ui.GOLD, 0.95f);
        Ui.panel(c, SW - 64, 170, 54, 36, Ui.PANEL, Ui.BORDER);
        Ui.ctext(c, textRenderer, Fmt.t("casino.bets"), SW - 37, 174, Ui.MUTED, 0.8f);
        Ui.ctext(c, textRenderer, ClientData.betsWon + "/" + ClientData.bets, SW - 37, 187, Ui.TEXT, 0.95f);

        switch (bottom) {
            case INVENTORY -> {
                Ui.text(c, textRenderer, Fmt.t("casino.inventory"), GX, 162, Ui.MUTED, 1f, true);
                for (int r = 0; r < 3; r++) for (int col = 0; col < 9; col++) cell(c, mx, my, GX + col * 20, GY + r * 20, 9 + r * 9 + col);
                for (int col = 0; col < 9; col++) cell(c, mx, my, GX + col * 20, 238, col);
            }
            case CATALOG -> {
                search.draw(c, textRenderer, GX, 160, 180, 13, Fmt.t("casino.search"));
                int per = 36, pages = Math.max(1, (filtered.size() + per - 1) / per);
                page = Math.max(0, Math.min(page, pages - 1));
                for (int i = 0; i < per; i++) {
                    int idx = page * per + i;
                    int x = GX + (i % 9) * 20, y = 176 + (i / 9) * 20;
                    Ui.rect(c, x, y, 20, 20, 0xFF0B0E15);
                    Ui.frame(c, x, y, 20, 20, idx < filtered.size() && filtered.get(idx).item() == targetItem ? Ui.GOLD : Ui.BORDER);
                    if (idx >= filtered.size()) continue;
                    ClientData.Entry e = filtered.get(idx);
                    ItemStack st = new ItemStack(e.item());
                    c.drawItem(st, x + 2, y + 2);
                    if (hit(mx, my, x, y, 20, 20)) {
                        Ui.rect(c, x + 1, y + 1, 18, 18, 0x22FFFFFF);
                        tipItem(st, e.value());
                    }
                }
                boolean pl = hit(mx, my, 46, 206, 18, 16), nr = hit(mx, my, SW - 64, 206, 18, 16);
                Ui.chip(c, textRenderer, 46, 206, 18, 16, "<", Ui.TEXT, pl, false);
                Ui.chip(c, textRenderer, SW - 64, 206, 18, 16, ">", Ui.TEXT, nr, false);
                Ui.ctext(c, textRenderer, (page + 1) + "/" + pages, SW - 55, 226, Ui.MUTED, 0.9f);
            }
            default -> {
                boolean stake = bottom == Bottom.STAKE_COINS;
                Ui.ctext(c, textRenderer, Fmt.t(stake ? "casino.coins.stake" : "casino.coins.target"), SW / 2f, 164, Ui.MUTED, 1f);
                coinInput.draw(c, textRenderer, 95, 178, 130, 18, Fmt.t("casino.coins.hint"));
                String[] a = {"+100", "+1K", "+10K", "+100K"};
                String[] b2 = {"1/2", "x2", "MAX", "X"};
                for (int i = 0; i < 4; i++) {
                    Ui.chip(c, textRenderer, 80 + i * 44, 204, 40, 16, a[i], Ui.TEXT, hit(mx, my, 80 + i * 44, 204, 40, 16), false);
                    Ui.chip(c, textRenderer, 80 + i * 44, 224, 40, 16, b2[i], i == 3 ? Ui.RED : Ui.TEXT, hit(mx, my, 80 + i * 44, 224, 40, 16), false);
                }
            }
        }
    }

    // ------------------------------------------------------------------ input
    private void setBottomForStake() {
        bottom = stakeKind == 0 ? Bottom.INVENTORY : Bottom.STAKE_COINS;
        if (stakeKind == 1) coinInput.set(stakeCoins);
    }

    private void setBottomForTarget() {
        bottom = targetKind == 0 ? Bottom.CATALOG : Bottom.TARGET_COINS;
        if (targetKind == 1) coinInput.set(targetCoins);
    }

    private void applyMultiplier(double factor, boolean isChance) {
        long sv = stakeValue();
        if (sv <= 0) return;
        double want = isChance ? sv * (1.0 - ClientData.houseEdge) / factor : sv * factor;
        if (targetKind == 1) {
            targetCoins = Math.max(1, Math.round(want));
            if (bottom == Bottom.TARGET_COINS) coinInput.set(targetCoins);
        } else {
            ClientData.Entry e = ClientData.nearest(want);
            if (e != null) targetItem = e.item();
        }
        play(ModSounds.UI_CLICK, 1.2f);
    }

    private void coinChip(int i, int row) {
        long cur = coinInput.value();
        long next;
        if (row == 0) {
            long[] add = {100, 1000, 10000, 100000};
            next = cur + add[i];
        } else {
            next = switch (i) {
                case 0 -> cur / 2;
                case 1 -> cur * 2;
                case 2 -> bottom == Bottom.STAKE_COINS ? ClientData.balance : cur;
                default -> 0;
            };
        }
        if (bottom == Bottom.STAKE_COINS) next = Math.min(next, ClientData.balance);
        coinInput.set(next);
        play(ModSounds.UI_CLICK, 1.1f);
    }

    @Override
    protected boolean click(double dmx, double dmy, int button) {
        if (waiting || spinning) return true;
        int x = (int) dmx, y = (int) dmy;
        boolean wasCoin = coinInput.focused, wasSearch = search.focused;
        coinInput.focused = false;
        search.focused = false;
        // side tabs + panels
        for (int side = 0; side < 2; side++) {
            int px = side == 0 ? LX : RX;
            int ty = PY + PH - 22;
            for (int k = 0; k < 2; k++) {
                if (hit(x, y, px + 6 + k * 42, ty, 38, 16)) {
                    if (side == 0) {
                        stakeKind = k;
                        setBottomForStake();
                    } else {
                        targetKind = k;
                        setBottomForTarget();
                    }
                    play(ModSounds.UI_CLICK, 1f);
                    return true;
                }
            }
            if (hit(x, y, px, PY, PW, PH - 24)) {
                if (side == 0) setBottomForStake();
                else setBottomForTarget();
                play(ModSounds.UI_CLICK, 1f);
                return true;
            }
        }
        if (hit(x, y, 10, 138, 150, 20)) {
            if (problem() == null) send();
            else play(ModSounds.ERROR, 1f);
            return true;
        }
        for (int i = 0; i < 6; i++) {
            if (hit(x, y, 168 + i * 24, 138, 22, 20)) {
                if (i < 3) applyMultiplier(2 << i, false);
                else applyMultiplier(new double[]{0.3, 0.5, 0.7}[i - 3], true);
                return true;
            }
        }
        switch (bottom) {
            case INVENTORY -> {
                for (int r = 0; r < 4; r++) {
                    for (int col = 0; col < 9; col++) {
                        int cx = GX + col * 20, cy = r < 3 ? GY + r * 20 : 238;
                        if (!hit(x, y, cx, cy, 20, 20)) continue;
                        int slot = r < 3 ? 9 + r * 9 + col : col;
                        ItemStack s = inv(slot);
                        if (s.isEmpty() || locked(s)) {
                            play(ModSounds.ERROR, 1f);
                            return true;
                        }
                        if (button == 1) {
                            if (slot == stakeSlot) stakeCount--;
                            if (stakeCount <= 0) {
                                stakeSlot = -1;
                                stakeCount = 0;
                            }
                        } else if (hasShiftDown()) {
                            stakeSlot = slot;
                            stakeCount = s.getCount();
                        } else if (slot == stakeSlot) {
                            stakeCount = Math.min(s.getCount(), stakeCount + 1);
                        } else {
                            stakeSlot = slot;
                            stakeCount = 1;
                        }
                        play(ModSounds.UI_CLICK, 1.3f);
                        return true;
                    }
                }
            }
            case CATALOG -> {
                if (hit(x, y, GX, 160, 180, 13)) {
                    search.focused = true;
                    return true;
                }
                if (hit(x, y, 46, 206, 18, 16)) {
                    page--;
                    play(ModSounds.UI_CLICK, 1f);
                    return true;
                }
                if (hit(x, y, SW - 64, 206, 18, 16)) {
                    page++;
                    play(ModSounds.UI_CLICK, 1f);
                    return true;
                }
                for (int i = 0; i < 36; i++) {
                    int idx = page * 36 + i;
                    if (idx < filtered.size() && hit(x, y, GX + (i % 9) * 20, 176 + (i / 9) * 20, 20, 20)) {
                        targetItem = filtered.get(idx).item();
                        play(ModSounds.UI_CLICK, 1.3f);
                        return true;
                    }
                }
            }
            default -> {
                if (hit(x, y, 95, 178, 130, 18)) {
                    coinInput.focused = true;
                    return true;
                }
                for (int i = 0; i < 4; i++) {
                    if (hit(x, y, 80 + i * 44, 204, 40, 16)) {
                        coinChip(i, 0);
                        return true;
                    }
                    if (hit(x, y, 80 + i * 44, 224, 40, 16)) {
                        coinChip(i, 1);
                        return true;
                    }
                }
            }
        }
        return wasCoin || wasSearch;
    }

    @Override
    protected boolean scroll(double mx, double my, double amount) {
        if (bottom == Bottom.CATALOG) {
            page += amount < 0 ? 1 : -1;
            return true;
        }
        return false;
    }

    @Override
    protected boolean typing() {
        return coinInput.focused || search.focused;
    }

    @Override
    protected boolean typedChar(char chr) {
        return coinInput.charTyped(chr) || search.charTyped(chr);
    }

    @Override
    protected boolean pressedKey(int keyCode) {
        return coinInput.keyPressed(keyCode) || search.keyPressed(keyCode);
    }
}
