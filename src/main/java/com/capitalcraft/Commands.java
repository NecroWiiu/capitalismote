package com.capitalcraft;

import com.capitalcraft.data.CapitalState;
import com.capitalcraft.data.PlayerData;
import com.capitalcraft.job.JobDef;
import com.capitalcraft.job.Jobs;
import com.capitalcraft.market.MarketEngine;
import com.capitalcraft.net.Net;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Random;

public final class Commands {
    private Commands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("capital")
                        .then(CommandManager.literal("saldo").executes(ctx -> balance(ctx.getSource())))
                        .then(CommandManager.literal("jornada").executes(ctx -> shift(ctx.getSource())))
                        .then(CommandManager.literal("ayuda").executes(ctx -> help(ctx.getSource())))
                        .then(CommandManager.literal("dar").requires(s -> s.hasPermissionLevel(2))
                                .then(CommandManager.argument("jugador", EntityArgumentType.player())
                                        .then(CommandManager.argument("monto", LongArgumentType.longArg(0))
                                                .executes(ctx -> give(ctx, 1)))))
                        .then(CommandManager.literal("quitar").requires(s -> s.hasPermissionLevel(2))
                                .then(CommandManager.argument("jugador", EntityArgumentType.player())
                                        .then(CommandManager.argument("monto", LongArgumentType.longArg(0))
                                                .executes(ctx -> give(ctx, -1)))))
                        .then(CommandManager.literal("trabajo").requires(s -> s.hasPermissionLevel(2))
                                .then(CommandManager.argument("jugador", EntityArgumentType.player())
                                        .then(CommandManager.argument("id", StringArgumentType.word())
                                                .suggests((c, b) -> CommandSource.suggestMatching(Jobs.DEFS.keySet(), b))
                                                .executes(Commands::setJob))))
                        .then(CommandManager.literal("finjornada").requires(s -> s.hasPermissionLevel(2))
                                .then(CommandManager.argument("jugador", EntityArgumentType.player())
                                        .executes(Commands::endShift)))
                        .then(CommandManager.literal("noticia").requires(s -> s.hasPermissionLevel(2))
                                .executes(ctx -> {
                                    CapitalState st = CapitalState.get(ctx.getSource().getServer());
                                    MarketEngine.makeEvent(st.market, new Random());
                                    st.markDirty();
                                    ctx.getSource().sendFeedback(() -> Fmt.t("cmd.news"), false);
                                    return 1;
                                }))));
    }

    private static int balance(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) return 0;
        PlayerData d = CapitalState.get(src.getServer()).player(p.getUuid());
        src.sendFeedback(() -> Fmt.t("cmd.balance", Fmt.money(d.balance)).formatted(Formatting.GOLD), false);
        return 1;
    }

    private static int shift(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) return 0;
        PlayerData d = CapitalState.get(src.getServer()).player(p.getUuid());
        if (d.jobId.isEmpty()) {
            src.sendFeedback(() -> Fmt.t("cmd.nojob"), false);
            return 0;
        }
        JobDef def = Jobs.DEFS.get(d.jobId);
        if (def == null) return 0;
        long[] prog = Jobs.progress(p, d, def);
        src.sendFeedback(() -> Fmt.t("job.assigned", Fmt.t("job." + def.id), Fmt.money(def.wage)).formatted(Formatting.YELLOW), false);
        for (int i = 0; i < prog.length; i++) {
            final int idx = i;
            src.sendFeedback(() -> Text.literal(" - ").append(Fmt.t("job." + def.id + ".o" + idx))
                    .append(Text.literal(": " + prog[idx] + "/" + def.objectives.get(idx).count)), false);
        }
        return 1;
    }

    private static int help(ServerCommandSource src) {
        for (int i = 1; i <= 6; i++) {
            final int n = i;
            src.sendFeedback(() -> Fmt.t("help." + n), false);
        }
        return 1;
    }

    private static int give(CommandContext<ServerCommandSource> ctx, int sign) {
        try {
            ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "jugador");
            long amount = LongArgumentType.getLong(ctx, "monto");
            CapitalState st = CapitalState.get(ctx.getSource().getServer());
            PlayerData d = st.player(p.getUuid());
            d.balance = Math.max(0, d.balance + sign * amount);
            st.markDirty();
            Net.sendWallet(p, d);
            ctx.getSource().sendFeedback(() -> Fmt.t("cmd.balance", Fmt.money(d.balance)), true);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int setJob(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "jugador");
            String id = StringArgumentType.getString(ctx, "id");
            JobDef def = Jobs.DEFS.get(id);
            if (def == null) {
                ctx.getSource().sendError(Fmt.t("cmd.badjob"));
                return 0;
            }
            CapitalState st = CapitalState.get(ctx.getSource().getServer());
            Jobs.assign(p, st.player(p.getUuid()), def);
            st.markDirty();
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int endShift(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "jugador");
            CapitalState st = CapitalState.get(ctx.getSource().getServer());
            PlayerData d = st.player(p.getUuid());
            if (d.jobId.isEmpty()) return 0;
            Jobs.settle(p, d);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }
}
