package com.capitalcraft.registry;

import com.capitalcraft.CapitalCraft;
import com.capitalcraft.block.GpuBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final BlockEntityType<GpuBlockEntity> GPU = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            CapitalCraft.id("gpu"),
            FabricBlockEntityTypeBuilder.create(GpuBlockEntity::new,
                    ModBlocks.GPU_BASIC, ModBlocks.GPU_ADVANCED, ModBlocks.GPU_ELITE).build());

    public static void init() {}
}
