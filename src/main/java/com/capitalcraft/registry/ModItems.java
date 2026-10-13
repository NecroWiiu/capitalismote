package com.capitalcraft.registry;

import com.capitalcraft.CapitalCraft;
import com.capitalcraft.Fmt;
import com.capitalcraft.item.MenuItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
    private ModItems() {}

    public static final Item CASINO_CHIP = reg("casino_chip", new MenuItem(MenuItem.Kind.CASINO, new Item.Settings().maxCount(1)));
    public static final Item STOCK_TERMINAL = reg("stock_terminal", new MenuItem(MenuItem.Kind.TERMINAL, new Item.Settings().maxCount(1)));
    public static final Item GPU_BASIC = reg("gpu_basic", new BlockItem(ModBlocks.GPU_BASIC, new Item.Settings()));
    public static final Item GPU_ADVANCED = reg("gpu_advanced", new BlockItem(ModBlocks.GPU_ADVANCED, new Item.Settings()));
    public static final Item GPU_ELITE = reg("gpu_elite", new BlockItem(ModBlocks.GPU_ELITE, new Item.Settings()));

    private static Item reg(String name, Item item) {
        return Registry.register(Registries.ITEM, CapitalCraft.id(name), item);
    }

    public static void init() {
        Registry.register(Registries.ITEM_GROUP, CapitalCraft.id("main"), FabricItemGroup.builder()
                .displayName(Fmt.t("group"))
                .icon(() -> new ItemStack(CASINO_CHIP))
                .entries((ctx, entries) -> {
                    entries.add(CASINO_CHIP);
                    entries.add(STOCK_TERMINAL);
                    entries.add(GPU_BASIC);
                    entries.add(GPU_ADVANCED);
                    entries.add(GPU_ELITE);
                })
                .build());
    }
}
