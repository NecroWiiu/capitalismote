package com.capitalcraft.econ;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.BannerPatternItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.item.SmithingTemplateItem;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

/** Gives every item in the game a price, derived from recipes + a table of raw materials. */
public final class ValueTable {
    private ValueTable() {}

    private static final double INF = Double.POSITIVE_INFINITY;
    private static final double CRAFT_MARKUP = 1.08;
    private static Map<Item, Long> values = new HashMap<>();
    private static Set<Item> targetable = new HashSet<>();
    private static boolean built = false;

    private static final Set<String> EXCLUDED = Set.of("air", "barrier", "bedrock", "command_block", "chain_command_block",
            "repeating_command_block", "command_block_minecart", "structure_block", "structure_void", "jigsaw", "light",
            "debug_stick", "knowledge_book", "spawner", "budding_amethyst", "reinforced_deepslate", "end_portal_frame",
            "petrified_oak_slab", "farmland", "dirt_path", "written_book", "filled_map", "potion", "splash_potion",
            "lingering_potion", "tipped_arrow", "suspicious_stew", "frogspawn", "enchanted_book");

    private static final Set<String> NETHERITE_GEAR = Set.of("helmet", "chestplate", "leggings", "boots", "sword",
            "pickaxe", "axe", "shovel", "hoe");

    private static final String SEEDS = """
            cobblestone 1
            cobbled_deepslate 2
            dirt 1
            sand 1
            red_sand 2
            gravel 1
            clay_ball 3
            netherrack 1
            end_stone 3
            obsidian 25
            crying_obsidian 70
            blackstone 2
            granite 1
            diorite 1
            andesite 1
            tuff 1
            calcite 4
            mud 2
            soul_sand 3
            soul_soil 3
            basalt 2
            ice 3
            snowball 1
            coal 8
            charcoal 6
            raw_copper 12
            copper_ingot 16
            raw_iron 34
            iron_ingot 40
            iron_nugget 5
            raw_gold 70
            gold_ingot 90
            gold_nugget 10
            redstone 10
            lapis_lazuli 18
            quartz 25
            amethyst_shard 20
            diamond 400
            emerald 140
            netherite_scrap 650
            ancient_debris 650
            glowstone_dust 12
            echo_shard 150
            prismarine_shard 12
            prismarine_crystals 15
            string 4
            feather 3
            leather 15
            bone 6
            rotten_flesh 2
            spider_eye 6
            gunpowder 12
            slime_ball 25
            ender_pearl 90
            blaze_rod 70
            ghast_tear 140
            nether_wart 12
            phantom_membrane 90
            nautilus_shell 150
            heart_of_the_sea 800
            scute 50
            shulker_shell 250
            dragon_breath 150
            nether_star 3500
            wither_skeleton_skull 1500
            skeleton_skull 400
            zombie_head 500
            creeper_head 500
            piglin_head 500
            dragon_head 1500
            player_head 500
            elytra 3500
            totem_of_undying 1200
            trident 1200
            dragon_egg 20000
            enchanted_golden_apple 2500
            name_tag 80
            saddle 120
            leather_horse_armor 80
            iron_horse_armor 150
            golden_horse_armor 250
            diamond_horse_armor 600
            wheat 5
            wheat_seeds 1
            beetroot_seeds 1
            melon_seeds 2
            pumpkin_seeds 2
            carrot 4
            potato 4
            poisonous_potato 3
            beetroot 4
            melon_slice 3
            pumpkin 8
            apple 8
            sweet_berries 3
            glow_berries 4
            sugar_cane 3
            cactus 3
            bamboo 2
            kelp 2
            cocoa_beans 6
            honey_bottle 25
            honeycomb 20
            egg 4
            beef 12
            porkchop 12
            chicken 10
            mutton 10
            rabbit 10
            rabbit_hide 4
            rabbit_foot 90
            cod 8
            salmon 10
            tropical_fish 20
            pufferfish 30
            ink_sac 6
            glow_ink_sac 14
            water_bucket 125
            lava_bucket 140
            milk_bucket 130
            powder_snow_bucket 130
            flint 3
            vine 3
            lily_pad 5
            moss_block 6
            red_mushroom 5
            brown_mushroom 5
            sea_pickle 6
            glow_lichen 5
            white_wool 10
            sponge 120
            turtle_egg 120
            sniffer_egg 400
            torchflower_seeds 20
            pitcher_pod 20
            shroomlight 12
            sculk 8
            """;

    public static synchronized void invalidate() {
        built = false;
    }

    public static synchronized void ensure(MinecraftServer server) {
        if (!built) {
            build(server);
            built = true;
        }
    }

    public static synchronized long baseValue(Item item) {
        Long v = values.get(item);
        return v == null ? 1L : v;
    }

    public static synchronized boolean isTargetable(Item item) {
        return targetable.contains(item);
    }

    public static synchronized List<Map.Entry<Item, Long>> entries() {
        return new ArrayList<>(values.entrySet());
    }

    /** Value of one item of this stack (durability & enchantments included). Works on both sides. */
    public static long unitValue(ItemStack s, ToLongFunction<Item> base) {
        long v = base.applyAsLong(s.getItem());
        double f = 1.0;
        if (s.isDamageable() && s.getMaxDamage() > 0) {
            f = Math.max(0.3, 1.0 - 0.7 * s.getDamage() / (double) s.getMaxDamage());
        }
        long total = Math.round(v * f);
        if (s.hasEnchantments()) {
            for (Map.Entry<Enchantment, Integer> e : EnchantmentHelper.get(s).entrySet()) {
                total += (long) e.getValue() * (e.getKey().isTreasure() ? 120 : 30);
            }
        }
        return Math.max(1L, total);
    }

    private static void build(MinecraftServer server) {
        Map<Item, Double> base = new HashMap<>();
        for (String line : SEEDS.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+");
            Item it = Registries.ITEM.get(new Identifier("minecraft", p[0]));
            if (it != Items.AIR) base.put(it, Double.parseDouble(p[1]));
        }
        seedTag(base, ItemTags.LOGS, 6);
        seedTag(base, ItemTags.SAPLINGS, 4);
        seedTag(base, ItemTags.LEAVES, 1);
        seedTag(base, ItemTags.FLOWERS, 3);

        DynamicRegistryManager drm = server.getRegistryManager();
        Map<Item, List<Recipe<?>>> byOutput = new HashMap<>();
        for (Recipe<?> r : server.getRecipeManager().values()) {
            ItemStack out = r.getOutput(drm);
            if (out.isEmpty()) continue;
            byOutput.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(r);
        }

        Map<Item, Double> memo = new HashMap<>();
        Set<Item> visiting = new HashSet<>();
        Map<Item, Long> result = new HashMap<>();
        Set<Item> target = new HashSet<>();
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            double v = resolve(item, base, byOutput, memo, visiting, drm);
            if (Double.isInfinite(v) || Double.isNaN(v)) v = fallback(item);
            result.put(item, Math.max(1L, Math.round(v)));
            if (!EXCLUDED.contains(Registries.ITEM.getId(item).getPath())) target.add(item);
        }
        values = result;
        targetable = target;
    }

    private static void seedTag(Map<Item, Double> base, TagKey<Item> tag, double v) {
        for (RegistryEntry<Item> e : Registries.ITEM.iterateEntries(tag)) base.putIfAbsent(e.value(), v);
    }

    private static double resolve(Item item, Map<Item, Double> base, Map<Item, List<Recipe<?>>> byOutput,
                                  Map<Item, Double> memo, Set<Item> visiting, DynamicRegistryManager drm) {
        Double b = base.get(item);
        if (b != null) return b;
        Double m = memo.get(item);
        if (m != null) return m;
        if (!visiting.add(item)) return INF;

        double best = INF;
        Identifier id = Registries.ITEM.getId(item);
        if (id.getNamespace().equals("minecraft") && id.getPath().startsWith("netherite_")
                && NETHERITE_GEAR.contains(id.getPath().substring(10))) {
            Item dia = Registries.ITEM.get(new Identifier("minecraft", "diamond_" + id.getPath().substring(10)));
            Item ingot = Registries.ITEM.get(new Identifier("minecraft", "netherite_ingot"));
            best = resolve(dia, base, byOutput, memo, visiting, drm) + resolve(ingot, base, byOutput, memo, visiting, drm) + 350;
        } else {
            for (Recipe<?> r : byOutput.getOrDefault(item, List.of())) {
                int count = Math.max(1, r.getOutput(drm).getCount());
                double sum = 0;
                int n = 0;
                boolean ok = true;
                for (Ingredient ing : r.getIngredients()) {
                    if (ing.isEmpty()) continue;
                    n++;
                    double cheapest = INF;
                    for (ItemStack st : ing.getMatchingStacks()) {
                        cheapest = Math.min(cheapest, resolve(st.getItem(), base, byOutput, memo, visiting, drm));
                    }
                    if (Double.isInfinite(cheapest)) {
                        ok = false;
                        break;
                    }
                    sum += cheapest;
                }
                if (!ok || n == 0) continue;
                best = Math.min(best, sum * CRAFT_MARKUP / count);
            }
        }
        visiting.remove(item);
        if (!Double.isInfinite(best)) memo.put(item, best);
        return best;
    }

    private static double fallback(Item item) {
        if (item instanceof SpawnEggItem) return 300;
        if (item instanceof MusicDiscItem) return 250;
        if (item instanceof SmithingTemplateItem) return 350;
        if (item instanceof BannerPatternItem) return 60;
        Rarity r = new ItemStack(item).getRarity();
        double v = switch (r) {
            case COMMON -> 6;
            case UNCOMMON -> 45;
            case RARE -> 180;
            default -> 750;
        };
        return item.getMaxCount() == 1 ? v * 1.5 : v;
    }
}
