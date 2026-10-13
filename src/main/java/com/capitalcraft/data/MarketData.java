package com.capitalcraft.data;

import com.capitalcraft.market.Assets;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayDeque;

public class MarketData {
    public record News(int template, int kind, int idx, long timeMs) {}

    public final AssetState[] stocks = new AssetState[Assets.STOCKS.length];
    public final AssetState[] cryptos = new AssetState[Assets.CRYPTOS.length];
    public double sentiment;
    public long lastDay = -1;
    public final ArrayDeque<News> news = new ArrayDeque<>();

    public void addNews(News n) {
        news.addFirst(n);
        while (news.size() > 10) news.removeLast();
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putDouble("sentiment", sentiment);
        n.putLong("lastDay", lastDay);
        NbtList s = new NbtList();
        for (AssetState a : stocks) s.add(a.toNbt());
        n.put("stocks", s);
        NbtList c = new NbtList();
        for (AssetState a : cryptos) c.add(a.toNbt());
        n.put("cryptos", c);
        NbtList nw = new NbtList();
        for (News x : news) {
            NbtCompound e = new NbtCompound();
            e.putInt("t", x.template());
            e.putInt("k", x.kind());
            e.putInt("i", x.idx());
            e.putLong("ms", x.timeMs());
            nw.add(e);
        }
        n.put("news", nw);
        return n;
    }

    public void readNbt(NbtCompound n) {
        sentiment = n.getDouble("sentiment");
        lastDay = n.contains("lastDay") ? n.getLong("lastDay") : -1;
        NbtList s = n.getList("stocks", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < stocks.length && i < s.size(); i++) stocks[i] = AssetState.fromNbt(s.getCompound(i), Assets.STOCKS[i].price());
        NbtList c = n.getList("cryptos", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < cryptos.length && i < c.size(); i++) cryptos[i] = AssetState.fromNbt(c.getCompound(i), Assets.CRYPTOS[i].price());
        NbtList nw = n.getList("news", NbtElement.COMPOUND_TYPE);
        news.clear();
        for (int i = 0; i < nw.size(); i++) {
            NbtCompound e = nw.getCompound(i);
            news.addLast(new News(e.getInt("t"), e.getInt("k"), e.getInt("i"), e.getLong("ms")));
        }
    }
}
