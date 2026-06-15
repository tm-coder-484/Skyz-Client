package net.sebbyo.combat.net;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.sebbyo.combat.SebbyoCombatMod;

/** S2C: remaining combat time, in seconds (0 = not in combat). */
public record CombatTimerPayload(int seconds) implements CustomPayload {

    public static final CustomPayload.Id<CombatTimerPayload> ID =
            new CustomPayload.Id<>(Identifier.of(SebbyoCombatMod.MOD_ID, "combat_timer"));

    public static final PacketCodec<PacketByteBuf, CombatTimerPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.VAR_INT, CombatTimerPayload::seconds, CombatTimerPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
