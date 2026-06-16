# Sebbyo Combat

A DonutSMP-style PvP mod for **Minecraft 1.21.11 (Fabric)**. Server-authoritative
combat tagging with a combat-log death penalty, a persistent kill-reward / death-
penalty effect system, and a team / friendly-fire system — all in **one jar** that
runs on the server and renders a styled combat-timer HUD on modded clients (with an
action-bar fallback for vanilla clients).

> Works on a dedicated server **and** on a singleplayer world opened to friends
> (vanilla "Open to LAN" or Essential's "invite friends") — those run an integrated
> server, which enforces every rule. Only the **host** needs the mod; friends get the
> action-bar timer without it and are still fully enforced.

---

## Features

### Combat tag (20s)
- Taking **or** dealing player-inflicted damage (melee, arrows/tridents, splash
  potions) tags **both** players for 20 seconds.
- A styled HUD pill shows the countdown bottom-centre; vanilla clients see an
  action-bar countdown instead.
- **Ender pearl / wind charge** while tagged → resets the timer to 20s.
- **Dying** while tagged → tag cleared.
- **Combat logging** (disconnecting while tagged) → you **die instantly at your
  logout spot** (items + XP drop there), credited to the last player who hit you;
  you respawn on reconnect.
- Creative / spectator players are never tagged.

### Kill rewards & death penalties (persistent, survive restarts)
- A **player kill** grants one random positive effect (infinite duration) **and**
  removes one level of a random penalty you're carrying.
- **Dying with buffs** → lose one level of one random buff.
- **Dying with no buffs** (any cause) → gain one level of a random penalty;
  penalties keep stacking while you're buff-less.

| Reward | Max level | | Penalty | Max level |
|---|---|---|---|---|
| Speed | ∞ | | Jump Boost, Slow Falling, Hunger, Slowness, Mining Fatigue, Weakness | 2 |
| Health Boost | 4 | | Blindness, Nausea, Darkness, Bad Omen, Glowing | 1 |
| Absorption | 2 | | | |
| Saturation, Conduit Power, Haste, Resistance, Fire Resistance, Invisibility, Hero of the Village | 2 | | | |
| Regeneration | 1 | | | |

### Parties & friendly fire
- `/party create <name>` · `/party invite <player>` · `/party accept` · `/party decline`
  · `/party leave` · `/party disband` · `/party list` · `/party info` · `/party msg <text>`
- Named `/party` (not `/team`) so it never clashes with vanilla's op-only `/team`
  scoreboard command — this one works for everyone and has no scoreboard side effects.
- Teammates **cannot damage each other** — melee, projectiles, and splash potions
  are all blocked, and a blocked hit never combat-tags anyone.
- One party per player. Invites must be accepted. Only the leader can invite or
  disband. When the leader leaves, leadership passes to the **oldest member**.
- Teams persist to `<world>/sebbyo_teams.json`; effects to `<world>/sebbyo_rewards.json`.

---

## Installation

Requires **Fabric Loader ≥ 0.17.3** and **Fabric API** on Minecraft **1.21.11**.

1. Put `sebbyo-combat-1.0.1.jar` and Fabric API in the **host's** `mods/` folder
   (the dedicated server, or the player who opens the world to friends).
2. Friends may optionally install it too for the styled HUD timer — without it they
   get an action-bar timer and are still fully governed by the rules.

## Building

```bash
./gradlew build          # -> build/libs/sebbyo-combat-1.0.1.jar
./gradlew test           # pure-logic unit tests (effect stacking, teams)
./gradlew runServer      # dev dedicated server
./gradlew runClient      # dev client
```

## Tech

Fabric Loader 0.17.3 · Fabric API 0.140.2+1.21.11 · Yarn 1.21.11+build.1 · Loom
1.15.3 · Java 21. Design + implementation notes in
[`docs/superpowers/`](docs/superpowers/).
