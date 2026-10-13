package com.capitalcraft.data;

import com.capitalcraft.market.MarketEngine;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CapitalState extends PersistentState {
    public static final String KEY = "capitalcraft";

    public final Map<UUID, PlayerData> players = new HashMap<>();
    public final MarketData market = new MarketData();

    public CapitalState() {
        MarketEngine.init(market, new Random());
    }

    public static CapitalState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(CapitalState::fromNbt, CapitalState::new, KEY);
    }

    public PlayerData player(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerData());
    }

    public static CapitalState fromNbt(NbtCompound n) {
        CapitalState s = new CapitalState();
        if (n.contains("market")) s.market.readNbt(n.getCompound("market"));
        NbtCompound ps = n.getCompound("players");
        for (String k : ps.getKeys()) {
            try {
                s.players.put(UUID.fromString(k), PlayerData.fromNbt(ps.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.put("market", market.toNbt());
        NbtCompound ps = new NbtCompound();
        players.forEach((k, v) -> ps.put(k.toString(), v.toNbt()));
        nbt.put("players", ps);
        return nbt;
    }
}
