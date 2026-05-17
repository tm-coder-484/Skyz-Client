package net.skyz.client.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.skyz.client.util.SkyzClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Prepends a timestamp prefix to incoming chat messages when the toggle is on.
 * The format matches Feather/Lunar: dim grey [HH:mm] before the message body.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    private static final SimpleDateFormat SKYZ$FMT = new SimpleDateFormat("HH:mm");

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;)V",
                    at = @At("HEAD"), argsOnly = true)
    private Text skyz$prependTimestamp(Text orig) {
        if (orig == null || !SkyzClientState.chatTimestamps) return orig;
        String ts = "[" + SKYZ$FMT.format(new Date()) + "] ";
        MutableText prefix = Text.literal(ts).setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY));
        return Text.empty().append(prefix).append(orig);
    }
}
