# Sebbyo Combat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Fabric 1.21.11 mod implementing a DonutSMP-style combat-tag (with combat-log death), a persisted kill-reward/death-penalty effect ledger, and a team/friendly-fire system — server-enforced with a client-rendered HUD timer.

**Architecture:** One jar, two entrypoints. `SebbyoCombatMod` (ModInitializer) runs all gameplay on the server; `SebbyoCombatClient` (ClientModInitializer) only renders the HUD from a synced value. A shared `DamageCore` is the single damage-interception point feeding combat-tagging, friendly-fire blocking, and kill detection. Pure logic (effect stacking, team store) lives in Minecraft-free classes so it can be unit-tested with plain JUnit; Minecraft-coupled wiring is verified in-game.

**Tech Stack:** Minecraft 1.21.11, Fabric Loader 0.17.3, Fabric API 0.140.2+1.21.11, Yarn 1.21.11+build.1, Loom 1.15.3, Java 21, Gson (bundled with MC), JUnit 5 (test source set).

---

## API-pinning rule (applies to every Minecraft-coupled task)

Exact Yarn 1.21.11 names are confirmed against the real jar before use — the same method used for the Skyz port:

```
JAR = C:/Users/tmaco0/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.1-v2/minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.1-v2.jar
FABRIC = C:/Users/tmaco0/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.140.2+1.21.11/<hash>/fabric-api-0.140.2+1.21.11.jar
javap -cp "$JAR" net.minecraft.<Class> | grep -i <member>
```
The sibling mod `Downloads/Sebbyo-mod2` is a working 1.21.11 Yarn client mod — reuse its `build.gradle`, `gradle.properties`, `DrawContext`/`HudRenderCallback`-adjacent patterns, and mixin setup as the reference for names.

---

## File structure

```
sebbyo-mod3/
  build.gradle, settings.gradle, gradle.properties, gradlew(.bat), gradle/wrapper/…
  src/main/resources/
    fabric.mod.json
    sebbyo_combat.mixins.json
    assets/sebbyo_combat/icon.png            (optional)
  src/main/java/net/sebbyo/combat/
    SebbyoCombatMod.java                      ModInitializer: wires events, registers payload+commands
    DamageCore.java                           attacker resolution + isPlayerInflicted + friendly-fire decision
    combat/CombatManager.java                 Map<UUID,CombatState>, tick-down, tag/reset, sync, combat-log death
    combat/CombatState.java                   { int remainingTicks; UUID lastAttacker; }
    effect/EffectLedger.java                  PURE: per-player effect map + stacking/penalty rules (no MC imports)
    effect/EffectCatalog.java                 reward list, penalty list, caps (String ids) — PURE
    effect/EffectManager.java                 applies EffectLedger to ServerPlayerEntity; persistence glue
    effect/EffectStore.java                   JSON load/save of the ledger (world/sebbyo_rewards.json)
    team/Team.java                            { String name; UUID leader; List<UUID> members; }
    team/TeamStore.java                       PURE-ish: team CRUD + invites + JSON (UUID-only, no MC types)
    team/TeamManager.java                     server-facing wrapper: lookups, friendly-fire query, persistence
    team/TeamCommands.java                    Brigadier /team … registration
    net/CombatTimerPayload.java               S2C CustomPayload(record) { int seconds }
  src/client/java/net/sebbyo/combat/client/
    SebbyoCombatClient.java                   ClientModInitializer: receive payload, register HUD callback
    client/CombatHudRenderer.java             style-A pill timer via DrawContext
  src/test/java/net/sebbyo/combat/
    EffectLedgerTest.java                     JUnit: stacking caps, death loss, penalty gain, kill-removes-penalty
    TeamStoreTest.java                        JUnit: create/join/leave/transfer/disband/persistence round-trip
```

---

### Task 0: Project scaffold

**Files:**
- Create: `sebbyo-mod3/gradle.properties`, `settings.gradle`, `build.gradle`, `gradle/wrapper/gradle-wrapper.properties`, copy `gradlew`/`gradlew.bat`/`gradle-wrapper.jar` from `Downloads/Sebbyo-mod2`.
- Create: `src/main/resources/fabric.mod.json`, `src/main/resources/sebbyo_combat.mixins.json`.

- [ ] **Step 1: Copy the wrapper + base gradle files from Sebbyo-mod2**, then set `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
minecraft_version=1.21.11
yarn_mappings=1.21.11+build.1
loader_version=0.17.3
loom_version=1.15.3
fabric_version=0.140.2+1.21.11
mod_version=1.0.0
maven_group=net.sebbyo
archives_base_name=sebbyo-combat
```

- [ ] **Step 2: `build.gradle`** — copy Sebbyo-mod2's, remove owo-lib/modmenu deps (not needed), keep `fabricApi`, add a `src/client` split source set (Loom `splitEnvironmentSourceSets()`) and JUnit 5:

```gradle
plugins { id 'fabric-loom' version "${loom_version}"; id 'java' }
loom { splitEnvironmentSourceSets() }
sourceSets { main { resources.srcDirs += ['src/main/resources'] } }
dependencies {
  minecraft "com.mojang:minecraft:${minecraft_version}"
  mappings "net.fabricmc:yarn:${yarn_mappings}:v2"
  modImplementation "net.fabricmc:fabric-loader:${loader_version}"
  modImplementation "net.fabricmc.fabric-api:fabric-api:${fabric_version}"
  testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
}
test { useJUnitPlatform() }
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
```

- [ ] **Step 3: `fabric.mod.json`** — declare both entrypoints + the split client jar + mixins:

```json
{
  "schemaVersion": 1, "id": "sebbyo_combat", "version": "${version}",
  "name": "Sebbyo Combat", "environment": "*",
  "entrypoints": {
    "main": ["net.sebbyo.combat.SebbyoCombatMod"],
    "client": ["net.sebbyo.combat.client.SebbyoCombatClient"]
  },
  "mixins": ["sebbyo_combat.mixins.json"],
  "depends": { "fabricloader": ">=0.17.3", "minecraft": "~1.21.11", "fabric-api": "*", "java": ">=21" }
}
```

- [ ] **Step 4: `sebbyo_combat.mixins.json`** — empty-ish, `compatibilityLevel: JAVA_21`, packages `net.sebbyo.combat.mixin` (client + common arrays empty for now; mixins added only if a needed hook has no Fabric event).

- [ ] **Step 5: Stub both entrypoint classes** (empty `onInitialize`/`onInitializeClient`) so it compiles.

- [ ] **Step 6: Verify build** — `./gradlew.bat compileJava --no-daemon`. Expected: BUILD SUCCESSFUL. Init git, commit.

```bash
git init && git add -A && git commit -m "chore: scaffold sebbyo-combat (1.21.11 fabric, split source sets)"
```

---

### Task 1: Effect ledger — pure logic + tests

The reward/penalty rules are pure functions on `String` effect ids, decoupled from Minecraft so they unit-test without a game.

**Files:**
- Create: `effect/EffectCatalog.java`, `effect/EffectLedger.java`
- Test: `src/test/java/net/sebbyo/combat/EffectLedgerTest.java`

- [ ] **Step 1: Write `EffectCatalog`** — the lists + cap lookup (caps are *max amplifier*, i.e. level−1):

```java
public final class EffectCatalog {
  // reward id -> max amplifier (level-1). Speed has no entry => unbounded.
  public static final Map<String,Integer> REWARD_CAPS = Map.of(
    "minecraft:health_boost", 3, "minecraft:absorption", 1, "minecraft:regeneration", 0,
    "minecraft:saturation", 1, "minecraft:conduit_power", 1, "minecraft:haste", 1,
    "minecraft:resistance", 1, "minecraft:fire_resistance", 1, "minecraft:invisibility", 1,
    "minecraft:hero_of_the_village", 1);
  public static final String SPEED = "minecraft:speed";
  public static final List<String> REWARDS = List.of(SPEED,
    "minecraft:saturation","minecraft:conduit_power","minecraft:haste","minecraft:regeneration",
    "minecraft:resistance","minecraft:fire_resistance","minecraft:health_boost",
    "minecraft:absorption","minecraft:invisibility","minecraft:hero_of_the_village");
  public static final Map<String,Integer> PENALTY_CAPS = Map.of(
    "minecraft:blindness",0,"minecraft:nausea",0,"minecraft:darkness",0,"minecraft:bad_omen",0,
    "minecraft:glowing",0,"minecraft:jump_boost",1,"minecraft:slow_falling",1,"minecraft:hunger",1,
    "minecraft:slowness",1,"minecraft:mining_fatigue",1,"minecraft:weakness",1);
  public static final List<String> PENALTIES = List.copyOf(PENALTY_CAPS.keySet());
  static int rewardCap(String id){ return REWARD_CAPS.getOrDefault(id, Integer.MAX_VALUE); }
  static int penaltyCap(String id){ return PENALTY_CAPS.getOrDefault(id, 0); }
}
```

- [ ] **Step 2: Write the failing test `EffectLedgerTest`** (drives the API). Use a seeded `Random` for determinism:

```java
class EffectLedgerTest {
  EffectLedger L = new EffectLedger();              // empty ledger for one player
  @Test void speedStacksUnbounded(){
    for(int i=0;i<5;i++) L.applyReward("minecraft:speed");
    assertEquals(4, L.amp("minecraft:speed"));      // 5 kills -> Speed V (amp 4)
  }
  @Test void healthBoostCapsAt4(){
    for(int i=0;i<9;i++) L.applyReward("minecraft:health_boost");
    assertEquals(3, L.amp("minecraft:health_boost"));
  }
  @Test void absorptionCapsAt2(){
    for(int i=0;i<9;i++) L.applyReward("minecraft:absorption");
    assertEquals(1, L.amp("minecraft:absorption"));
  }
  @Test void regenCapsAt1(){
    for(int i=0;i<9;i++) L.applyReward("minecraft:regeneration");
    assertEquals(0, L.amp("minecraft:regeneration"));
  }
  @Test void deathWithRewardsDropsOneLevel(){
    L.applyReward("minecraft:speed"); L.applyReward("minecraft:speed"); // Speed III
    L.onDeath(new Random(1));
    assertEquals(1, L.amp("minecraft:speed"));      // -> Speed II
  }
  @Test void deathLevel1RewardRemoved(){
    L.applyReward("minecraft:haste");               // amp 0
    L.onDeath(new Random(1));
    assertFalse(L.has("minecraft:haste"));
  }
  @Test void deathWithNoRewardsGivesPenalty(){
    L.onDeath(new Random(1));
    assertEquals(1, L.totalPenaltyCount());         // exactly one penalty entry/level added
  }
  @Test void killRemovesOnePenaltyLevel(){
    L.addPenaltyForTest("minecraft:slowness", 1);   // Slowness II
    L.onKill(new Random(1));                          // grants a reward AND peels a penalty
    assertEquals(0, L.amp("minecraft:slowness"));    // -> Slowness I
  }
}
```

- [ ] **Step 3: Run it, expect FAIL** — `./gradlew.bat test --tests EffectLedgerTest`. Expected: compile error / fail (EffectLedger not implemented).

- [ ] **Step 4: Implement `EffectLedger`** — `Map<String,Integer> amps` (id→amplifier). Key methods:

```java
public final class EffectLedger {
  private final Map<String,Integer> amps = new HashMap<>();
  public int amp(String id){ return amps.getOrDefault(id,-1); }   // -1 = absent
  public boolean has(String id){ return amps.containsKey(id); }
  public Map<String,Integer> view(){ return Map.copyOf(amps); }

  public void applyReward(String id){                              // stacking with caps
    int cap = EffectCatalog.rewardCap(id);
    amps.merge(id, 0, (cur,z)-> Math.min(cap, cur+1));            // first grant=0, then +1 up to cap
    if(!amps.containsKey(id)) amps.put(id,0);                      // ensure present on first
  }
  public void onKill(Random rng){
    applyReward(EffectCatalog.REWARDS.get(rng.nextInt(EffectCatalog.REWARDS.size())));
    removeOnePenalty(rng);
  }
  public void onDeath(Random rng){
    List<String> rewards = amps.keySet().stream().filter(this::isReward).toList();
    if(!rewards.isEmpty()) dropOneLevel(rewards.get(rng.nextInt(rewards.size())));
    else gainPenalty(rng);
  }
  private void gainPenalty(Random rng){
    List<String> avail = EffectCatalog.PENALTIES.stream()
        .filter(id-> amp(id) < EffectCatalog.penaltyCap(id)).toList();
    if(avail.isEmpty()) return;
    String id = avail.get(rng.nextInt(avail.size()));
    amps.merge(id,0,(cur,z)->cur+1);
  }
  private void removeOnePenalty(Random rng){
    List<String> held = EffectCatalog.PENALTIES.stream().filter(amps::containsKey).toList();
    if(held.isEmpty()) return;
    dropOneLevel(held.get(rng.nextInt(held.size())));
  }
  private void dropOneLevel(String id){ int n=amps.get(id); if(n<=0) amps.remove(id); else amps.put(id,n-1); }
  private boolean isReward(String id){ return EffectCatalog.REWARDS.contains(id); }
  // test helpers
  int totalPenaltyCount(){ return (int) amps.keySet().stream().filter(id->!isReward(id)).count(); }
  void addPenaltyForTest(String id,int amp){ amps.put(id,amp); }
}
```
*(Note for implementer: fix `applyReward` first-grant logic so a brand-new reward lands at amp 0 — see test `speedStacksUnbounded` expecting amp 4 after 5 applies. Implement as: `amps.merge(id,0,(c,z)->Math.min(cap,c+1))` only AFTER ensuring first insert is 0; simplest correct form: `if(!has(id)) amps.put(id,0); else amps.put(id, Math.min(cap, amp(id)+1));`.)*

- [ ] **Step 5: Run tests, expect PASS.** `./gradlew.bat test --tests EffectLedgerTest`. Fix until green.

- [ ] **Step 6: Commit** — `git commit -am "feat(effect): pure effect ledger with stacking/penalty rules + tests"`.

---

### Task 2: Team store — pure logic + tests + persistence

**Files:**
- Create: `team/Team.java`, `team/TeamStore.java`
- Test: `src/test/java/net/sebbyo/combat/TeamStoreTest.java`

- [ ] **Step 1: Write failing `TeamStoreTest`** covering: create rejects dup name / already-in-team; invite→accept adds member; one-team-per-player; leave by member; **leader leave transfers to oldest remaining**; disband clears; JSON `save(path)`→`load(path)` round-trip preserves teams + member order. (Full test code written here, using `UUID.randomUUID()` and a `@TempDir`.)

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: Implement `Team` (record-ish with ordered `List<UUID> members`, leader first) and `TeamStore`:** in-memory `Map<String,Team> byName`, `Map<UUID,String> byPlayer`, transient `Map<UUID,Invite> invites`. Methods: `create(leader,name)`, `invite(leader,target)`, `accept(target)`, `decline(target)`, `leave(player)` (if leader → promote `members.get(1)` or disband if alone), `disband(leader)`, `sameTeam(a,b)`, `teamOf(uuid)`, `save(Path)`, `load(Path)` (Gson). Validation: name regex `^[A-Za-z0-9_]{1,16}$`, unique, not-already-in-team.

- [ ] **Step 4: Run tests, expect PASS.**

- [ ] **Step 5: Commit** — `git commit -am "feat(team): team store with invites, leader-transfer, json persistence + tests"`.

---

### Task 3: DamageCore + friendly fire (server)

Single interception point. **Pin** the Fabric event first: `javap -cp "$FABRIC" net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents | grep -i allow` (confirm `ALLOW_DAMAGE` arity: `(LivingEntity, DamageSource, float)->boolean`). Confirm `DamageSource.getAttacker()` and `.getSource()` Yarn names via `javap -cp "$JAR" net.minecraft.entity.damage.DamageSource`.

**Files:** Create `DamageCore.java`; modify `SebbyoCombatMod.java` to register the handler.

- [ ] **Step 1:** `DamageCore.resolveAttacker(DamageSource): ServerPlayerEntity|null` — returns `getAttacker()` if a player; else if `getSource()` is a projectile/owner-bearing entity, resolve its owner to a player; else null. Covers melee, arrows/tridents (`ProjectileEntity.getOwner()`), splash potions (`getSource()` owner).
- [ ] **Step 2:** Register `ServerLivingEntityEvents.ALLOW_DAMAGE`: if victim is `ServerPlayerEntity` and `resolveAttacker` is a player on the **same team** (`TeamManager.sameTeam`) → return `false` (cancel, no tag/no reward). Otherwise return `true` and (if attacker!=null and victim is player) hand off to `CombatManager.onPlayerDamaged(victim, attacker)`.
- [ ] **Step 3:** Compile (`compileJava`). In-game verify deferred to Task 9.
- [ ] **Step 4: Commit.**

---

### Task 4: CombatManager — tag, timer, combat-log death

**Files:** Create `combat/CombatState.java`, `combat/CombatManager.java`; modify `SebbyoCombatMod` (register tick + disconnect + pearl/wind-charge hooks).

Pin: `ServerTickEvents.END_SERVER_TICK`, `ServerPlayConnectionEvents.DISCONNECT`, `UseItemCallback` (Yarn), `ServerPlayerEntity` lethal-damage path (`player.damage(ServerWorld, DamageSource, float)` or `player.kill(ServerWorld)` — javap to confirm 1.21.11 signature; also confirm `world.getDamageSources().playerAttack(attacker)` for credit).

- [ ] **Step 1:** `CombatState{int remainingTicks; UUID lastAttacker;}`; `CombatManager` holds `Map<UUID,CombatState>`. `tag(player, attacker)` sets `remainingTicks=400`, records `lastAttacker`. `onPlayerDamaged(victim,attacker)` → `tag(victim,attacker)` **and** `tag(attacker,victim's uuid? no — tag attacker with no lastAttacker change)`; i.e. both parties enter combat (attacker's lastAttacker untouched).
- [ ] **Step 2:** END_SERVER_TICK: for each tracked player decrement; on reaching 0 remove + send `CombatTimerPayload(0)`; else send payload once per 20 ticks (and on any change). Skip creative/spectator (don't tag them — guard in `tag`).
- [ ] **Step 3:** Pearl/wind-charge: `UseItemCallback` — if `stack.isOf(Items.ENDER_PEARL)` or `Items.WIND_CHARGE` and player **already tagged**, reset to 400. (Confirm `Items.WIND_CHARGE` Yarn id.)
- [ ] **Step 4:** DISCONNECT: if tagged → apply lethal damage credited to `lastAttacker` (resolve to online/offline player attack source; if attacker offline, generic player-attack credit) **while the entity is still in the world**, so vanilla drops items+XP at the logout spot; then notify `EffectManager.onKill(lastAttacker)` for the reward. Clear combat state.
- [ ] **Step 5:** Death detection (any cause): on player death, clear their combat state. (Use `ServerLivingEntityEvents.AFTER_DEATH` or `ServerPlayerEvents` — javap to confirm.)
- [ ] **Step 6:** Compile. Commit.

---

### Task 5: EffectManager + EffectStore — apply ledger to players, persist

**Files:** Create `effect/EffectManager.java`, `effect/EffectStore.java`; modify `SebbyoCombatMod`.

Pin: `StatusEffectInstance` infinite constant (`StatusEffectInstance.INFINITE` = -1), `Registries.STATUS_EFFECT.get(Identifier.of(id))` → `RegistryEntry<StatusEffect>`, `player.addStatusEffect(...)`, `player.clearStatusEffects()`, `MinecraftServer.getSavePath(WorldSavePath.ROOT)` for the world folder.

- [ ] **Step 1:** `EffectManager` owns `Map<UUID,EffectLedger>` + a shared `Random`. `applyToPlayer(player)` clears the mod's effects then for each `(id,amp)` in the ledger adds an infinite `StatusEffectInstance`. `onKill(killerUuid)` → `ledger.onKill(rng)` + `applyToPlayer` + `store.save`. `onDeathThen(player)` → after respawn, `ledger.onDeath(rng)` + `applyToPlayer` + save. (Death clears vanilla effects; we re-apply the surviving ledger.)
- [ ] **Step 2:** Hook `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY` (or death event resolving killer) → if both players, `onKill(killer)`. Confirm event name via javap.
- [ ] **Step 3:** Hook respawn (`ServerPlayerEvents.AFTER_RESPAWN` / copy-from) and join (`ServerPlayConnectionEvents.JOIN`) → `applyToPlayer` (so buffs+penalties survive death/restart). On death → schedule `ledger.onDeath` so the penalty/loss resolves and is re-applied at respawn.
- [ ] **Step 4:** `EffectStore` Gson `save/load` `world/sebbyo_rewards.json` shaped `{ "<uuid>": { "<effectId>": amp } }`. Load on `SERVER_STARTED`, save on mutation + `SERVER_STOPPING`.
- [ ] **Step 5:** Compile. Commit.

---

### Task 6: Networking — S2C timer payload + action-bar fallback

**Files:** Create `net/CombatTimerPayload.java`; modify `SebbyoCombatMod` (register payload type + send), `SebbyoCombatClient` (receive).

Pin: `CustomPayload` + `PayloadTypeRegistry.playS2C().register(...)`, `ServerPlayNetworking.send`, `ServerPlayNetworking.canSend(player, id)` (for the vanilla fallback test), `player.sendMessage(Text, true)` for action bar.

- [ ] **Step 1:** `record CombatTimerPayload(int seconds) implements CustomPayload` with an `Id` + `PacketCodec` (`PacketCodecs.INTEGER`). Register on both sides.
- [ ] **Step 2:** `CombatManager` sync: if `ServerPlayNetworking.canSend(player, CombatTimerPayload.ID)` → send payload; else `player.sendMessage(Text.literal("⚔ In combat: "+sec+"s"), true)` once per second.
- [ ] **Step 3:** Compile both source sets (`./gradlew.bat build`). Commit.

---

### Task 7: Client HUD — style-A pill timer

**Files:** Create `client/CombatHudRenderer.java`; modify `SebbyoCombatClient`.

Pin from Sebbyo-mod2: `HudRenderCallback.EVENT`, `DrawContext.fill`, text draw, `client.getWindow().getScaledWidth()/Height()`.

- [ ] **Step 1:** Client stores `remainingSeconds` + a render-time interpolation anchor from the latest payload (`MinecraftClient.execute(...)` to hop to render thread).
- [ ] **Step 2:** `HudRenderCallback`: if `remainingSeconds>0`, draw the style-A pill bottom-centre, above the hotbar/health (`y = scaledHeight - 55`): rounded dark bg, red border, sword glyph or "⚔", a depleting bar (`width * remaining/20`), and `"<n>s"`. Use the Skyz `SkyzRenderHelper.fillRoundedRect`-style helper (port a tiny rounded-rect helper, or use `DrawContext.fill` rectangles).
- [ ] **Step 3:** `./gradlew.bat build`. Commit.

---

### Task 8: Team commands

**Files:** Create `team/TeamManager.java`, `team/TeamCommands.java`; modify `SebbyoCombatMod`.

Pin: `CommandRegistrationCallback.EVENT`, Brigadier `literal/argument`, `EntityArgumentType.player(...)`, `ServerCommandSource`.

- [ ] **Step 1:** `TeamManager` wraps `TeamStore` + the world save path; exposes `sameTeam`, `teamOf`, persistence. Load on `SERVER_STARTED`.
- [ ] **Step 2:** Register `/team create|invite|accept|decline|leave|disband|list|info` mapping to `TeamStore` calls with feedback `Text`. Leader-only guard on `invite`/`disband`. Each mutation saves.
- [ ] **Step 3:** Compile. Commit.

---

### Task 9: Wire-up + integration verification

**Files:** finalize `SebbyoCombatMod.onInitialize` / `SebbyoCombatClient.onInitializeClient` registering everything in order.

- [ ] **Step 1:** Ensure init order: load stores on `SERVER_STARTED`; register DamageCore, CombatManager ticks/hooks, EffectManager hooks, networking, commands; client registers receiver + HUD.
- [ ] **Step 2: `./gradlew.bat build`** → `BUILD SUCCESSFUL`, jar in `build/libs/`.
- [ ] **Step 3: In-game (`./gradlew.bat runServer` + a client, or two dev clients):** run the Section-9 test checklist from the spec (hit→both tagged + HUD; pearl reset; combat-log death + credit + reward; death clears tag; kill grants reward + removes a penalty; buff-less death → penalty; caps; `/team` flow + friendly-fire block + restart persistence).
- [ ] **Step 4: Commit** the final wire-up.

---

## Self-review notes (done)
- **Spec coverage:** combat tag (T3,T4) · timer HUD + fallback (T6,T7) · combat-log death + credit (T4) · death clears tag (T4) · pearl/wind reset (T4) · kill reward + stacking caps (T1,T5) · death loses one buff level / gains penalty (T1,T5) · kill removes penalty (T1,T5) · persistence/restart (T2,T5,T8) · teams + FF + invites + leader transfer (T2,T8,T3). All mapped.
- **Open verification:** all Minecraft-coupled Yarn names are pinned via javap at the start of each task (per the API-pinning rule) — these are the only "to confirm" items and are intentionally resolved at implementation time against the real jar, exactly as the Skyz port was done.
