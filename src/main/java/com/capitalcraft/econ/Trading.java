package com.capitalcraft.econ;

import com.capitalcraft.Config;
import com.capitalcraft.Fmt;
import com.capitalcraft.data.AssetState;
import com.capitalcraft.data.CapitalState;
import com.capitalcraft.data.PlayerData;
import com.capitalcraft.data.Position;
import com.capitalcraft.market.Assets;
import com.capitalcraft.net.Net;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;

public final class Trading {
    private Trading() {}

    public record Result(boolean ok, Text message) {}

    private static Result fail(String key) {
        return new Result(false, Fmt.t(key));
    }

    /** kind 0 = stock, 1 = crypto. Amount is expressed in coins. */
    public static Result trade(ServerPlayerEntity p, CapitalState st, int kind, int idx, boolean buy, long amount, boolean all) {
        PlayerData pd = st.player(p.getUuid());
        boolean stock = kind == 0;
        AssetState[] arr = stock ? st.market.stocks : st.market.cryptos;
        if (idx < 0 || idx >= arr.length) return fail("trade.err.invalid");
        String sym = stock ? Assets.STOCKS[idx].symbol() : Assets.CRYPTOS[idx].symbol();
        Map<String, Position> book = stock ? pd.stocks : pd.crypto;
        double price = arr[idx].price;
        double fee = Config.data.tradeFee;
        Position pos = book.get(sym);

        if (buy) {
            long spend = all ? pd.balance : amount;
            if (spend < 1) return fail("trade.err.small");
            if (spend > pd.balance) return fail("trade.err.funds");
            if (stock) {
                long shares = (long) Math.floor(spend / (price * (1 + fee)));
                long cost = (long) Math.ceil(shares * price * (1 + fee));
                while (shares > 0 && cost > pd.balance) {
                    shares--;
                    cost = (long) Math.ceil(shares * price * (1 + fee));
                }
                if (shares < 1) return fail("trade.err.small");
                pd.balance -= cost;
                if (pos == null) pos = book.computeIfAbsent(sym, k -> new Position());
                pos.qty += shares;
                pos.cost += cost;
                pd.add("trades", 1);
                pd.add("volume", cost);
                st.markDirty();
                return new Result(true, Fmt.t("trade.bought", Fmt.qty(shares), sym, Fmt.money(cost)));
            }
            double q = spend * (1 - fee) / price;
            pd.balance -= spend;
            if (pos == null) pos = book.computeIfAbsent(sym, k -> new Position());
            pos.qty += q;
            pos.cost += spend;
            pd.add("trades", 1);
            pd.add("volume", spend);
            st.markDirty();
            return new Result(true, Fmt.t("trade.bought", Fmt.qty(q), sym, Fmt.money(spend)));
        }

        if (pos == null || pos.qty <= 1e-9) return fail("trade.err.nopos");
        double sellQty;
        if (all) {
            sellQty = pos.qty;
        } else {
            sellQty = stock ? Math.ceil(amount / price) : amount / price;
            sellQty = Math.min(sellQty, pos.qty);
        }
        if (stock) sellQty = Math.floor(sellQty + 1e-9);
        double gross = sellQty * price;
        long proceeds = (long) Math.floor(gross * (1 - fee));
        if (sellQty <= 0 || proceeds < 1) return fail("trade.err.small");
        double costPortion = pos.cost * (sellQty / pos.qty);
        pos.qty -= sellQty;
        pos.cost -= costPortion;
        if (pos.qty < 1e-9) book.remove(sym);
        pd.balance += proceeds;
        pd.add("trades", 1);
        pd.add("volume", gross);
        pd.add("realized", proceeds - costPortion);
        st.markDirty();
        return new Result(true, Fmt.t("trade.sold", Fmt.qty(sellQty), sym, Fmt.money(proceeds)));
    }

    public static void payDividends(MinecraftServer server, CapitalState st) {
        for (Map.Entry<UUID, PlayerData> e : st.players.entrySet()) {
            PlayerData pd = e.getValue();
            double total = 0;
            for (int i = 0; i < Assets.STOCKS.length; i++) {
                Position pos = pd.stocks.get(Assets.STOCKS[i].symbol());
                if (pos != null && pos.qty > 0) total += pos.qty * st.market.stocks[i].price * Assets.STOCKS[i].yield();
            }
            long pay = (long) Math.floor(total);
            if (pay > 0) {
                pd.balance += pay;
                pd.add("dividends", pay);
                ServerPlayerEntity sp = server.getPlayerManager().getPlayer(e.getKey());
                if (sp != null) {
                    sp.sendMessage(Fmt.t("dividends", Fmt.money(pay)), false);
                    Net.sendWallet(sp, pd);
                }
            }
        }
        st.markDirty();
    }
}
