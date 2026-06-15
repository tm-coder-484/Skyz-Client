# Sebbyo Combat — Design Spec

**Date:** 2026-06-15
**Target:** Minecraft 1.21.11 · Fabric · Yarn mappings · Java 21
**Status:** Approved design, ready for implementation planning

A DonutSMP-style PvP mod with three interlocking systems: a **combat tag** (with
combat-log death), a **kill-reward** buff system, and a **team / friendly-fire**
system. Server-authoritative enforcement plus a client-rendered HUD timer, shipped
as **one jar** that runs on both sides (with an action-bar fallback for vanilla
clients).

---

## 1. Goals & non-goals

**Goals**
- Tag players in combat for 20s on player-inflicted damage; show a styled countdown.
- Punish combat logging with an instant, properly-credited death.
- Reward player kills with a random, stacking, until-death potion effect.
- Block all friendly fire between teammates; persist teams across restarts.

**Non-goals (YAGNI)**
- No command-blocking during combat (e.g. blocking /home) — not requested.
- No GUI for teams (commands only).
- No config screen in v1 (a few constants/JSON config at most).

---

## 2. Environment & build

Reuse the verified 1.21.11 toolchain from the sibling Skyz mod
(`Downloads/Sebbyo-mod2/gradle.properties`):

| Item | Version |
|---|---|
| Minecraft | 1.21.11 |
| Yarn mappings | 1.21.11+build.1 |
| Fabric Loader | 0.17.3 |
| Fabric API | 0.140.2+1.21.11 |
| Loom | 1.15.3 |
| Java | 21 |

- **Mod id:** `sebbyo_combat` · **package:** `net.sebbyo.combat` · **name:** Sebbyo Combat
  (name/id easily renamed before release).
- `fabric.mod.json` declares a **main** entrypoint (`SebbyoCombatMod`) and a
  **client** entrypoint (`SebbyoCombatClient`), plus the mixins config.

---

## 3. Architecture

Single jar, environment-split:

```
SebbyoCombatMod (ModInitializer, runs on server + integrated server)
 ├─ DamageCore            shared damage interception + attacker resolution
 ├─ CombatManager         per-player combat state, 20s timer, combat-log death
 ├─ KillRewardManager     random effect grant + stacking rules
 ├─ TeamManager           team CRUD, friendly-fire checks, JSON persistence
 ├─ TeamCommands          /team … (Brigadier via CommandRegistrationCallback)
 └─ CombatNetworking      S2C custom payload: remaining combat ticks

SebbyoCombatClient (ClientModInitializer, client only)
 └─ CombatHudRenderer     style-A pill timer, bottom-center (HudRenderCallback)
```

**Networking:** a single S2C custom payload `combat_timer` carrying the player's
remaining ticks (int). Sent when the value changes (start / reset / per-second
tick-down / clear). Modded clients render the HUD from it. Vanilla clients never
negotiate the channel → server falls back to an **action-bar** message each second
(`sendMessage(text, overlay=true)`) so they still see a countdown.

**State ownership:** all gameplay state lives server-side. The client only ever
*displays* what the server tells it. Combat state is transient (not persisted);
**team state and the kill-reward ledger are persisted** to the world folder
(`sebbyo_teams.json`, `sebbyo_rewards.json`).

**Hosting compatibility (dedicated *and* integrated/Essential-hosted):** the mod
targets `environment="*"` and uses only standard server-side Fabric events, which
fire identically on an **integrated server** — i.e. when a host opens a singleplayer
world to friends (vanilla Open-to-LAN or Essential's "invite friends"). Therefore:
- The **host must have the mod**; their integrated server enforces all rules for
  everyone connected. Joining friends need it only for the fancy HUD — without it
  they get the **action-bar fallback** and are still fully enforced (server-
  authoritative). The `canSend()` check means the timer degrades gracefully to the
  action bar if Essential's relay doesn't negotiate the custom channel.
- Persistence uses `MinecraftServer.getSavePath(WorldSavePath.ROOT)`, which resolves
  to the singleplayer world folder on an integrated server, so teams/effects survive
  the host reopening the world.
- **Backstop:** a host who is combat-tagged when they *close the world* is a server
  shutdown, not a clean disconnect — handle tagged players on `SERVER_STOPPING` in
  addition to `DISCONNECT`.
- **Testing** is therefore done both on a dev dedicated server (`runServer`) and via
  a `runClient` world opened to LAN (the integrated-server path Essential uses).

---

## 4. Module: Combat tag

**State:** `CombatManager` holds `Map<UUID, CombatState>` where `CombatState` =
`{ int remainingTicks, UUID lastAttacker, long lastAttackerStampTick }`.

**Tick loop** (`ServerTickEvents.END_SERVER_TICK`): decrement each player's
`remainingTicks`; when it crosses to 0, clear state and send a "0" sync; otherwise
sync on change / once per second.

**Triggers (all set `remainingTicks = 400` = 20s):**
- A player **takes** player-inflicted damage → tag victim.
- A player **deals** damage to another player → tag attacker.
  (One PvP hit therefore tags both.)
- A tagged player **throws an ender pearl** or **uses a wind charge** → reset to
  20s. Detected via `UseItemCallback` / projectile-spawn; **only if already
  tagged** (no effect when untagged).

**"Player-inflicted" resolution (DamageCore):** a `DamageSource` counts when its
attacker/owner resolves to a `ServerPlayerEntity` — direct melee (`getAttacker`),
player projectiles via `getSource`/projectile owner (arrows, tridents,
snowballs…), and thrown splash/lingering potions (thrower). The resolved attacker
is recorded as `lastAttacker` for kill credit.

**Death while tagged:** on `ServerPlayerEvents.AFTER_RESPAWN` / death detection,
clear the victim's combat state (→0).

**Combat-log death (the key rule):** on `ServerPlayConnectionEvents.DISCONNECT`,
if the player is tagged:
1. Kill the player entity server-side immediately (`player.kill()` / apply lethal
   damage) attributed to `lastAttacker` so vanilla drops items + XP at the logout
   location and the killer gets credit + the kill reward.
2. Player respawns normally on reconnect.
Untagged disconnects are untouched.

**Exemptions:** creative & spectator players are never tagged.

---

## 5. Module: Kill rewards

On a confirmed **player-vs-player kill** (victim is a player, killer resolves to a
player — including combat-log kills), the killer gains **one random** effect from:

`Speed, Saturation, Conduit Power, Haste, Regeneration, Resistance,
Fire Resistance, Health Boost, Absorption, Invisibility, Hero of the Village`

- **Duration:** true-infinite (`StatusEffectInstance.INFINITE`), but the reward set
  is **mod-owned and persisted** (see Lifecycle) rather than relying on vanilla
  effect state.
- **Stacking when the rolled effect is re-granted:**
  - **Speed** → amplifier += 1, unbounded.
  - **Health Boost** → max level 4 (amplifier 3).
  - **Absorption** → max level 2 (amplifier 1).
  - **Regeneration** → max level 1 (amplifier 0).
  - **All other rewards** (Saturation, Conduit Power, Haste, Resistance, Fire
    Resistance, Invisibility, Hero of the Village) → max level 2 (amplifier 1).
  - A roll for an effect already at its cap is a no-op.

**Negative penalty pool** — when a player dies holding **no** reward buffs they gain
a random debuff (uniform random pick, levelled up to its cap, stored in the **same**
ledger and persisted / re-applied just like rewards):

| Effect | Max level |
|---|---|
| Blindness · Nausea · Darkness · Bad Omen · Glowing (spectral-arrow glow) | 1 |
| Jump Boost · Slow Falling · Hunger · Slowness · Mining Fatigue · Weakness | 2 |

Rewards and penalties **coexist** in the ledger and both persist. A buff-less death
adds one penalty level; conversely **each PvP kill also removes one level of a random
penalty** (removed entirely at level 1) in addition to granting a reward — so landing
kills both buffs you and digs you out of penalties.

**Lifecycle (persistence · death · restart):** the mod keeps a per-player ledger
`Map<UUID, Map<effectId, amplifier>>` (rewards **and** penalties) persisted to
`world/sebbyo_rewards.json` (same Gson / world-folder pattern as teams).
- **On kill:** apply the reward stacking rule **and** drop one level of one random
  penalty the player currently holds (if any); update the ledger, (re)apply the
  resulting effects at infinite duration, save.
- **On death (any cause):** vanilla clears effects on death; the mod then applies
  one step based on the player's current **reward (positive)** effects:
  - **Has ≥1 reward effect** → drop **one level of one random** reward effect
    (amplifier − 1; Speed 5 → Speed 4; a level-1 reward is removed entirely).
  - **Has no reward effects** → grant **one random negative effect** from the penalty
    pool above, levelling a random entry up to its cap.
  It then re-applies the resulting ledger (rewards + penalties) after respawn and
  saves.
- **On join / server start:** re-apply the ledger's effects to the player at infinite
  duration, so buffs **and** penalties **survive disconnects and server restarts**.

**Note:** kill rewards and combat tagging both read the same DamageCore
attacker-resolution, so credit is consistent between the two systems.

---

## 6. Module: Teams & friendly fire

**Commands** (Brigadier):
`/team create <name>`, `/team invite <player>`, `/team accept`, `/team decline`,
`/team leave`, `/team disband`, `/team list`, `/team info`.

**Data model** (`TeamManager`):
```
Team { String name; UUID leader; List<UUID> members (ordered by join time); }
Map<UUID, Team>  playerIndex   (one team per player)
Map<UUID, PendingInvite>       (invited player → {team, inviter, expiry})
```

**Rules:**
- One team per player; `create`/`accept` rejected if already in a team.
- Names unique, ≤16 chars, alphanumeric/underscore; no hard member cap.
- Invite → target must `/team accept` (or `/team decline`); invites expire (e.g.
  2 min).
- **Leader-only:** `invite`, `disband`.
- **Leader `/team leave`** → leadership transfers to the **oldest remaining
  member**; if the leader was the last member, the team is removed.
- `disband` removes the team for all members.

**Friendly fire (DamageCore):** if attacker and victim are **players on the same
team**, the damage is **cancelled** (melee, player projectiles, and splash/lingering
potion effects), and the blocked hit **does not** combat-tag or reward anyone.

**Persistence:** serialize teams to `world/sebbyo_teams.json` (Gson). Load on
server start (`ServerLifecycleEvents.SERVER_STARTED`), save on every mutation
(create/join/leave/disband/transfer) and on server stop. Pending invites are
transient (in-memory only).

---

## 7. Module: HUD timer (client)

**Style A** (approved): bottom-center, just above the health bar — a rounded dark
pill with a sword icon, an "IN COMBAT" label, a depleting red progress bar, and the
remaining seconds. Rendered via `HudRenderCallback` using `DrawContext`
(rounded-rect + text). Driven purely by the latest `combat_timer` payload value;
hidden when remaining = 0. Smoothly interpolates the bar between per-second updates
using client render delta.

**Vanilla fallback:** server action-bar text (`"⚔ In combat: 13s"`) once per second
for clients that didn't register the channel.

---

## 8. Edge cases & decisions

- PvP hit tags both attacker and victim; FF-blocked hit tags neither.
- Pearl/wind-charge reset applies only to already-tagged players.
- Combat-log kill grants the killer the same kill reward as a normal kill.
- Creative/spectator: not tagged; not subject to FF cancellation (they don't deal
  survival damage anyway).
- Death **with** reward buffs → lose one level of one random buff (Speed 5 → Speed 4;
  a level-1 buff is removed). Death **with no** buffs (any cause) → gain one level of
  a random penalty, capped per the pool. Each **PvP kill** grants a reward **and**
  removes one level of a random penalty. Buffs and penalties coexist, persist, and
  survive restarts.
- Self-damage / environmental (fall, lava, mobs) never tags (only player sources).

---

## 9. Testing plan

- **Unit-ish (server logic):** team membership/transfer/persistence round-trip;
  stacking-rule function (`nextAmplifier(effect, current)`); attacker-resolution for
  melee/arrow/trident/splash-potion sources.
- **In-game (dev `runServer` + two clients or a client + fake players):**
  1. Hit a player → both tagged 20s; HUD shows; action-bar on a vanilla client.
  2. Pearl while tagged → resets to 20s; pearl while untagged → nothing.
  3. Disconnect while tagged → die at logout spot, items/XP drop, killer credited
     + buffed; respawn on reconnect.
  4. Die while tagged → tag clears.
  5. Kill a player → random effect; kill again with Speed rolled → Speed II; verify
     Health Boost caps at 2, Absorption/Regen at 1; die → effects wiped.
  6. `/team create`/`invite`/`accept`; teammate hits do nothing and don't tag;
     restart server → team persists.

---

## 10. Risks / to verify during implementation

- Exact 1.21.11 Yarn names/signatures for: damage event
  (`ServerLivingEntityEvents.ALLOW_DAMAGE` arity), projectile/potion owner
  resolution, infinite `StatusEffectInstance` constant, `player.kill()` credit path,
  `CustomPayload`/`PayloadTypeRegistry` networking, and `HudRenderCallback`. These
  are standard but will be pinned against the actual jar (same approach used for the
  Skyz mod) in the implementation plan.
- Killing on DISCONNECT must run before the player entity is removed — verify the
  event fires while the entity is still in the world; otherwise apply on the tick
  before removal via a queued lethal-damage flag.
