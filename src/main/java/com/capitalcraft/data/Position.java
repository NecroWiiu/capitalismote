package com.capitalcraft.data;

import net.minecraft.nbt.NbtCompound;

public class Position {
    public double qty;
    public double cost;

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putDouble("q", qty);
        n.putDouble("c", cost);
        return n;
    }

    public static Position fromNbt(NbtCompound n) {
        Position p = new Position();
        p.qty = n.getDouble("q");
        p.cost = n.getDouble("c");
        return p;
    }
}
