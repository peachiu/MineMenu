# MineBoost

An **FPS boost menu** for **Minecraft Java 1.21.1** (Fabric). Press **`.`** (period) in game to open it.

![The MineBoost menu](docs/menu.png)

- **One-click presets:** Potato, Low, Balanced, High and Ultra.
- **The graphics settings that matter for FPS**, all on one screen, biggest impact first.
- **Extra boosts vanilla doesn't have:** turn off all particles, hide rain and snow, limit how far mobs, chests and signs are drawn, freeze animated textures, and drop FPS when the game is in the background.
- **FPS overlay** with average FPS and 1% low, so you can see what each setting does.

Works in singleplayer and on servers. It only changes what your own game draws.

## Install

1. Install the [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft **1.21.1**.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1 and put it in your `.minecraft/mods` folder.
3. Download `mineboost-1.0.1.jar` from the [Releases page](https://github.com/peachiu/MineMenu/releases) and put it in the same `mods` folder.
4. Start Minecraft with the Fabric 1.21.1 profile, join a world and press **`.`**.

You can change the key in **Options → Controls → Key Binds → MineBoost**.

## How much faster?

It depends on your PC and on what's on screen. Measured in a test world with 150 chickens in view, on a PC with no graphics card (software rendering, so the numbers are low, but the comparison is fair):

| Preset | FPS | Compared to Ultra |
|---|---|---|
| Ultra | 5.2 | 1× |
| High | 5.2 | 1× |
| Balanced | 6.3 | 1.2× |
| Low | 12.4 | 2.4× |
| Potato | 16.8 | **3.2×** |

No mod can promise a fixed number like "50× more FPS". The biggest gains come from render distance, the graphics mode, and cutting what's drawn (entities, particles, weather). MineBoost puts all of that in one place. For even more FPS, MineBoost also works alongside [Sodium](https://modrinth.com/mod/sodium), which rewrites Minecraft's renderer.

## Presets

| | Potato | Low | Balanced | High | Ultra |
|---|---|---|---|---|---|
| Render distance | 4 | 6 | 10 | 16 | 24 |
| Simulation distance | 5 | 6 | 8 | 12 | 16 |
| Graphics | Fast | Fast | Fancy | Fancy | Fancy |
| Smooth lighting | Off | Off | On | On | On |
| Clouds | Off | Off | Fast | Fancy | Fancy |
| Particles | Minimal | Decreased | Decreased | All | All |
| Entity shadows | Off | Off | On | On | On |
| Biome blend | 0 | 1 | 2 | 3 | 5 |
| Mipmaps | 0 | 1 | 2 | 4 | 4 |
| Entity distance (vanilla %) | 50% | 75% | 100% | 100% | 150% |
| Menu blur | 0 | 0 | 3 | 5 | 5 |
| **Entity distance (MineBoost)** | 32 blocks | 48 blocks | 80 blocks | Unlimited | Unlimited |
| **Chests & signs** | 16 blocks | 32 blocks | 48 blocks | Normal | Normal |
| **Disable all particles** | On | Off | Off | Off | Off |
| **Hide rain & snow** | On | On | Off | Off | Off |
| **Animated textures** | Off | On | On | On | On |
| **Background FPS** | 5 | 10 | 15 | 30 | 30 |

Every preset also sets **Chunk Updates** to *Threaded*, so building chunks never freezes a frame. Presets don't touch Max Framerate, VSync, GUI scale or brightness, because those are personal.

The active preset gets a ✔. Change any setting afterwards and the ✔ goes away, since you now have your own mix.

## MineBoost settings

| Setting | What it does |
|---|---|
| Entity Distance | Mobs, animals, dropped items and item frames farther than this aren't drawn. Players, the Ender Dragon and the Wither are always drawn. One of the biggest boosts near farms and mob grinders. |
| Chests & Signs | Chests, signs, banners, heads and beds farther than this aren't drawn. Great for big bases and storage rooms. |
| Disable All Particles | No particles at all, including ones already on screen. Stronger than vanilla's Particles: Minimal. |
| Hide Rain & Snow | Rain and snow aren't drawn. It still rains in the world (crops grow, fires go out); you just don't see it. |
| Animated Textures | Off freezes water, lava, fire and portal animations. Small boost. |
| Background FPS | FPS limit when Minecraft isn't the window you're using. Minimized always drops to 1 FPS. Frees your PC for OBS, Discord or your browser. |
| FPS Overlay | FPS, average FPS and 1% low in the top-right corner. The 1% low shows how bad the stutters are. |

Settings are saved to `config/mineboost.json`. Vanilla settings are saved to `options.txt` as usual.

| Rain on | Hide Rain & Snow + FPS Overlay |
|---|---|
| ![Rain on](docs/rain-on.png) | ![Rain hidden, FPS overlay on](docs/rain-hidden.png) |

## Building

You need **Java 21**.

```bash
cd mineboost
./gradlew build
```

The jar ends up in `mineboost/build/libs/mineboost-1.0.1.jar`.

### Making a release

1. Set `mod_version` in `mineboost/gradle.properties`.
2. Write the release notes in `mineboost/release-notes/v<version>.md`.
3. Commit and push, then go to **Actions → Release MineBoost → Run workflow**. It creates the tag `mineboost-v<version>` and publishes the jar.
