package net.skyz.client.render;

/**
 * ESP box-outline renderer — <b>dormant on the 26.1 port</b>.
 *
 * <p>TODO[PORT-26.1]: The original implementation drew storage / player /
 * mob / block-ESP boxes via Fabric's {@code WorldRenderEvents.LAST}
 * ({@code fabric-rendering-v1}). That module is NOT present in Fabric API
 * 0.145.4+26.1.2 — per Fabric's 26.1 announcement, the Renderer/Indigo
 * rendering modules "may not be available for the initial release of 26.1."
 *
 * <p>The full pre-port logic is preserved in git history (the 1.21.11
 * {@code WorldRendererMixin}) and in {@code Sebbyo-mod2}. When the Fabric
 * rendering module ships for 26.1 — or when we migrate ESP to a
 * {@code LevelRenderer} mixin against the new render-state pipeline —
 * restore the box-drawing here.
 *
 * <p>NOTE: ESP <i>detection</i> is unaffected. {@code SkyzClientState}'s
 * background scan thread still runs and keeps {@code blockEspMatches}
 * up to date; only the on-screen drawing is paused.
 */
public final class SkyzEspRenderer {
    private SkyzEspRenderer() {}

    // Intentionally no render() entry point on 26.1 — see class javadoc.
    // The matching registration in SkyzClientMod is commented out with the
    // same PORT-26.1 marker so both halves are re-enabled together.
}
