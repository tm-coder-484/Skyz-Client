package net.sebbyo.combat.team;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /party create|invite|accept|decline|leave|disband|list|info|msg}.
 *
 * <p>Named <b>party</b>, not <b>team</b>, so it never merges with Minecraft's op-only
 * {@code /team} scoreboard command. Each party is mirrored onto a managed scoreboard
 * team ({@link PartyVisuals}) so members still get the vanilla team visuals (coloured
 * nametags + glow, see-invisible teammates, locator-bar grouping) — but driven by this
 * command, so no player needs op.
 */
public final class TeamCommands {

    private TeamCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("party")
                .then(literal("create").then(argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            TeamStore.Result r = store().create(p.getUuid(), StringArgumentType.getString(ctx, "name"));
                            respond(ctx, r);
                            if (r.ok()) PartyVisuals.apply(ctx.getSource().getServer(), p);
                            return 1;
                        })))
                .then(literal("invite").then(argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                            TeamStore.Result r = store().invite(p.getUuid(), target.getUuid());
                            respond(ctx, r);
                            if (r.ok()) {
                                target.sendMessage(Text.literal(p.getName().getString()
                                        + " invited you to a party. /party accept or /party decline."), false);
                            }
                            return 1;
                        })))
                .then(literal("accept").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    TeamStore.Result r = store().accept(p.getUuid());
                    respond(ctx, r);
                    if (r.ok()) PartyVisuals.apply(ctx.getSource().getServer(), p);
                    return 1;
                }))
                .then(literal("decline").executes(ctx -> {
                    respond(ctx, store().decline(ctx.getSource().getPlayerOrThrow().getUuid()));
                    return 1;
                }))
                .then(literal("leave").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    Team before = store().teamOf(p.getUuid());
                    String partyName = before == null ? null : before.name;
                    TeamStore.Result r = store().leave(p.getUuid());
                    respond(ctx, r);
                    if (r.ok()) {
                        MinecraftServer server = ctx.getSource().getServer();
                        PartyVisuals.clear(server, p);
                        if (partyName != null && store().getTeamByName(partyName) == null) {
                            PartyVisuals.deleteTeam(server, partyName);
                        }
                    }
                    return 1;
                }))
                .then(literal("disband").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    Team before = store().teamOf(p.getUuid());
                    String partyName = before == null ? null : before.name;
                    List<UUID> members = before == null ? List.of() : new ArrayList<>(before.members);
                    TeamStore.Result r = store().disband(p.getUuid());
                    respond(ctx, r);
                    if (r.ok()) {
                        MinecraftServer server = ctx.getSource().getServer();
                        if (partyName != null) PartyVisuals.deleteTeam(server, partyName);
                        for (UUID m : members) {
                            ServerPlayerEntity online = server.getPlayerManager().getPlayer(m);
                            if (online != null) PartyVisuals.clear(server, online);
                        }
                    }
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

    private static void respond(CommandContext<ServerCommandSource> ctx, TeamStore.Result r) {
        feedback(ctx, r.message());
        if (r.ok()) TeamManager.INSTANCE.save();
    }

    private static void feedback(CommandContext<ServerCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
    }
}
