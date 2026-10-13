package com.capitalcraft.client;

import com.capitalcraft.client.ui.CasinoScreen;
import com.capitalcraft.client.ui.MiningScreen;
import com.capitalcraft.client.ui.TradingScreen;
import com.capitalcraft.net.Net;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;

public class CapitalCraftClient implements ClientModInitializer {
    private static KeyBinding hudKey;

    @Override
    public void onInitializeClient() {
        hudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.capitalcraft.hud",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.capitalcraft"));
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            while (hudKey.wasPressed()) ClientData.hudVisible = !ClientData.hudVisible;
        });
        HudRenderCallback.EVENT.register(Hud::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientData.clear());

        ClientPlayNetworking.registerGlobalReceiver(Net.WALLET, (client, handler, buf, sender) -> {
            long bal = buf.readLong();
            int bets = buf.readVarInt();
            int won = buf.readVarInt();
            long wag = buf.readLong();
            long wonV = buf.readLong();
            client.execute(() -> {
                ClientData.balance = bal;
                ClientData.bets = bets;
                ClientData.betsWon = won;
                ClientData.wagered = wag;
                ClientData.won = wonV;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Net.VALUES, (client, handler, buf, sender) -> {
            float he = buf.readFloat();
            float mn = buf.readFloat();
            float mx = buf.readFloat();
            int n = buf.readVarInt();
            ArrayList<Object[]> rows = new ArrayList<>(n);
            for (int i = 0; i < n; i++) rows.add(new Object[]{buf.readIdentifier(), buf.readVarLong(), buf.readBoolean()});
            client.execute(() -> {
                ClientData.houseEdge = he;
                ClientData.minChance = mn;
                ClientData.maxChance = mx;
                ClientData.values.clear();
                ClientData.catalog.clear();
                for (Object[] r : rows) ClientData.addValue((net.minecraft.util.Identifier) r[0], (Long) r[1], (Boolean) r[2]);
                ClientData.finishValues();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Net.OPEN, (client, handler, buf, sender) -> {
            int kind = buf.readByte();
            BlockPos pos = buf.readBlockPos();
            client.execute(() -> {
                if (ClientData.catalog.isEmpty()) ClientPlayNetworking.send(Net.VALUES_REQ, PacketByteBufs.create());
                switch (kind) {
                    case Net.OPEN_CASINO -> client.setScreen(new CasinoScreen());
                    case Net.OPEN_TERMINAL -> client.setScreen(new TradingScreen());
                    case Net.OPEN_GPU -> client.setScreen(new MiningScreen(pos));
                    default -> {
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Net.CASINO_RESULT, (client, handler, buf, sender) -> {
            boolean ok = buf.readBoolean();
            Text msg = buf.readText();
            boolean win = buf.readBoolean();
            float roll = buf.readFloat();
            float chance = buf.readFloat();
            ItemStack reward = buf.readItemStack();
            long coins = buf.readLong();
            client.execute(() -> {
                if (client.currentScreen instanceof CasinoScreen cs) cs.onResult(ok, msg, win, roll, chance, reward, coins);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Net.TRADE_RESULT, (client, handler, buf, sender) -> {
            boolean ok = buf.readBoolean();
            Text msg = buf.readText();
            client.execute(() -> {
                if (client.currentScreen instanceof TradingScreen ts) ts.onTradeResult(ok, msg);
                else if (client.currentScreen instanceof MiningScreen ms) ms.onTradeResult(ok, msg);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Net.MARKET, (client, handler, buf, sender) -> {
            ClientData.Market m = new ClientData.Market();
            m.balance = buf.readLong();
            m.sentiment = buf.readFloat();
            m.realized = buf.readDouble();
            m.dividends = buf.readDouble();
            int ns = buf.readVarInt();
            m.sPrice = new double[ns];
            m.sOpen = new double[ns];
            m.sQty = new double[ns];
            m.sCost = new double[ns];
            for (int i = 0; i < ns; i++) {
                m.sPrice[i] = buf.readDouble();
                m.sOpen[i] = buf.readDouble();
                m.sQty[i] = buf.readDouble();
                m.sCost[i] = buf.readDouble();
            }
            int nc = buf.readVarInt();
            m.cPrice = new double[nc];
            m.cOpen = new double[nc];
            m.cQty = new double[nc];
            m.cCost = new double[nc];
            m.cDiff = new float[nc];
            for (int i = 0; i < nc; i++) {
                m.cPrice[i] = buf.readDouble();
                m.cOpen[i] = buf.readDouble();
                m.cQty[i] = buf.readDouble();
                m.cCost[i] = buf.readDouble();
                m.cDiff[i] = buf.readFloat();
            }
            int nn = buf.readVarInt();
            for (int i = 0; i < nn; i++) {
                ClientData.News n = new ClientData.News();
                n.template = buf.readVarInt();
                n.kind = buf.readByte();
                n.idx = buf.readByte();
                n.ago = buf.readVarInt();
                m.news.add(n);
            }
            m.selKind = buf.readByte();
            m.selIdx = buf.readVarInt();
            m.selHigh = buf.readDouble();
            m.selLow = buf.readDouble();
            int h = buf.readVarInt();
            m.hist = new float[h];
            for (int i = 0; i < h; i++) m.hist[i] = buf.readFloat();
            client.execute(() -> {
                ClientData.market = m;
                ClientData.balance = m.balance;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Net.GPU_STATE, (client, handler, buf, sender) -> {
            ClientData.Gpu g = new ClientData.Gpu();
            g.pos = buf.readBlockPos();
            g.tier = buf.readVarInt();
            g.coin = buf.readVarInt();
            g.power = buf.readBoolean();
            g.overclock = buf.readVarInt();
            g.temp = buf.readFloat();
            g.throttled = buf.readBoolean();
            g.noFunds = buf.readBoolean();
            g.coolant = buf.readBoolean();
            g.usdPerSec = buf.readDouble();
            g.powerCost = buf.readDouble();
            g.session = buf.readDouble();
            g.total = buf.readDouble();
            g.balance = buf.readLong();
            int n = buf.readVarInt();
            g.price = new double[n];
            g.diff = new float[n];
            g.holdings = new double[n];
            g.est = new double[n];
            for (int i = 0; i < n; i++) {
                g.price[i] = buf.readDouble();
                g.diff[i] = buf.readFloat();
                g.holdings[i] = buf.readDouble();
                g.est[i] = buf.readDouble();
            }
            client.execute(() -> {
                ClientData.gpu = g;
                ClientData.balance = g.balance;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Net.JOB, (client, handler, buf, sender) -> {
            boolean has = buf.readBoolean();
            if (!has) {
                client.execute(() -> ClientData.job = null);
                return;
            }
            ClientData.JobInfo j = new ClientData.JobInfo();
            j.id = buf.readString();
            j.tier = buf.readVarInt();
            j.wage = buf.readLong();
            j.streak = buf.readVarInt();
            j.secondsLeft = buf.readVarInt();
            j.recvMs = System.currentTimeMillis();
            int n = buf.readVarInt();
            j.progress = new long[n];
            j.target = new long[n];
            for (int i = 0; i < n; i++) {
                j.progress[i] = buf.readLong();
                j.target[i] = buf.readLong();
            }
            client.execute(() -> ClientData.job = j);
        });
    }

    public static void requestValues() {
        PacketByteBuf b = PacketByteBufs.create();
        ClientPlayNetworking.send(Net.VALUES_REQ, b);
    }

    public static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }
}
