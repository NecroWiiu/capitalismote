package com.capitalcraft.block;

import com.capitalcraft.Fmt;
import com.capitalcraft.data.CapitalState;
import com.capitalcraft.data.PlayerData;
import com.capitalcraft.net.Net;
import com.capitalcraft.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.List;

public class GpuBlock extends HorizontalFacingBlock implements BlockEntityProvider {
    public static final BooleanProperty LIT = Properties.LIT;
    public final int tier;

    public GpuBlock(int tier, Settings settings) {
        super(settings);
        this.tier = tier;
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(LIT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!world.isClient && placer instanceof ServerPlayerEntity sp && world.getBlockEntity(pos) instanceof GpuBlockEntity be) {
            be.setOwner(sp.getUuid());
            CapitalState st = CapitalState.get(sp.getServer());
            PlayerData d = st.player(sp.getUuid());
            d.add("gpu_placed", 1);
            st.markDirty();
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (player instanceof ServerPlayerEntity sp && world.getBlockEntity(pos) instanceof GpuBlockEntity be) {
            if (be.getOwner() == null) be.setOwner(sp.getUuid());
            if (!be.isOwner(sp) && !sp.hasPermissionLevel(2)) {
                sp.sendMessage(Fmt.t("gpu.not_owner"), true);
                return ActionResult.CONSUME;
            }
            Net.sendOpen(sp, Net.OPEN_GPU, pos);
            Net.sendGpuState(sp, be);
        }
        return ActionResult.CONSUME;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new GpuBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient || type != ModBlockEntities.GPU) return null;
        return (w, p, s, be) -> GpuBlockEntity.tick(w, p, s, (GpuBlockEntity) be);
    }

    @Override
    public void appendTooltip(ItemStack stack, BlockView world, List<Text> tooltip, TooltipContext options) {
        tooltip.add(Fmt.t("tip.gpu." + tier).formatted(Formatting.GRAY));
        tooltip.add(Fmt.t("tip.gpu.use").formatted(Formatting.DARK_GRAY));
    }
}
