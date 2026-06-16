package net.sebbyo.combat.team;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /party create|invite|accept|decline|leave|disband|list|info|msg}.
 *
 * <p>Named <b>party</b>, not <b>team</b>, on purpose: Minecraft already ships an
 * op-only {@code /team} (scoreboard teams). Registering our own {@code /team} merged
 * with the vanilla one, which op-gated everything and pulled in vanilla side effects
 * (glowing outlines, locator-bar visibility, etc.). A unique name keeps this mod's
 * party system completely separate and usable by everyone.
 */
public final class TeamCommands {

    private TeamCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("party")
                .then(literal("create").then(argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            apply(ctx, store().create(p.getUuid(), StringArgumentType.getString(ctx, "name")));
                            return 1;
                        })))
                .then(literal("invite").then(argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                            TeamStore.Result r = store().invite(p.getUuid(), target.getUuid());
                            apply(ctx, r);
                            if (r.ok()) {
                                target.sendMessage(Text.literal(p.getName().getString()
                                        + " invited you to a party. /party accept or /party decline."), false);
                            }
                            return 1;
                        })))
                .then(literal("accept").executes(ctx -> {
                    apply(ctx, store().accept(ctx.getSource().getPlayerOrThrow().getUuid()));
                    return 1;
                }))
                .then(literal("decline").executes(ctx -> {
                    apply(ctx, store().decline(ctx.getSource().getPlayerOrThrow().getUuid()));
                    return 1;
                }))
                .then(literal("leave").executes(ctx -> {
                    apply(ctx, store().leave(ctx.getSource().getPlayerOrThrow().getUuid()));
                    return 1;
                }))
                .then(literal("disband").executes(ctx -> {
                    apply(ctx, store().disband(ctx.getSource().getPlayerOrThrow().getUuid()));
                    return 1;
                }))
                .then(literal("info").executes(ctx -> {
                    Team t = store().teamOf(ctx.getSource().getPlayerOrThrow().getUuid());
                    if (t == null) feedback(ctx, "You are not in a party.");
                    else feedback(ctx, "Party '" + t.name + "' — " + t.members.size() + " member(s).");
                    return 1;
                }))
                .then(literal("list").executes(ctx -> {
                    if (store().all().isEmpty()) feedback(ctx, "There are no parties.");
                    else store().all().forEach(t -> feedback(ctx, "- " + t.name + " (" + t.members.size() + ")"));
                    return 1;
                }))
                .then(literal("msg").then(argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            Team t = store().teamOf(p.getUuid());
                            if (t == null) {
                                feedback(ctx, "You are not in a party.");
                                return 1;
                            }
                            String msg = StringArgumentType.getString(ctx, "message");
                            MinecraftServer server = ctx.getSource().getServer();
                            Text line = Text.literal("[Party] " + p.getName().getString() + ": " + msg);
                            for (UUID member : t.members) {
                                ServerPlayerEntity online = server.getPlayerManager().getPlayer(member);
                                if (online != null) online.sendMessage(line, false);
                            }
                            return 1;
                        }))));
    }

    private static TeamStore store() {
        return TeamManager.INSTANCE.store();
    }

    private static void apply(CommandContext<ServerCommandSource> ctx, TeamStore.Result r) {
        feedback(ctx, r.message());
        if (r.ok()) TeamManager.INSTANCE.save();
    }

    private static void feedback(CommandContext<ServerCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
    }
}
