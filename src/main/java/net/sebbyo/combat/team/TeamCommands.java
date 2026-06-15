package net.sebbyo.combat.team;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** {@code /team create|invite|accept|decline|leave|disband|list|info}. */
public final class TeamCommands {

    private TeamCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("team")
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
                                target.sendMessage(Text.literal(
                                        p.getName().getString() + " invited you to a team. /team accept or /team decline."), false);
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
                    if (t == null) feedback(ctx, "You are not in a team.");
                    else feedback(ctx, "Team '" + t.name + "' — " + t.members.size() + " member(s).");
                    return 1;
                }))
                .then(literal("list").executes(ctx -> {
                    if (store().all().isEmpty()) feedback(ctx, "There are no teams.");
                    else store().all().forEach(t -> feedback(ctx, "- " + t.name + " (" + t.members.size() + ")"));
                    return 1;
                })));
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
