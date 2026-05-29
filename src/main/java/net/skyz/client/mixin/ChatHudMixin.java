package net.skyz.client.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.skyz.client.util.SkyzClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Prepends a timestamp prefix to incoming chat messages when the toggle is on.
 * The format matches Feather/Lunar: dim grey [HH:mm] before the message body.
 *
 * <p>In 26.1 Mojang mappings the vanilla {@code ChatHud} class is named
 * {@code ChatComponent} (in {@code net.minecraft.client.gui.components}),
 * but mixin still uses the simple-name lookup for {@code ChatHud.class}
 * because that's what we historically targeted — wait, no, we must target
 * the Mojang class. So {@code @Mixin(ChatComponent.class)} here.
 */
@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {

    private static final SimpleDateFormat SKYZ$FMT = new SimpleDateFormat("HH:mm");

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
                    at = @At("HEAD"), argsOnly = true)
    private Component skyz$prependTimestamp(Component orig) {
        if (orig == null || !SkyzClientState.chatTimestamps) return orig;
        String ts = "[" + SKYZ$FMT.format(new Date()) + "] ";
        MutableComponent prefix = Component.literal(ts)
                .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY));
        return Component.empty().append(prefix).append(orig);
    }
}
