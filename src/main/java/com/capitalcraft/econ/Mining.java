package com.capitalcraft.econ;

import com.capitalcraft.market.Assets;

public final class Mining {
    private Mining() {}

    public static final double[] BASE_USD = {0.45, 1.8, 7.0};
    public static final double[] OC_YIELD = {1.0, 1.4, 1.8};
    public static final double[] POWER = {0.05, 0.20, 0.80};
    public static final double[] OC_POWER = {1.0, 1.7, 2.6};

    private static int t(int tier) {
        return Math.max(0, Math.min(2, tier - 1));
    }

    public static double usdPerSec(int tier, int oc, Assets.Crypto c, double difficulty) {
        if (!c.minable()) return 0;
        return BASE_USD[t(tier)] * OC_YIELD[Math.max(0, Math.min(2, oc))] * c.payout() / Math.max(0.1, difficulty);
    }

    public static double powerCost(int tier, int oc) {
        return POWER[t(tier)] * OC_POWER[Math.max(0, Math.min(2, oc))];
    }
}
