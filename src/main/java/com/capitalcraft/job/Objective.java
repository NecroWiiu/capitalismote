package com.capitalcraft.job;

import com.capitalcraft.data.PlayerData;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.StatHandler;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** One quota of a job. Progress is read from vanilla statistics (no mixins needed). */
public final class Objective {
    public enum Type { MINE, CRAFT, USE, KILL, STAT, COUNTER }

    public final Type type;
    public final List<String> targets;
    public final long count;

    private List<Block> blocks;
    private List<Item> items;
    private List<EntityType<?>> entities;
    private List<Identifier> stats;

    public Objective(Type type, List<String> targets, long count) {
        this.type = type;
        this.targets = targets;
        this.count = Math.max(1, count);
    }

    private static <T> List<T> resolve(Registry<T> reg, RegistryKey<? extends Registry<T>> key, List<String> targets) {
        List<T> out = new ArrayList<>();
        for (String t : targets) {
            if (t.startsWith("#")) {
                for (RegistryEntry<T> e : reg.iterateEntries(TagKey.of(key, new Identifier(t.substring(1))))) out.add(e.value());
            } else {
                Identifier id = new Identifier(t);
                if (reg.containsId(id)) out.add(reg.get(id));
            }
        }
        return out;
    }

    public synchronized long read(ServerPlayerEntity p, PlayerData d) {
        StatHandler sh = p.getStatHandler();
        long sum = 0;
        switch (type) {
            case MINE -> {
                if (blocks == null) blocks = resolve(Registries.BLOCK, RegistryKeys.BLOCK, targets);
                for (Block b : blocks) sum += sh.getStat(Stats.MINED.getOrCreateStat(b));
            }
            case CRAFT -> {
                if (items == null) items = resolve(Registries.ITEM, RegistryKeys.ITEM, targets);
                for (Item i : items) sum += sh.getStat(Stats.CRAFTED.getOrCreateStat(i));
            }
            case USE -> {
                if (items == null) items = resolve(Registries.ITEM, RegistryKeys.ITEM, targets);
                for (Item i : items) sum += sh.getStat(Stats.USED.getOrCreateStat(i));
            }
            case KILL -> {
                if (entities == null) entities = resolve(Registries.ENTITY_TYPE, RegistryKeys.ENTITY_TYPE, targets);
                for (EntityType<?> e : entities) sum += sh.getStat(Stats.KILLED.getOrCreateStat(e));
            }
            case STAT -> {
                if (stats == null) {
                    stats = new ArrayList<>();
                    for (String t : targets) {
                        Identifier s = Registries.CUSTOM_STAT.get(new Identifier(t));
                        if (s != null) stats.add(s);
                    }
                }
                for (Identifier s : stats) sum += sh.getStat(Stats.CUSTOM.getOrCreateStat(s));
            }
            case COUNTER -> {
                for (String t : targets) sum += d.counter(t);
            }
        }
        return sum;
    }
}
