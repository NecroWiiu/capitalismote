package com.capitalcraft.item;

import com.capitalcraft.Fmt;
import com.capitalcraft.net.Net;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

public class MenuItem extends Item {
    public enum Kind { CASINO, TERMINAL }

    public final Kind kind;

    public MenuItem(Kind kind, Settings settings) {
        super(settings);
        this.kind = kind;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp) {
            Net.sendWallet(sp);
            Net.sendOpen(sp, kind == Kind.CASINO ? Net.OPEN_CASINO : Net.OPEN_TERMINAL, BlockPos.ORIGIN);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Fmt.t(kind == Kind.CASINO ? "tip.casino" : "tip.terminal").formatted(Formatting.GRAY));
    }
}
