package com.capitalcraft;

import com.capitalcraft.data.CapitalState;
import com.capitalcraft.data.PlayerData;
import com.capitalcraft.econ.Trading;
import com.capitalcraft.econ.ValueTable;
import com.capitalcraft.job.Jobs;
import com.capitalcraft.market.MarketEngine;
import com.capitalcraft.net.Net;
import com.capitalcraft.registry.ModBlockEntities;
import com.capitalcraft.registry.ModBlocks;
import com.capitalcraft.registry.ModItems;
import com.capitalcraft.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class CapitalCraft implements ModInitializer {
    public static final String MOD_ID = "capitalcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Random RNG = new Random();
    private static long tickCounter = 0;

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Config.load();
        ModSounds.init();
        ModBlocks.init();
        ModBlockEntities.init();
        ModItems.init();
        Net.registerServer();
        Jobs.registerReload();
        Commands.register();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player, server));
        ServerTickEvents.END_SERVER_TICK.register(CapitalCraft::onTick);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            ValueTable.invalidate();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) Net.sendValues(p);
        });
        LOGGER.info("CapitalCraft loaded. El capital nunca duerme.");
    }

    private static void onJoin(ServerPlayerEntity p, MinecraftServer server) {
        CapitalState st = CapitalState.get(server);
        PlayerData d = st.player(p.getUuid());
        if (!d.welcomed) {
            d.welcomed = true;
            d.balance += Config.data.startingCash;
            p.getInventory().offerOrDrop(new ItemStack(ModItems.CASINO_CHIP));
            p.getInventory().offerOrDrop(new ItemStack(ModItems.STOCK_TERMINAL));
            p.sendMessage(Fmt.t("welcome.1"), false);
            p.sendMessage(Fmt.t("welcome.2"), false);
            p.sendMessage(Fmt.t("welcome.3"), false);
            st.markDirty();
        }
        Net.sendValues(p);
        Net.sendWallet(p, d);
        Net.sendJob(p, d);
    }

    private static void onTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % 20 != 0) return;
        CapitalState st = CapitalState.get(server);
        MarketEngine.step(st.market, RNG);
        if (tickCounter % 200 == 0) MarketEngine.sampleHistory(st.market);
        long day = Math.floorDiv(server.getOverworld().getTimeOfDay(), 24000L);
        if (day != st.market.lastDay) {
            boolean first = st.market.lastDay == -1;
            st.market.lastDay = day;
            MarketEngine.newDay(st.market);
            if (!first) Trading.payDividends(server, st);
        }
        Jobs.tick(server);
        st.markDirty();
    }
}
