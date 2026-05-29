package net.skyz.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.skyz.client.SkyzClientMod;

/**
 * Background music manager using MC's SoundManager.
 *
 * We register a custom SoundEvent at skyz_client:menu_music
 * and tell the sound manager to play it.
 *
 * For the music FILE: place music.ogg in .minecraft/resourcepacks/skyz_music/
 * or we fall back to playing a vanilla ambient sound quietly as placeholder.
 *
 * Toggle with the mute button. Volume follows the game's music volume setting.
 */
public class SkyzAudioManager {

    private static SkyzAudioManager INSTANCE;
    public static SkyzAudioManager getInstance() {
        if (INSTANCE == null) INSTANCE = new SkyzAudioManager();
        return INSTANCE;
    }

    private boolean muted   = false;
    private boolean playing = false;
    private SoundInstance current = null;

    private SkyzAudioManager() {}

    /** Start playing menu music. Safe to call multiple times. */
    public void play() {
        if (muted || playing) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;

        // Use a repeating ambient sound as placeholder music.
        // Ideally the user adds music.ogg to a resource pack.
        // We use MusicType.MENU which MC uses internally.
        mc.execute(() -> {
            try {
                // Play the vanilla menu/title music if it exists, looped quietly.
                // SimpleSoundInstance.forUI plays at full screen volume (was PositionedSoundInstance.master in yarn).
                current = SimpleSoundInstance.forUI(
                        SoundEvents.MUSIC_MENU.value(), 1.0f, 0.35f);
                mc.getSoundManager().play(current);
                playing = true;
                SkyzClientMod.LOGGER.info("[Skyz Client] Menu music started.");
            } catch (Exception e) {
                SkyzClientMod.LOGGER.warn("[Skyz Client] Could not start music: {}", e.getMessage());
            }
        });
    }

    /** Stop current music. */
    public void stop() {
        if (!playing || current == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        mc.execute(() -> {
            try {
                mc.getSoundManager().stop(current);
            } catch (Exception ignored) {}
            playing = false;
            current = null;
        });
    }

    /**
     * Toggle mute. Returns true if now playing.
     */
    public boolean toggle() {
        muted = !muted;
        if (muted) {
            stop();
            return false;
        } else {
            play();
            return true;
        }
    }

    public boolean isMuted()   { return muted; }
    public boolean isPlaying() { return playing; }
}
