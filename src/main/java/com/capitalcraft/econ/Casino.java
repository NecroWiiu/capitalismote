package com.capitalcraft.econ;

import com.capitalcraft.Config;
import com.capitalcraft.Fmt;
import com.capitalcraft.data.CapitalState;
import com.capitalcraft.data.PlayerData;
import com.capitalcraft.item.MenuItem;
import com.capitalcraft.job.Jobs;
import com.capitalcraft.net.Net;
import com.capitalcraft.registry.ModItems;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Random;

public final class Casino {
    private Casino() {}

    private static final Random RNG = new Random();

    private static void fail(ServerPlayerEntity p, String key) {
        fail(p, Fmt.t(key));
    }

    private static void fail(ServerPlayerEntity p, Text msg) {
        Net.sendCasinoResult(p, false, msg, false, 0f, 0f, ItemStack.EMPTY, 0L);
    }

    public static void play(ServerPlayerEntity p, int stakeKind, int slot, int count, long coins,
                            int targetKind, Identifier targetItem, long targetCoins) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        ValueTable.ensure(server);
        CapitalState st = CapitalState.get(server);
        PlayerData pd = st.player(p.getUuid());

        if (!Net.hasOpener(p, ModItems.CASINO_CHIP)) {
            fail(p, "casino.err.need_chip");
            return;
        }

        long stakeValue;
        ItemStack stakeStack = ItemStack.EMPTY;
        int take = 0;
        if (stakeKind == 0) {
            PlayerInventory inv = p.getInventory();
            if (slot < 0 || slot >= inv.main.size()) {
                fail(p, "casino.err.slot");
                return;
            }
            ItemStack s = inv.main.get(slot);
            if (s.isEmpty()) {
                fail(p, "casino.err.slot");
                return;
            }
            if (Jobs.isKit(s) || s.getItem() instanceof MenuItem) {
                fail(p, "casino.err.protected");
                return;
            }
            take = Math.max(1, Math.min(count, s.getCount()));
            stakeValue = ValueTable.unitValue(s, ValueTable::baseValue) * take;
            stakeStack = s;
        } else {
            if (coins <= 0 || coins > pd.balance) {
                fail(p, "casino.err.funds");
                return;
            }
            stakeValue = coins;
        }

        long targetValue;
        Item targetIt = null;
        if (targetKind == 0) {
            Item it = Registries.ITEM.get(targetItem);
            if (it == Items.AIR || !ValueTable.isTargetable(it)) {
                fail(p, "casino.err.target");
                return;
            }
            targetIt = it;
            targetValue = ValueTable.baseValue(it);
        } else {
            if (targetCoins <= 0 || targetCoins > 1_000_000_000_000L) {
                fail(p, "casino.err.target");
                return;
            }
            targetValue = targetCoins;
        }

        double chance = (double) stakeValue / (double) targetValue * (1.0 - Config.data.houseEdge);
        chance = Math.min(chance, Config.data.maxChance);
        if (chance < Config.data.minChance) {
            fail(p, "casino.err.chance");
            return;
        }

        if (stakeKind == 0) stakeStack.decrement(take);
        else pd.balance -= coins;

        double roll = RNG.nextDouble();
        boolean win = roll < chance;
        pd.bets++;
        pd.wagered += stakeValue;
        pd.add("bets", 1);
        pd.add("wagered", stakeValue);

        ItemStack reward = ItemStack.EMPTY;
        long coinReward = 0;
        if (win) {
            pd.betsWon++;
            pd.won += targetValue;
            pd.add("bets_won", 1);
            if (targetIt != null) {
                reward = new ItemStack(targetIt);
                p.getInventory().offerOrDrop(reward.copy());
            } else {
                pd.balance += targetCoins;
                coinReward = targetCoins;
            }
        } else {
            pd.lost += stakeValue;
        }
        st.markDirty();
        Net.sendCasinoResult(p, true, Text.empty(), win, (float) roll, (float) chance, reward, coinReward);
        Net.sendWallet(p, pd);
    }
}
