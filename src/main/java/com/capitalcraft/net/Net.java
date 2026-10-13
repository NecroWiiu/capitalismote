package com.capitalcraft.net;

import com.capitalcraft.CapitalCraft;
import com.capitalcraft.Config;
import com.capitalcraft.Fmt;
import com.capitalcraft.block.GpuBlockEntity;
import com.capitalcraft.data.AssetState;
import com.capitalcraft.data.CapitalState;
import com.capitalcraft.data.MarketData;
import com.capitalcraft.data.PlayerData;
import com.capitalcraft.data.Position;
import com.capitalcraft.econ.Casino;
import com.capitalcraft.econ.Mining;
import com.capitalcraft.econ.Trading;
import com.capitalcraft.econ.ValueTable;
import com.capitalcraft.job.JobDef;
import com.capitalcraft.job.Jobs;
import com.capitalcraft.market.Assets;
import com.capitalcraft.registry.ModItems;
import com.capitalcraft.registry.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;

public final class Net {
    private Net() {}

    private static Identifier id(String s) {
        return CapitalCraft.id(s);
    }

    // server -> client
    public static final Identifier VALUES = id("values");
    public static final Identifier WALLET = id("wallet");
    public static final Identifier OPEN = id("open");
    public static final Identifier CASINO_RESULT = id("casino_result");
    public static final Identifier MARKET = id("market");
    public static final Identifier TRADE_RESULT = id("trade_result");
    public static final Identifier GPU_STATE = id("gpu_state");
    public static final Identifier JOB = id("job");
    // client -> server
    public static final Identifier VALUES_REQ = id("values_req");
    public static final Identifier CASINO_PLAY = id("casino_play");
    public static final Identifier MARKET_REQ = id("market_req");
    public static final Identifier TRADE = id("trade");
    public static final Identifier GPU_REQ = id("gpu_req");
    public static final Identifier GPU_ACT = id("gpu_act");

    public static final int OPEN_CASINO = 0, OPEN_TERMINAL = 1, OPEN_GPU = 2;

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(VALUES_REQ,
                (server, player, handler, buf, sender) -> server.execute(() -> sendValues(player)));

        ServerPlayNetworking.registerGlobalReceiver(CASINO_PLAY, (server, player, handler, buf, sender) -> {
            int stakeKind = buf.readByte();
            int slot = buf.readInt();
            int count = buf.readInt();
            long coins = buf.readLong();
            int targetKind = buf.readByte();
            Identifier item = buf.readIdentifier();
            long tcoins = buf.readLong();
            server.execute(() -> Casino.play(player, stakeKind, slot, count, coins, targetKind, item, tcoins));
        });

        ServerPlayNetworking.registerGlobalReceiver(MARKET_REQ, (server, player, handler, buf, sender) -> {
            int kind = buf.readByte();
            int idx = buf.readVarInt();
            server.execute(() -> sendMarket(player, kind, idx));
        });

        ServerPlayNetworking.registerGlobalReceiver(TRADE, (server, player, handler, buf, sender) -> {
            int kind = buf.readByte();
            int idx = buf.readVarInt();
            boolean buy = buf.readBoolean();
            long amount = buf.readLong();
            boolean all = buf.readBoolean();
            server.execute(() -> {
                if (!hasOpener(player, ModItems.STOCK_TERMINAL)) {
                    sendTradeResult(player, new Trading.Result(false, Fmt.t("trade.err.need_terminal")));
                    return;
                }
                CapitalState st = CapitalState.get(server);
                Trading.Result r = Trading.trade(player, st, kind, idx, buy, amount, all);
                sendTradeResult(player, r);
                sendWallet(player, st.player(player.getUuid()));
                sendMarket(player, kind, idx);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(GPU_REQ, (server, player, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                GpuBlockEntity be = gpuAt(player, pos);
                if (be != null) sendGpuState(player, be);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(GPU_ACT, (server, player, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            int action = buf.readByte();
            int arg = buf.readVarInt();
            server.execute(() -> gpuAct(player, pos, action, arg));
        });
    }

    public static boolean hasOpener(ServerPlayerEntity p, Item item) {
        PlayerInventory inv = p.getInventory();
        for (ItemStack s : inv.main) if (s.isOf(item)) return true;
        for (ItemStack s : inv.offHand) if (s.isOf(item)) return true;
        return false;
    }

    private static GpuBlockEntity gpuAt(ServerPlayerEntity p, BlockPos pos) {
        if (p.squaredDistanceTo(Vec3d.ofCenter(pos)) > 64) return null;
        if (!(p.getServerWorld().getBlockEntity(pos) instanceof GpuBlockEntity be)) return null;
        if (!be.isOwner(p) && !p.hasPermissionLevel(2)) return null;
        return be;
    }

    private static void gpuAct(ServerPlayerEntity p, BlockPos pos, int action, int arg) {
        GpuBlockEntity be = gpuAt(p, pos);
        if (be == null) return;
        MinecraftServer server = p.getServer();
        CapitalState st = CapitalState.get(server);
        switch (action) {
            case 0 -> be.power = !be.power;
            case 1 -> {
                if (arg >= 0 && arg < Assets.CRYPTOS.length && Assets.CRYPTOS[arg].minable()) be.coin = arg;
            }
            case 2 -> be.overclock = Math.max(0, Math.min(2, arg));
            case 3 -> sendTradeResult(p, Trading.trade(p, st, 1, arg, false, 0, true));
            case 4 -> {
                for (int i = 0; i < Assets.CRYPTOS.length; i++) {
                    Position pos2 = st.player(p.getUuid()).crypto.get(Assets.CRYPTOS[i].symbol());
                    if (pos2 != null && pos2.qty > 0) Trading.trade(p, st, 1, i, false, 0, true);
                }
                sendTradeResult(p, new Trading.Result(true, Fmt.t("trade.sold_all")));
            }
            default -> {
            }
        }
        be.markDirty();
        sendWallet(p, st.player(p.getUuid()));
        sendGpuState(p, be);
    }

    // ---------------------------------------------------------------- senders

    public static void sendOpen(ServerPlayerEntity p, int kind, BlockPos pos) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeByte(kind);
        b.writeBlockPos(pos);
        ServerPlayNetworking.send(p, OPEN, b);
    }

    public static void sendWallet(ServerPlayerEntity p) {
        sendWallet(p, CapitalState.get(p.getServer()).player(p.getUuid()));
    }

    public static void sendWallet(ServerPlayerEntity p, PlayerData d) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeLong(d.balance);
        b.writeVarInt(d.bets);
        b.writeVarInt(d.betsWon);
        b.writeLong(d.wagered);
        b.writeLong(d.won);
        ServerPlayNetworking.send(p, WALLET, b);
    }

    public static void sendValues(ServerPlayerEntity p) {
        ValueTable.ensure(p.getServer());
        PacketByteBuf b = PacketByteBufs.create();
        b.writeFloat((float) Config.data.houseEdge);
        b.writeFloat((float) Config.data.minChance);
        b.writeFloat((float) Config.data.maxChance);
        List<Map.Entry<Item, Long>> list = ValueTable.entries();
        b.writeVarInt(list.size());
        for (Map.Entry<Item, Long> e : list) {
            b.writeIdentifier(Registries.ITEM.getId(e.getKey()));
            b.writeVarLong(e.getValue());
            b.writeBoolean(ValueTable.isTargetable(e.getKey()));
        }
        ServerPlayNetworking.send(p, VALUES, b);
    }

    public static void sendCasinoResult(ServerPlayerEntity p, boolean ok, Text msg, boolean win, float roll,
                                        float chance, ItemStack reward, long coins) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeBoolean(ok);
        b.writeText(msg);
        b.writeBoolean(win);
        b.writeFloat(roll);
        b.writeFloat(chance);
        b.writeItemStack(reward);
        b.writeLong(coins);
        ServerPlayNetworking.send(p, CASINO_RESULT, b);
    }

    public static void sendTradeResult(ServerPlayerEntity p, Trading.Result r) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeBoolean(r.ok());
        b.writeText(r.message());
        ServerPlayNetworking.send(p, TRADE_RESULT, b);
    }

    public static void sendMarket(ServerPlayerEntity p, int selKind, int selIdx) {
        MinecraftServer server = p.getServer();
        CapitalState st = CapitalState.get(server);
        PlayerData pd = st.player(p.getUuid());
        MarketData m = st.market;
        PacketByteBuf b = PacketByteBufs.create();
        b.writeLong(pd.balance);
        b.writeFloat((float) m.sentiment);
        b.writeDouble(pd.counters.getOrDefault("realized", 0.0));
        b.writeDouble(pd.counters.getOrDefault("dividends", 0.0));
        b.writeVarInt(Assets.STOCKS.length);
        for (int i = 0; i < Assets.STOCKS.length; i++) {
            AssetState s = m.stocks[i];
            Position pos = pd.stocks.get(Assets.STOCKS[i].symbol());
            b.writeDouble(s.price);
            b.writeDouble(s.open);
            b.writeDouble(pos == null ? 0 : pos.qty);
            b.writeDouble(pos == null ? 0 : pos.cost);
        }
        b.writeVarInt(Assets.CRYPTOS.length);
        for (int i = 0; i < Assets.CRYPTOS.length; i++) {
            AssetState s = m.cryptos[i];
            Position pos = pd.crypto.get(Assets.CRYPTOS[i].symbol());
            b.writeDouble(s.price);
            b.writeDouble(s.open);
            b.writeDouble(pos == null ? 0 : pos.qty);
            b.writeDouble(pos == null ? 0 : pos.cost);
            b.writeFloat((float) s.difficulty);
        }
        long now = System.currentTimeMillis();
        int n = Math.min(6, m.news.size());
        b.writeVarInt(n);
        int c = 0;
        for (MarketData.News nw : m.news) {
            if (c++ >= n) break;
            b.writeVarInt(nw.template());
            b.writeByte(nw.kind());
            b.writeByte(nw.idx());
            b.writeVarInt((int) Math.min(100000, (now - nw.timeMs()) / 1000));
        }
        AssetState[] arr = selKind == 0 ? m.stocks : m.cryptos;
        int idx = Math.max(0, Math.min(arr.length - 1, selIdx));
        AssetState sel = arr[idx];
        b.writeByte(selKind);
        b.writeVarInt(idx);
        b.writeDouble(sel.high);
        b.writeDouble(sel.low);
        b.writeVarInt(sel.histLen);
        for (int i = 0; i < sel.histLen; i++) b.writeFloat(sel.hist[i]);
        ServerPlayNetworking.send(p, MARKET, b);
    }

    public static void sendGpuState(ServerPlayerEntity p, GpuBlockEntity be) {
        CapitalState st = CapitalState.get(p.getServer());
        PlayerData pd = st.player(p.getUuid());
        PacketByteBuf b = PacketByteBufs.create();
        int tier = be.tier();
        b.writeBlockPos(be.getPos());
        b.writeVarInt(tier);
        b.writeVarInt(be.coin);
        b.writeBoolean(be.power);
        b.writeVarInt(be.overclock);
        b.writeFloat(be.temp);
        b.writeBoolean(be.throttled);
        b.writeBoolean(be.noFunds);
        b.writeBoolean(be.coolant);
        b.writeDouble(be.lastUsd);
        b.writeDouble(be.lastPower);
        b.writeDouble(be.sessionUsd);
        b.writeDouble(be.totalUsd);
        b.writeLong(pd.balance);
        b.writeVarInt(Assets.CRYPTOS.length);
        for (int i = 0; i < Assets.CRYPTOS.length; i++) {
            Assets.Crypto c = Assets.CRYPTOS[i];
            AssetState s = st.market.cryptos[i];
            Position pos = pd.crypto.get(c.symbol());
            b.writeDouble(s.price);
            b.writeFloat((float) s.difficulty);
            b.writeDouble(pos == null ? 0 : pos.qty);
            b.writeDouble(Mining.usdPerSec(tier, be.overclock, c, s.difficulty) * Config.data.miningMultiplier);
        }
        ServerPlayNetworking.send(p, GPU_STATE, b);
    }

    public static void sendJob(ServerPlayerEntity p, PlayerData d) {
        PacketByteBuf b = PacketByteBufs.create();
        JobDef def = d.jobId.isEmpty() ? null : Jobs.DEFS.get(d.jobId);
        if (def == null) {
            b.writeBoolean(false);
            ServerPlayNetworking.send(p, JOB, b);
            return;
        }
        b.writeBoolean(true);
        b.writeString(def.id);
        b.writeVarInt(def.tier);
        b.writeLong(def.wage);
        b.writeVarInt(d.streak);
        int tod = (int) Math.floorMod(p.getServer().getOverworld().getTimeOfDay(), 24000L);
        b.writeVarInt(Math.max(0, (Config.data.shiftEndTick - tod) / 20));
        long[] prog = Jobs.progress(p, d, def);
        b.writeVarInt(prog.length);
        for (int i = 0; i < prog.length; i++) {
            b.writeLong(Math.min(prog[i], def.objectives.get(i).count));
            b.writeLong(def.objectives.get(i).count);
        }
        ServerPlayNetworking.send(p, JOB, b);
    }
}
