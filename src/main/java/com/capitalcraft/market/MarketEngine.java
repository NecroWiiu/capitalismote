package com.capitalcraft.market;

import com.capitalcraft.data.AssetState;
import com.capitalcraft.data.MarketData;

import java.util.Random;

public final class MarketEngine {
    public static final double SECONDS_PER_DAY = 1200.0;

    private MarketEngine() {}

    public static void init(MarketData m, Random r) {
        for (int i = 0; i < Assets.STOCKS.length; i++) {
            m.stocks[i] = backfill(Assets.STOCKS[i].price(), Assets.STOCKS[i].vol(), r);
        }
        for (int i = 0; i < Assets.CRYPTOS.length; i++) {
            Assets.Crypto c = Assets.CRYPTOS[i];
            m.cryptos[i] = c.stable() ? backfill(c.price(), 0.0008, r) : backfill(c.price(), c.vol(), r);
        }
    }

    private static AssetState backfill(double price, double dailyVol, Random r) {
        AssetState s = new AssetState(price);
        double sigma = dailyVol / Math.sqrt(120.0);
        double p = price;
        float hi = (float) price, lo = (float) price;
        for (int i = AssetState.HIST - 1; i >= 0; i--) {
            s.hist[i] = (float) p;
            hi = Math.max(hi, (float) p);
            lo = Math.min(lo, (float) p);
            p *= Math.exp(-sigma * r.nextGaussian());
        }
        s.histLen = AssetState.HIST;
        s.open = s.hist[0];
        s.high = hi;
        s.low = lo;
        return s;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Called once per second. */
    public static void step(MarketData m, Random r) {
        m.sentiment = clamp(m.sentiment - m.sentiment * 0.01 + r.nextGaussian() * 0.03, -1, 1);
        for (int i = 0; i < Assets.STOCKS.length; i++) {
            Assets.Stock d = Assets.STOCKS[i];
            stepAsset(m.stocks[i], d.vol(), d.drift(), 1.0, m.sentiment, r, 0.01);
        }
        for (int i = 0; i < Assets.CRYPTOS.length; i++) {
            Assets.Crypto c = Assets.CRYPTOS[i];
            AssetState s = m.cryptos[i];
            if (c.stable()) {
                s.price = clamp(s.price + (c.price() - s.price) * 0.04 + r.nextGaussian() * 0.0005, 0.93, 1.07);
                track(s);
                continue;
            }
            stepAsset(s, c.vol(), c.drift(), c.meme() ? 2.2 : 1.5, m.sentiment, r, c.price() * 1e-4);
            s.difficulty = clamp(s.difficulty + (1.0 - s.difficulty) * 0.004 + r.nextGaussian() * 0.012, 0.7, 1.45);
        }
        if (r.nextInt(100) == 0) makeEvent(m, r);
    }

    private static void stepAsset(AssetState s, double dailyVol, double dailyDrift, double beta,
                                  double sentiment, Random r, double minPrice) {
        double sigma = dailyVol / Math.sqrt(SECONDS_PER_DAY);
        double mu = dailyDrift / SECONDS_PER_DAY;
        double rev = Math.log(s.base / s.price) * 0.0004;
        double ev = 0;
        if (s.eventTicks > 0) {
            ev = s.eventDrift;
            s.eventTicks--;
        }
        double sent = sentiment * 0.00003 * beta;
        double jump = r.nextInt(9000) == 0 ? r.nextGaussian() * dailyVol * 1.5 : 0;
        double ret = mu + rev + sent + ev + sigma * r.nextGaussian() + jump;
        s.price = Math.max(s.price * Math.exp(ret), minPrice);
        s.base *= Math.exp(sigma * 0.15 * r.nextGaussian());
        track(s);
    }

    private static void track(AssetState s) {
        if (s.price > s.high) s.high = s.price;
        if (s.price < s.low) s.low = s.price;
    }

    private static void apply(AssetState s, double move, int dur) {
        s.price *= Math.exp(move * 0.4);
        s.base *= Math.exp(move * 0.5);
        s.eventDrift = move * 0.6 / dur;
        s.eventTicks = dur;
        track(s);
    }

    private static int randomCoin(Random r) {
        int i;
        do {
            i = r.nextInt(Assets.CRYPTOS.length);
        } while (Assets.CRYPTOS[i].stable());
        return i;
    }

    public static void makeEvent(MarketData m, Random r) {
        int type = r.nextInt(12);
        int kind = 3;
        int idx = 0;
        int dur = 60 + r.nextInt(120);
        double sign = (type % 2 == 0) ? 1 : -1;
        switch (type) {
            case 0, 1 -> {
                kind = 0;
                idx = r.nextInt(Assets.STOCKS.length);
                apply(m.stocks[idx], sign * (0.03 + r.nextDouble() * 0.09), dur);
            }
            case 2, 3 -> {
                kind = 2;
                idx = r.nextInt(Assets.SECTORS.length);
                double mv = sign * (0.02 + r.nextDouble() * 0.06);
                for (int i = 0; i < Assets.STOCKS.length; i++) {
                    if (Assets.STOCKS[i].sector() == idx) apply(m.stocks[i], mv, dur);
                }
            }
            case 4, 5 -> {
                kind = 1;
                idx = randomCoin(r);
                apply(m.cryptos[idx], sign * (0.08 + r.nextDouble() * 0.22), dur);
            }
            case 6, 7 -> {
                double mv = sign * (0.05 + r.nextDouble() * 0.10);
                for (int i = 0; i < Assets.CRYPTOS.length; i++) {
                    if (!Assets.CRYPTOS[i].stable()) apply(m.cryptos[i], mv, dur);
                }
            }
            case 8 -> {
                m.sentiment = clamp(m.sentiment + 0.4, -1, 1);
                for (AssetState s : m.stocks) apply(s, 0.02 + r.nextDouble() * 0.04, dur);
            }
            case 9 -> {
                m.sentiment = clamp(m.sentiment - 0.5, -1, 1);
                for (AssetState s : m.stocks) apply(s, -(0.03 + r.nextDouble() * 0.05), dur);
                for (int i = 0; i < Assets.CRYPTOS.length; i++) {
                    if (!Assets.CRYPTOS[i].stable()) apply(m.cryptos[i], -(0.04 + r.nextDouble() * 0.08), dur);
                }
            }
            default -> {
                kind = 1;
                idx = Assets.cryptoIndex(r.nextBoolean() ? "CRC" : "WLF");
                apply(m.cryptos[idx], type == 10 ? -(0.35 + r.nextDouble() * 0.25) : (0.30 + r.nextDouble() * 0.40), dur);
            }
        }
        m.addNews(new MarketData.News(type, kind, idx, System.currentTimeMillis()));
    }

    public static void sampleHistory(MarketData m) {
        for (AssetState s : m.stocks) s.push((float) s.price);
        for (AssetState s : m.cryptos) s.push((float) s.price);
    }

    public static void newDay(MarketData m) {
        for (AssetState s : m.stocks) {
            s.open = s.price;
            s.high = s.low = s.price;
        }
        for (AssetState s : m.cryptos) {
            s.open = s.price;
            s.high = s.low = s.price;
        }
    }
}
