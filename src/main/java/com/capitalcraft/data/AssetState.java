package com.capitalcraft.data;

import net.minecraft.nbt.NbtCompound;

public class AssetState {
    public static final int HIST = 120;

    public double price, open, high, low, base;
    public double difficulty = 1.0;
    public double eventDrift;
    public int eventTicks;
    public float[] hist = new float[HIST];
    public int histLen;

    public AssetState(double p) {
        price = open = high = low = base = p;
    }

    public void push(float v) {
        if (histLen < HIST) {
            hist[histLen++] = v;
        } else {
            System.arraycopy(hist, 1, hist, 0, HIST - 1);
            hist[HIST - 1] = v;
        }
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putDouble("p", price);
        n.putDouble("o", open);
        n.putDouble("h", high);
        n.putDouble("l", low);
        n.putDouble("b", base);
        n.putDouble("d", difficulty);
        n.putDouble("ed", eventDrift);
        n.putInt("et", eventTicks);
        int[] bits = new int[histLen];
        for (int i = 0; i < histLen; i++) bits[i] = Float.floatToIntBits(hist[i]);
        n.putIntArray("hist", bits);
        return n;
    }

    public static AssetState fromNbt(NbtCompound n, double fallback) {
        AssetState s = new AssetState(n.contains("p") ? n.getDouble("p") : fallback);
        if (n.contains("o")) s.open = n.getDouble("o");
        if (n.contains("h")) s.high = n.getDouble("h");
        if (n.contains("l")) s.low = n.getDouble("l");
        if (n.contains("b")) s.base = n.getDouble("b");
        if (n.contains("d")) s.difficulty = n.getDouble("d");
        s.eventDrift = n.getDouble("ed");
        s.eventTicks = n.getInt("et");
        int[] bits = n.getIntArray("hist");
        s.histLen = Math.min(bits.length, HIST);
        for (int i = 0; i < s.histLen; i++) s.hist[i] = Float.intBitsToFloat(bits[i]);
        return s;
    }
}
