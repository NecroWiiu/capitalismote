package com.capitalcraft.block;

import com.capitalcraft.Config;
import com.capitalcraft.data.AssetState;
import com.capitalcraft.data.CapitalState;
import com.capitalcraft.data.PlayerData;
import com.capitalcraft.data.Position;
import com.capitalcraft.econ.Mining;
import com.capitalcraft.market.Assets;
import com.capitalcraft.registry.ModBlockEntities;
import com.capitalcraft.registry.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.UUID;

public class GpuBlockEntity extends BlockEntity {
    private UUID owner;
    public int coin = Assets.firstMinable();
    public boolean power = true;
    public int overclock = 0;
    public float temp = 30f;
    public boolean throttled = false;
    public boolean noFunds = false;
    public boolean coolant = false;
    public double sessionUsd, totalUsd, lastUsd, lastPower;
    private int timer, hum;

    public GpuBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GPU, pos, state);
    }

    public int tier() {
        return getCachedState().getBlock() instanceof GpuBlock g ? g.tier : 1;
    }

    public UUID getOwner() { return owner; }

    public void setOwner(UUID id) {
        this.owner = id;
        markDirty();
    }

    public boolean isOwner(ServerPlayerEntity p) {
        return owner != null && owner.equals(p.getUuid());
    }

    public static void tick(World world, BlockPos pos, BlockState state, GpuBlockEntity be) {
        if (!(world instanceof ServerWorld sw)) return;
        if (++be.timer < 20) return;
        be.timer = 0;
        be.second(sw, pos, state);
    }

    private static boolean hasCoolant(World w, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState s = w.getBlockState(pos.offset(d));
            if (s.getFluidState().isIn(FluidTags.WATER)
                    || s.isOf(Blocks.ICE) || s.isOf(Blocks.PACKED_ICE) || s.isOf(Blocks.BLUE_ICE)
                    || s.isOf(Blocks.SNOW_BLOCK) || s.isOf(Blocks.POWDER_SNOW)) return true;
        }
        return false;
    }

    private void second(ServerWorld w, BlockPos pos, BlockState state) {
        int tier = tier();
        coolant = hasCoolant(w, pos);
        double target = power ? 40 + tier * 6 + overclock * 20 - (coolant ? 14 : 0) - (throttled ? 25 : 0) : 26;
        temp += (float) Math.max(-1.5, Math.min(1.5, (target - temp) * 0.08));
        if (power && !throttled && temp >= 90f) throttled = true;
        if (throttled && temp <= 70f) throttled = false;
        lastUsd = 0;
        lastPower = 0;
        noFunds = false;
        boolean running = false;
        if (power && owner != null) {
            CapitalState st = CapitalState.get(w.getServer());
            PlayerData pd = st.player(owner);
            double cost = Mining.powerCost(tier, overclock) * Config.data.electricityMultiplier * (throttled ? 0.3 : 1.0);
            lastPower = cost;
            pd.powerDebt += cost;
            long pay = (long) pd.powerDebt;
            if (pay > 0) {
                if (pd.balance >= pay) {
                    pd.balance -= pay;
                    pd.powerDebt -= pay;
                } else {
                    noFunds = true;
                }
            }
            if (!noFunds && !throttled) {
                Assets.Crypto c = Assets.CRYPTOS[coin];
                AssetState s = st.market.cryptos[coin];
                double usd = Mining.usdPerSec(tier, overclock, c, s.difficulty) * Config.data.miningMultiplier;
                Position p = pd.crypto.computeIfAbsent(c.symbol(), k -> new Position());
                p.qty += usd / s.price;
                p.cost += usd;
                pd.add("mined_usd", usd);
                lastUsd = usd;
                sessionUsd += usd;
                totalUsd += usd;
                running = true;
            }
            st.markDirty();
        }
        if (running != state.get(GpuBlock.LIT)) {
            w.setBlockState(pos, state.with(GpuBlock.LIT, running), Block.NOTIFY_ALL);
        }
        if (running && ++hum >= 4) {
            hum = 0;
            w.playSound(null, pos, ModSounds.GPU_HUM, SoundCategory.BLOCKS, 0.35f, 0.9f + tier * 0.1f);
        }
        if (throttled) {
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 2, 0.2, 0.05, 0.2, 0.01);
        }
        markDirty();
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (owner != null) nbt.putUuid("Owner", owner);
        nbt.putInt("Coin", coin);
        nbt.putBoolean("Power", power);
        nbt.putInt("OC", overclock);
        nbt.putFloat("Temp", temp);
        nbt.putBoolean("Throttled", throttled);
        nbt.putDouble("Session", sessionUsd);
        nbt.putDouble("Total", totalUsd);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        coin = Math.max(0, Math.min(Assets.CRYPTOS.length - 1, nbt.getInt("Coin")));
        if (!Assets.CRYPTOS[coin].minable()) coin = Assets.firstMinable();
        power = !nbt.contains("Power") || nbt.getBoolean("Power");
        overclock = Math.max(0, Math.min(2, nbt.getInt("OC")));
        temp = nbt.contains("Temp") ? nbt.getFloat("Temp") : 30f;
        throttled = nbt.getBoolean("Throttled");
        sessionUsd = nbt.getDouble("Session");
        totalUsd = nbt.getDouble("Total");
    }
}
