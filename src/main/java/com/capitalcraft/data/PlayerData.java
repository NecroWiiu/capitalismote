package com.capitalcraft.data;

import net.minecraft.nbt.NbtCompound;

import java.util.HashMap;
import java.util.Map;

public class PlayerData {
    public long balance;
    public boolean welcomed;
    public long wagered, won, lost;
    public int bets, betsWon;
    public int jobsDone, streak, jobDay = -1;
    public String jobId = "";
    public String lastJobId = "";
    public long[] baseline = new long[0];
    public double powerDebt;
    public final Map<String, Double> counters = new HashMap<>();
    public final Map<String, Position> stocks = new HashMap<>();
    public final Map<String, Position> crypto = new HashMap<>();

    public long counter(String key) {
        return counters.getOrDefault(key, 0.0).longValue();
    }

    public void add(String key, double v) {
        counters.merge(key, v, Double::sum);
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putLong("balance", balance);
        n.putBoolean("welcomed", welcomed);
        n.putLong("wagered", wagered);
        n.putLong("won", won);
        n.putLong("lost", lost);
        n.putInt("bets", bets);
        n.putInt("betsWon", betsWon);
        n.putInt("jobsDone", jobsDone);
        n.putInt("streak", streak);
        n.putInt("jobDay", jobDay);
        n.putString("jobId", jobId);
        n.putString("lastJobId", lastJobId);
        n.putLongArray("baseline", baseline);
        n.putDouble("powerDebt", powerDebt);
        NbtCompound c = new NbtCompound();
        counters.forEach(c::putDouble);
        n.put("counters", c);
        NbtCompound s = new NbtCompound();
        stocks.forEach((k, v) -> s.put(k, v.toNbt()));
        n.put("stocks", s);
        NbtCompound cr = new NbtCompound();
        crypto.forEach((k, v) -> cr.put(k, v.toNbt()));
        n.put("crypto", cr);
        return n;
    }

    public static PlayerData fromNbt(NbtCompound n) {
        PlayerData d = new PlayerData();
        d.balance = n.getLong("balance");
        d.welcomed = n.getBoolean("welcomed");
        d.wagered = n.getLong("wagered");
        d.won = n.getLong("won");
        d.lost = n.getLong("lost");
        d.bets = n.getInt("bets");
        d.betsWon = n.getInt("betsWon");
        d.jobsDone = n.getInt("jobsDone");
        d.streak = n.getInt("streak");
        d.jobDay = n.contains("jobDay") ? n.getInt("jobDay") : -1;
        d.jobId = n.getString("jobId");
        d.lastJobId = n.getString("lastJobId");
        d.baseline = n.getLongArray("baseline");
        d.powerDebt = n.getDouble("powerDebt");
        NbtCompound c = n.getCompound("counters");
        for (String k : c.getKeys()) d.counters.put(k, c.getDouble(k));
        NbtCompound s = n.getCompound("stocks");
        for (String k : s.getKeys()) d.stocks.put(k, Position.fromNbt(s.getCompound(k)));
        NbtCompound cr = n.getCompound("crypto");
        for (String k : cr.getKeys()) d.crypto.put(k, Position.fromNbt(cr.getCompound(k)));
        return d;
    }
}
