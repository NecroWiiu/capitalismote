package com.capitalcraft.job;

import com.capitalcraft.CapitalCraft;
import com.capitalcraft.Config;
import com.capitalcraft.Fmt;
import com.capitalcraft.block.GpuBlock;
import com.capitalcraft.data.CapitalState;
import com.capitalcraft.data.PlayerData;
import com.capitalcraft.econ.ValueTable;
import com.capitalcraft.item.MenuItem;
import com.capitalcraft.net.Net;
import com.capitalcraft.registry.ModSounds;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BedItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class Jobs {
    private Jobs() {}

    public static volatile Map<String, JobDef> DEFS = new LinkedHashMap<>();
    private static final Random RNG = new Random();
    private static final Set<UUID> WARNED_60 = new HashSet<>();
    private static final Set<UUID> WARNED_10 = new HashSet<>();

    public static void registerReload() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return CapitalCraft.id("jobs");
            }

            @Override
            public void reload(ResourceManager manager) {
                load(manager);
            }
        });
    }

    public static void load(ResourceManager rm) {
        Map<String, JobDef> fresh = new LinkedHashMap<>();
        Map<Identifier, Resource> found = rm.findResources("capitalcraft_jobs", id -> id.getPath().endsWith(".json"));
        List<Identifier> ids = new ArrayList<>(found.keySet());
        ids.sort(Comparator.comparing(Identifier::toString));
        for (Identifier rid : ids) {
            try (BufferedReader br = found.get(rid).getReader()) {
                JsonObject o = JsonParser.parseReader(br).getAsJsonObject();
                String path = rid.getPath();
                String id = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
                int tier = o.has("tier") ? o.get("tier").getAsInt() : 1;
                long wage = o.get("wage").getAsLong();
                boolean gpu = o.has("requires_gpu") && o.get("requires_gpu").getAsBoolean();
                List<JobDef.KitEntry> kit = new ArrayList<>();
                if (o.has("kit")) {
                    for (JsonElement e : o.getAsJsonArray("kit")) {
                        JsonObject k = e.getAsJsonObject();
                        kit.add(new JobDef.KitEntry(k.get("item").getAsString(), k.has("count") ? k.get("count").getAsInt() : 1));
                    }
                }
                List<Objective> objs = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("objectives")) {
                    JsonObject ob = e.getAsJsonObject();
                    List<String> targets = new ArrayList<>();
                    JsonArray ta = ob.getAsJsonArray("targets");
                    for (JsonElement t : ta) targets.add(t.getAsString());
                    objs.add(new Objective(Objective.Type.valueOf(ob.get("type").getAsString().toUpperCase(Locale.ROOT)),
                            targets, ob.get("count").getAsLong()));
                }
                if (!objs.isEmpty()) fresh.put(id, new JobDef(id, tier, wage, gpu, kit, objs));
            } catch (Exception ex) {
                CapitalCraft.LOGGER.warn("[CapitalCraft] Bad job file {}: {}", rid, ex.toString());
            }
        }
        DEFS = fresh;
        CapitalCraft.LOGGER.info("[CapitalCraft] Loaded {} jobs", fresh.size());
    }

    public static boolean isKit(ItemStack s) {
        return s.hasNbt() && s.getNbt().getBoolean("CapitalKit");
    }

    private static void sound(ServerPlayerEntity p, SoundEvent ev, float pitch) {
        p.getServerWorld().playSound(null, p.getBlockPos(), ev, SoundCategory.PLAYERS, 0.8f, pitch);
    }

    public static long[] progress(ServerPlayerEntity p, PlayerData d, JobDef def) {
        long[] out = new long[def.objectives.size()];
        for (int i = 0; i < out.length; i++) {
            long base = i < d.baseline.length ? d.baseline[i] : 0;
            out[i] = Math.max(0, def.objectives.get(i).read(p, d) - base);
        }
        return out;
    }

    public static void tick(MinecraftServer server) {
        if (!Config.data.jobsEnabled || DEFS.isEmpty()) return;
        ServerWorld ow = server.getOverworld();
        long wt = ow.getTimeOfDay();
        long day = Math.floorDiv(wt, 24000L);
        int tod = (int) Math.floorMod(wt, 24000L);
        CapitalState st = CapitalState.get(server);
        for (ServerPlayerEntity p : new ArrayList<>(server.getPlayerManager().getPlayerList())) {
            if (p.isSpectator()) continue;
            PlayerData d = st.player(p.getUuid());
            boolean has = !d.jobId.isEmpty();
            if (d.jobDay < day) {
                if (has) settle(p, d);
                d.jobDay = (int) day;
                st.markDirty();
                if (!p.isCreative() && tod < Config.data.shiftEndTick - 1200) {
                    JobDef def = pick(d);
                    if (def != null) assign(p, d, def);
                }
            } else if (has && tod >= Config.data.shiftEndTick) {
                settle(p, d);
            } else if (has) {
                int left = Config.data.shiftEndTick - tod;
                if (left <= 1200 && WARNED_60.add(p.getUuid())) {
                    p.sendMessage(Fmt.t("job.warn60").formatted(Formatting.GOLD), true);
                    sound(p, ModSounds.ALERT, 1.0f);
                }
                if (left <= 200 && WARNED_10.add(p.getUuid())) {
                    p.sendMessage(Fmt.t("job.warn10").formatted(Formatting.RED, Formatting.BOLD), true);
                    sound(p, ModSounds.ALERT, 1.4f);
                }
            }
            if (!d.jobId.isEmpty()) Net.sendJob(p, d);
        }
    }

    private static JobDef pick(PlayerData d) {
        double r = RNG.nextDouble();
        int tier = r < 0.5 ? 1 : (r < 0.85 ? 2 : 3);
        List<JobDef> pool = new ArrayList<>();
        List<JobDef> any = new ArrayList<>();
        for (JobDef def : DEFS.values()) {
            if (def.requiresGpu && d.counter("gpu_placed") <= 0) continue;
            if (def.id.equals(d.lastJobId)) continue;
            any.add(def);
            if (def.tier == tier) pool.add(def);
        }
        if (pool.isEmpty()) pool = any;
        if (pool.isEmpty()) return null;
        return pool.get(RNG.nextInt(pool.size()));
    }

    public static void assign(ServerPlayerEntity p, PlayerData d, JobDef def) {
        d.jobId = def.id;
        d.lastJobId = def.id;
        d.baseline = new long[def.objectives.size()];
        for (int i = 0; i < d.baseline.length; i++) d.baseline[i] = def.objectives.get(i).read(p, d);
        WARNED_60.remove(p.getUuid());
        WARNED_10.remove(p.getUuid());

        for (JobDef.KitEntry k : def.kit) {
            Item item = Registries.ITEM.get(new Identifier("minecraft", k.item()));
            if (item == Items.AIR) continue;
            ItemStack s = new ItemStack(item, k.count());
            NbtCompound nbt = s.getOrCreateNbt();
            nbt.putBoolean("CapitalKit", true);
            if (s.isDamageable()) nbt.putBoolean("Unbreakable", true);
            NbtCompound display = s.getOrCreateSubNbt("display");
            NbtList lore = new NbtList();
            lore.add(NbtString.of(Text.Serializer.toJson(Fmt.t("kit.lore").formatted(Formatting.GRAY, Formatting.ITALIC))));
            display.put("Lore", lore);
            p.getInventory().offerOrDrop(s);
        }
        if (Config.data.dailyRation) p.getInventory().offerOrDrop(new ItemStack(Items.BREAD, 6));

        p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Fmt.t("job." + def.id).formatted(Formatting.YELLOW)));
        p.networkHandler.sendPacket(new TitleS2CPacket(Fmt.t("job.new_shift").formatted(Formatting.GOLD, Formatting.BOLD)));
        p.sendMessage(Fmt.t("job.assigned", Fmt.t("job." + def.id), Fmt.money(def.wage)).formatted(Formatting.YELLOW), false);
        sound(p, ModSounds.SHIFT_START, 1.0f);
        Net.sendJob(p, d);
    }

    public static void settle(ServerPlayerEntity p, PlayerData d) {
        JobDef def = DEFS.get(d.jobId);
        MinecraftServer server = p.getServer();
        ValueTable.ensure(server);
        double ratio = 0;
        if (def != null) {
            long[] prog = progress(p, d, def);
            for (int i = 0; i < prog.length; i++) ratio += Math.min(1.0, prog[i] / (double) def.objectives.get(i).count);
            ratio /= prog.length;
        }
        long wage = def == null ? 0 : Math.round(def.wage * ratio);
        long bonus = 0;
        boolean done = def != null && ratio >= 0.999;
        if (done) {
            d.streak++;
            d.jobsDone++;
            bonus = Math.round(def.wage * (0.25 + Math.min(d.streak - 1, 10) * 0.05));
        } else {
            d.streak = 0;
        }
        long[] conf = confiscate(p);
        long surplus = Math.round(conf[0] * Config.data.surplusPayoutRate);
        long total = wage + bonus + surplus;
        d.balance += total;
        d.add("earned", total);
        String jobKey = d.jobId;
        d.jobId = "";
        d.baseline = new long[0];
        WARNED_60.remove(p.getUuid());
        WARNED_10.remove(p.getUuid());

        p.sendMessage(Fmt.t("payroll.header").formatted(Formatting.GOLD, Formatting.BOLD), false);
        p.sendMessage(Fmt.t("payroll.job", Fmt.t("job." + jobKey), Math.round(ratio * 100)).formatted(Formatting.YELLOW), false);
        p.sendMessage(Fmt.t("payroll.wage", Fmt.money(wage)).formatted(Formatting.GREEN), false);
        if (bonus > 0) p.sendMessage(Fmt.t("payroll.bonus", Fmt.money(bonus), d.streak).formatted(Formatting.GREEN), false);
        p.sendMessage(Fmt.t("payroll.confiscated", Fmt.money(conf[1]), Fmt.money(conf[0] - surplus)).formatted(Formatting.RED), false);
        if (surplus > 0) p.sendMessage(Fmt.t("payroll.surplus", Fmt.money(surplus), Math.round(Config.data.surplusPayoutRate * 100)).formatted(Formatting.GRAY), false);
        p.sendMessage(Fmt.t("payroll.total", Fmt.money(total)).formatted(Formatting.GOLD, Formatting.BOLD), false);
        p.sendMessage(Fmt.t("payroll.tip").formatted(Formatting.DARK_GRAY, Formatting.ITALIC), false);
        sound(p, ModSounds.SHIFT_END, 1.0f);
        p.getServerWorld().playSound(null, p.getBlockPos(), ModSounds.CASH, SoundCategory.PLAYERS, 0.8f, 1.0f);
        Net.sendWallet(p, d);
        Net.sendJob(p, d);
        CapitalState.get(server).markDirty();
    }

    /** Takes everything but the bare necessities. Returns {value, itemCount}. */
    private static long[] confiscate(ServerPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        long value = 0, count = 0;
        int food = 10, torches = 8, beds = 1;
        boolean enabled = Config.data.confiscationEnabled;
        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack s = inv.main.get(i);
            if (s.isEmpty()) continue;
            if (isKit(s)) {
                inv.main.set(i, ItemStack.EMPTY);
                continue;
            }
            if (!enabled) continue;
            Item it = s.getItem();
            if (it instanceof MenuItem) continue;
            if (it instanceof BlockItem bi && (bi.getBlock() instanceof GpuBlock || bi.getBlock() instanceof ShulkerBoxBlock)) continue;
            int keep = 0;
            if (it.isFood() && food > 0) {
                keep = Math.min(food, s.getCount());
                food -= keep;
            } else if (it == Items.TORCH && torches > 0) {
                keep = Math.min(torches, s.getCount());
                torches -= keep;
            } else if (it instanceof BedItem && beds > 0) {
                keep = Math.min(beds, s.getCount());
                beds -= keep;
            }
            int take = s.getCount() - keep;
            if (take > 0) {
                value += ValueTable.unitValue(s, ValueTable::baseValue) * take;
                count += take;
                s.decrement(take);
            }
        }
        return new long[]{value, count};
    }
}
