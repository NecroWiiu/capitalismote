package com.capitalcraft.registry;

import com.capitalcraft.CapitalCraft;
import com.capitalcraft.block.GpuBlock;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.MapColor;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
    private ModBlocks() {}

    public static final GpuBlock GPU_BASIC = reg("gpu_basic", new GpuBlock(1, settings(3.0f)));
    public static final GpuBlock GPU_ADVANCED = reg("gpu_advanced", new GpuBlock(2, settings(4.0f)));
    public static final GpuBlock GPU_ELITE = reg("gpu_elite", new GpuBlock(3, settings(5.0f)));

    private static FabricBlockSettings settings(float hardness) {
        return FabricBlockSettings.create()
                .mapColor(MapColor.GRAY)
                .strength(hardness, 6.0f)
                .requiresTool()
                .sounds(BlockSoundGroup.METAL)
                .luminance(state -> state.get(GpuBlock.LIT) ? 6 : 0);
    }

    private static GpuBlock reg(String name, GpuBlock block) {
        return Registry.register(Registries.BLOCK, CapitalCraft.id(name), block);
    }

    public static void init() {}
}
