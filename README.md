# MineMenu

A **singleplayer-only** fun menu for **Minecraft Java 1.21.1** (Fabric). Fly, turn on god mode, one-shot mobs, call down lightning, rain TNT, freeze the world and more. Made for messing around and filming videos.

![The MineMenu menu](docs/menu.png)

> MineMenu only works in your own singleplayer worlds. It refuses to work on multiplayer servers, and it switches itself off if you open your world to LAN.

## Install

1. Install the [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft **1.21.1**.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1 and put it in your `.minecraft/mods` folder.
3. Put `minemenu-1.0.0.jar` in the same `mods` folder (see [Building](#building) for how to get it).
4. Start Minecraft with the Fabric 1.21.1 profile and open a singleplayer world.
5. Press **Right Shift** to open the menu.

## What's in the menu

Hover any button in the game to see what it does.

### Toggles

| Toggle | What it does |
|---|---|
| Fly | Creative-style flight, even in survival. Double-tap jump to take off. |
| God Mode | No damage, no hunger, no drowning, no burning. (The void and `/kill` still work so you can't get stuck.) |
| One-Hit Kill | +1000 attack damage. Everything dies in one hit. |
| Speed | Walk and fly 3× faster. |
| Super Jump | Jump really high. No fall damage while it's on. |
| Night Vision | See in the dark and underwater. |
| Insta-Mine | Break blocks almost instantly. |
| Long Reach | Reach blocks and mobs from about 20 blocks away. |
| Mob Aura | Hostile mobs within 8 blocks of you die automatically. |
| Freeze World | Freezes all mobs, projectiles, redstone and so on. You can still move around. |
| Size | Cycle between Normal, **Giant** (4×) and **Tiny** (¼). |
| Show HUD | Shows the list of active cheats in the top-left corner. Turn it off for clean footage. |

### Actions

| Action | What it does |
|---|---|
| Kill Hostile Mobs | Kills every hostile mob within 64 blocks. |
| Kill ALL Mobs | Kills every mob within 64 blocks. Your tamed pets, armor stands and you are left alone. |
| Smite | Lightning bolt where you're looking. |
| Explode | TNT-sized explosion where you're looking. It breaks blocks, so turn on God Mode first. |
| TNT Rain | Drops a ring of 12 lit TNT around you. |
| Teleport | Teleports you to the block you're looking at (up to 160 blocks away). |
| Launch Me | Shoots you into the sky. |
| Heal & Feed | Full health and hunger. |
| Set Day / Set Night | Changes the time. |
| Clear Weather / Thunderstorm | Changes the weather. |

### Keybinds

Change these in **Options → Controls → Key Binds → MineMenu**.

| Key | Default |
|---|---|
| Open MineMenu | Right Shift |
| Toggle Fly | not bound |
| Toggle God Mode | not bound |
| Kill Hostile Mobs | not bound |

## Notes

- The menu doesn't pause the game, so you can watch things happen behind it.
- Toggles stay on when you switch worlds. Everything is undone before the world saves, so nothing sticks to your save file (no permanent flying or giant size if you remove the mod).
- Explosions and TNT break blocks. Make a backup or use a test world if you care about your build.

## Building

You need **Java 21**.

```bash
./gradlew build
```

The mod jar ends up in `build/libs/minemenu-1.0.0.jar`. Ignore the `-sources.jar` file.

To try it straight from the source code, run `./gradlew runClient`.

## How it works (for the curious)

MineMenu is a client mod. When you play singleplayer, Minecraft runs a small server inside your game, and MineMenu makes its changes there, on your player only. That is also how it knows when you're *not* in singleplayer: there is no local server, or the server has been opened to LAN, so it does nothing.

- `MineMenuClient`: keybinds, the HUD, and hooks into the local server.
- `MineMenuScreen`: the menu.
- `ServerCheats`: everything that changes the game.
- `CheatState` / `Feature`: which toggles are on.
