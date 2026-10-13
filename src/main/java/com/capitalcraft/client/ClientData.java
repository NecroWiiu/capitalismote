package com.capitalcraft.client;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class ClientData {
    private ClientData() {}

    public record Entry(Item item, long value, String search) {}

    public static class JobInfo {
        public String id;
        public int tier, streak, secondsLeft;
        public long wage, recvMs;
        public long[] progress, target;

        public int secondsNow() {
            return (int) (secondsLeft - (System.currentTimeMillis() - recvMs) / 1000);
        }
    }

    public static class News {
        public int template, kind, idx, ago;
    }

    public static class Market {
        public long balance;
        public float sentiment;
        public double realized, dividends;
        public double[] sPrice, sOpen, sQty, sCost;
        public double[] cPrice, cOpen, cQty, cCost;
        public float[] cDiff;
        public List<News> news = new ArrayList<>();
        public int selKind, selIdx;
        public double selHigh, selLow;
        public float[] hist = new float[0];
    }

    public static class Gpu {
        public net.minecraft.util.math.BlockPos pos;
        public int tier, coin, overclock;
        public boolean power, throttled, noFunds, coolant;
        public float temp;
        public double usdPerSec, powerCost, session, total;
        public long balance;
        public double[] price, holdings, est;
        public float[] diff;
    }

    public static long balance;
    public static int bets, betsWon;
    public static long wagered, won;
    public static float houseEdge = 0.10f, minChance = 0.005f, maxChance = 0.90f;
    public static final Map<Item, Long> values = new HashMap<>();
    public static final List<Entry> catalog = new ArrayList<>();
    public static boolean hudVisible = true;
    public static JobInfo job;
    public static Market market;
    public static Gpu gpu;

    private static final Random RNG = new Random();

    public static long unit(Item it) {
        return values.getOrDefault(it, 1L);
    }

    public static void clear() {
        balance = 0;
        job = null;
        market = null;
        gpu = null;
        values.clear();
        catalog.clear();
    }

    public static void addValue(Identifier id, long value, boolean targetable) {
        Item it = Registries.ITEM.get(id);
        if (it == net.minecraft.item.Items.AIR) return;
        values.put(it, value);
        if (targetable) {
            String name = it.getName().getString().toLowerCase(Locale.ROOT);
            catalog.add(new Entry(it, value, name + " " + id.getPath()));
        }
    }

    public static void finishValues() {
        catalog.sort((a, b) -> a.value() != b.value() ? Long.compare(a.value(), b.value()) : a.search().compareTo(b.search()));
    }

    /** Catalog entry whose value is closest (log scale) to the wanted one; ties are randomised. */
    public static Entry nearest(double v) {
        if (catalog.isEmpty()) return null;
        double want = Math.max(1.0, v);
        int lo = 0, hi = catalog.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (catalog.get(mid).value() < want) lo = mid + 1;
            else hi = mid;
        }
        int best = lo;
        if (lo > 0) {
            double a = Math.abs(Math.log(catalog.get(lo - 1).value() / want));
            double b = Math.abs(Math.log(catalog.get(lo).value() / want));
            if (a < b) best = lo - 1;
        }
        long val = catalog.get(best).value();
        int a = best, b = best;
        while (a > 0 && catalog.get(a - 1).value() == val) a--;
        while (b < catalog.size() - 1 && catalog.get(b + 1).value() == val) b++;
        return catalog.get(a + RNG.nextInt(b - a + 1));
    }
}
