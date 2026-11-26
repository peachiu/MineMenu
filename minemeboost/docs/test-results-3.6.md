# MineMeBoostXray 3.6.0: test results

Run with the in-game test suite (`./gradlew runClientTests`, see the README): the real production jar in a Minecraft
1.21.1 client under Xvfb with software rendering (Mesa llvmpipe, no GPU).

| Run | Mods | Result |
|---|---|---|
| MineXray suite (`-Psuite=xray`) | Fabric API only | RESULT_XRAY |
| MineXray suite | + Sodium, Lithium, ImmediatelyFast, EntityCulling | RESULT_XRAY_COMPAT |
| Multiplayer suite (`-Psuite=mp`) | vanilla 1.21.1 dedicated server | RESULT_MP |
| Full suite (`-Psuite=full`, 3.4 + 3.5 + 3.6 checks) | Fabric API only | RESULT_FULL |
| MineXray cost (`-Psuite=xraym`) | Fabric API only | table below |

## What the MineXray suite checks

- **Base:** `config/minemeboost/minexray.json` is written; 9 built-in presets; keys X (Classic) and G (Ore ESP); the Command Palette finds "xray diamonds".
- **Migration:** MineMenu's Ore Vision switches Ore ESP on, and switching Ore ESP off in MineXray switches Ore Vision off. The same goes for Chest ESP / Block ESP and Mob Glow / Entity ESP. Mob Glow no longer puts the Glowing effect on mobs.
- **Ore ESP:** turned on with its key; a 2x2x2 diamond cube is found as **one vein of 8**, and its outline is 24 segments (12 edges of 2 blocks) instead of 96. Breaking one block updates it to 7 within 1 s, placing it back to 8. Few draw calls (2 in the test scene).
- **Block ESP:** chest and spawner found. **Fluid View:** lava found and drawn. **Cave Finder:** cave air found and drawn.
- **Lava Warning:** fires while "mining" a block with lava under it.
- **Entity ESP:** zombie and cow drawn.
- **Classic X-Ray** (without Sodium): on means full bright and chunk culling off. On screen, the stone floor goes from 98% stone-grey pixels to 15%; at 30% opacity stone is see-through; switching it off brings culling, brightness and the floor back, and no rebuild is left hanging. **With Sodium:** the key is refused with "Classic X-Ray isn't available with Sodium, use Ore ESP", and nothing changes.
- **Presets:** Diamonds Only drops gold; a share code exports and imports; a broken code is refused.
- **Block editor:** the looked-at block is added; `#minecraft:diamond_ores` resolves to 2 blocks; iron ore brings deepslate iron ore.
- **Tab:** all 7 sub-pages open at 1366x768, GUI Scale 2 (screenshots in the run folder).
- **Scanner:** a 128-block scan finishes while the game keeps running (1% low 10 FPS on the software renderer). It lists the diamond vein. Mark as waypoint saves it, Show highlights it, Teleport lands in safe air at the vein, and Mine gives the 8 diamonds.
- **Dimension:** going to the Nether switches to Netherite Hunt; back home it's All Ores again (and the cache is cleared each time).
- **Safe Mode:** a forced error in Fluid View turns only that mode off. **Profiles** save and load MineXray modes.
- **All off:** no scan data and no GPU buffers are kept.

## MineXray cost per mode

Measured underground (y = -20, spectator, view distance 8) in a normal world. Each mode: off/on three times, 6 s per measurement, after it had time to scan (Ore ESP 128: 25 s).

| Mode | Avg FPS off → on | 1% low off → on |
|---|---|---|
| Ore ESP 32 | 17.4 → 16.6 (-4%) | 11.5 → 11.6 (+1%) |
| Ore ESP 64 | 18.6 → 16.8 (-10%) | 13.7 → 12.5 (-8%) |
| Ore ESP 128 | 18.9 → 17.3 (-9%) | 12.7 → 10.5 (-18%) |
| Block ESP | 18.3 → 17.9 (-2%) | 12.8 → 12.4 (-3%) |
| Cave Finder | 17.8 → 18.1 (+1%) | 12.5 → 13.3 (+7%) |
| Fluid View | 18.9 → 19.7 (+4%) | 12.8 → 13.9 (+9%) |
| Classic X-Ray | 18.9 → 34.1 (+80%) | 11.5 → 24.1 (+109%) |

The software renderer runs everything on the CPU, so these numbers say more about the CPU side (scanning, building
geometry) than about a real GPU. Differences of ±5% are within the noise of this machine. Classic X-Ray gets
*faster* because most blocks aren't drawn at all. **Not measured on the i3-1005G1** (there's no such machine
here): to get the real numbers on a PC, open MineXray > Settings > *Measure the cost (A/B)*. It takes about two
minutes and writes `config/minemeboost/xray-cost-results.md`.
