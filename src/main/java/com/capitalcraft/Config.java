package com.capitalcraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    private Config() {}

    public static class Data {
        public double houseEdge = 0.10;
        public double minChance = 0.005;
        public double maxChance = 0.90;
        public long startingCash = 1500;
        public boolean jobsEnabled = true;
        public int shiftEndTick = 12000;
        public boolean confiscationEnabled = true;
        public double surplusPayoutRate = 0.15;
        public boolean dailyRation = true;
        public double tradeFee = 0.005;
        public double miningMultiplier = 1.0;
        public double electricityMultiplier = 1.0;
    }

    public static Data data = new Data();

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("capitalcraft.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Data d = gson.fromJson(r, Data.class);
                if (d != null) data = d;
            } catch (Exception e) {
                CapitalCraft.LOGGER.warn("[CapitalCraft] Could not read capitalcraft.json, using defaults", e);
            }
        }
        data.houseEdge = Math.max(0.0, Math.min(0.5, data.houseEdge));
        data.maxChance = Math.max(0.05, Math.min(0.99, data.maxChance));
        data.minChance = Math.max(0.0001, Math.min(data.maxChance, data.minChance));
        data.shiftEndTick = Math.max(2000, Math.min(23000, data.shiftEndTick));
        data.tradeFee = Math.max(0.0, Math.min(0.2, data.tradeFee));
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            gson.toJson(data, w);
        } catch (IOException e) {
            CapitalCraft.LOGGER.warn("[CapitalCraft] Could not write capitalcraft.json", e);
        }
    }
}
