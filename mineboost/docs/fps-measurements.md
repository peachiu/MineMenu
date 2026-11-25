# MineBoost FPS measurements (2.5.0)

## 2.5.0: Sharpen, Impostors, presets from your A/B test

Same test PC (no graphics card, Mesa llvmpipe software rendering, 4 CPU cores), real MineMeBoost 3.5.0 jar, measured
by the in-game test suite (`./gradlew runClientTests -Psuite=ab` and `-Psuite=quick`).

| Option | Scene | Avg FPS off → on | 1% low off → on | Verdict |
|---|---|---|---|---|
| **Impostors 24**, *new* | 150 armored zombies 28-44 blocks away, render distance 2 (mobs are the cost) ³ | 102.0 → 113.1 (**+11%**) | 41.3 → 63.2 (**+53%**) | Kept, **off by default**: far mobs stop animating, you choose |
| Impostors 24 | the 2.4 A/B scene (terrain is the cost) | 17.4 → 17.7 (+2%) | 10.1 → 12.0 | Within noise when mobs aren't the bottleneck |
| **Sharpen 50%** at Render Scale 75%, *new* | 150 armored zombies ³ | 100.1 → 82.2 (**−18%**) | 47.0 → 44.0 | **Kept at 50%** (Render Scale itself is off by default): on this CPU renderer every texture read is expensive; on a graphics card the 9 reads per pixel cost a fraction of a millisecond. Set it Off for maximum FPS |
| Render Scale 75% (with Sharpen 50%) | the A/B scene | 10.5 → 13.5 (**+28%**) | 6.2 → 7.6 | Net gain even with the sharpening |
| Sharpen, picture quality | edge contrast of the middle of the screen at 75% | 3.89 → 9.65 (100% strength); 6.21 → 7.28 (50%) | | The 75% picture looks as crisp as 100% |
| Far Entity Ticks (beta) | the A/B scene ³ | 17.2 → 17.2 (0%) | 14.3 → 12.4 | Still no gain → off by default |
| Smooth Fog Edge | the A/B scene ³ | 17.2 → 17.2 (0%) | 13.3 → 13.4 | Looks only, no cost |

³ = off/on three times. The other boosts in the 2.5 A/B table stayed within ±6% in its terrain-heavy scene (render distance −4: +79%).

**Presets from your A/B test**: after an A/B test, Toaster uses 50% if that gained ≥15% (and 5% more than 75%), else 75% if
that gained ≥8%; Potato and Intel iGPU use 75% if that gained ≥8%; otherwise 100%. Checked in the test suite with
three made-up results (big gains, no gains, no A/B test).

---

## 2.4.0

Same test PC as below (no graphics card, Mesa llvmpipe software rendering, 4 CPU cores, 854×480), real
MineMeBoost 3.4.0 jar. Each line is **off 4 s / on 4 s, twice** (the in-game **A/B test**, Tools → A/B all boosts)
or **off 5 s / on 5 s, three times** (marked ³). Scene: 98 zombies in diamond armor 26-44 blocks away, 40 chests,
150 dropped items, 300 cows behind you 25-40 blocks away, render distance 8. This scene is limited by the CPU
(drawing terrain alone costs 56% of the frame, see the Lag Doctor screenshot), so most options move FPS by less
than the noise (about ±5%).

| Option | Avg FPS off → on | 1% low off → on | Verdict |
|---|---|---|---|
| **Render Scale 75%**, *new* | 10.7 → 12.6 (**+17%**) | 7.1 → 10.3 (+45%) | Kept, **off by default** (100%): it makes the world blurrier, you choose |
| **Render Scale 50%**, *new* | 12.2 → 12.8 (+5%) | 9.4 → 9.8 | (same option) |
| Render Scale, simple scene ³ | 100% 38.6 · 75% 40.5 (+5%) · 50% 44.4 (**+15%**) | | On a real iGPU, which is limited by pixels, the gain is usually bigger than on this CPU renderer |
| **Dynamic Render Scale**, *new* | goes down 5% a second below the FPS target (10% when far below), back up after 3 s above | | Kept, off by default |
| **Section Culling**, *new* | 12.4 → 11.5 (−7%) | 10.1 → 8.7 | **No gain → off by default.** Smart Culling (on by default) already hides the same entities; with Sodium it's switched off |
| **Far Entity Ticks (beta)**, *new* ³ | 12.2 → 11.9 (−2%) | 10.6 → 9.8 (−8%) | **No gain → off by default.** Animating far mobs is cheap compared with drawing |
| **Smooth Fog Edge**, *new* ³ | 12.5 → 12.1 (−3%) | 10.1 → 10.1 | Visual only (chunks fade in instead of popping), costs nothing measurable. Off by default |
| **Battery Saver**, *new* | caps FPS at 30 and Render Scale at 70% | | Can't be measured in this scene (already under 30 FPS). Off by default |
| **Max Sounds**, *new* | | | Not measurable here (no sound card on the test PC). Off by default |
| Minimap (MineMenu) ³ | 68.2 → 66.4 · 22.2 → 23.3 · 17.1 → 17.2 | | **No measurable cost** (three runs: −3%, +5%, +1%) |
| Max Entities 50 | 11.6 → 12.6 (+9%) | 8.9 → 10.4 | Kept |
| Graphics: Fast *(vanilla)* | 11.0 → 12.0 (+10%) | 8.0 → 8.1 | |
| Render distance −4 *(vanilla)* | 10.8 → 23.7 (**+119%**) | 7.5 → 14.2 | The biggest lever in a terrain-heavy scene |

The other boosts in the A/B table (Simple Far Mobs, Entity Distance, Chests & Signs, Item Distance, Simple Items,
Smart Culling, Max Particles, Animated Textures, Clouds, Entity Shadows, Smooth Lighting) stayed within ±5% in this
terrain-heavy scene; their 2.3 numbers below were measured in an entity-heavy scene, where they matter.

Other 2.4 features, checked for what they promise:

| Feature | Result |
|---|---|
| **Lag Doctor** | 10 s report: the per-frame costs add up to one frame (e.g. 22.9 ms terrain of a 48.7 ms frame); each finding has a one-click fix. |
| **Ore Vision / Chest ESP background scan** | Scanning moved off the game thread (snapshots of the chunk sections, read by one background thread). Finds all 5 test diamonds and the chest; breaking one ore rescans only that 16×16×16 section. |
| **Copy/Paste, Fill 64×64×64** (262 144 blocks) | Built 4096 blocks per tick: the game keeps running (worst frame 71-138 ms depending on the machine's load, never a freeze), Undo restores it. |
| **Memory** | A whole test run (every hack, Minimap, ESP, Chaos Mode, a 64³ fill and undo): 395 MB used at the start, 402 MB at the end after a GC. No growth. |
| **Iris** | Render Scale switches itself off (shaders have their own resolution), with the reason in the tooltip. |

---

## 2.3.0

These are the numbers behind the **Impact:** line in each MineBoost tooltip.

## How it was measured

- The real mod jar (MineMeBoost 3.3.0), Minecraft 1.21.1 with Fabric, no other mods.
- A test PC **without a graphics card** (Mesa llvmpipe software rendering, 4 CPU cores, 854×480).
  The FPS values are low, but the *difference* between on and off is what matters.
  On a real GPU, like Intel UHD, the percentages are usually smaller for options that cut drawing
  work and similar for options that cut CPU work (entities, items).
- One busy scene: **98 zombies in full diamond armor with a sword and shield** 26-44 blocks away,
  **40 chests** 8-24 blocks away and **150 stacks of dropped items** 5-12 blocks away, render distance 8.
- Each option: **off 5 s, on 5 s, three times in a row** (so warm-up and background noise affect both
  sides equally), then the averages of the three runs.

## Results

| Option (value) | Avg FPS off → on | 1% low off → on | Verdict |
|---|---|---|---|
| **Simple Far Mobs** (16 blocks), *new* | 24.1 → 31.5 (**+31%**) | 14.6 → 21.5 (**+48%**) | Kept |
| Max Entities (50) | 26.1 → 35.8 (**+37%**) | 18.0 → 24.2 (+34%) | Kept |
| Entity Distance (24 blocks) | 26.3 → 34.1 (**+30%**) | 17.7 → 24.0 (+36%) | Kept |
| Item Distance (6 blocks) | 26.9 → 29.6 (+10%) | 17.4 → 19.7 (+14%) | Kept |
| Simple Items | 26.1 → 27.3 (+4%) | 17.6 → 18.4 (+5%) | Kept (small) |
| Chests & Signs (16 blocks) | 26.0 → 26.5 (+2%) | 16.1 → 17.4 (+8%) | Kept (small; only helps with hundreds of chests) |
| Entity Shadows off *(vanilla)* | 28.3 → 27.5 (−3%) | 18.6 → 19.1 (+3%) | No measurable gain |
| Smooth Chunk Loading, *new* | 54.0 → 53.8 (0%) | 18.6 → 16.5 (−11%) | **Removed**: no gain while flying through new terrain |

Other new features, checked for what they promise rather than for FPS:

| Feature | Result |
|---|---|
| **Particle priority** (Max Particles 250) | 600 far particles, then 100 near ones: total stays at 250 and **all 100 near ones** are shown (the farthest are removed first). Before, the near ones were all rejected. |
| **Idle Mode** (10 FPS after 15 s) | 10.0 FPS while idle; a key press brings full FPS back on the next frame. |
| **Intel iGPU preset** | Applies and shows as active (✔). |
| **Benchmark compare** | The second run shows "vs last run: avg A → B (±%), 1% low ..." and the settings that changed in between. |

## Not added, and why

- **Animation LOD** (animating far mobs less often): skipping animation frames makes far mobs stutter,
  and the cost is mostly in drawing them, not animating them. Simple Far Mobs cuts the drawing instead.
- **Fog off**: fog is free in 1.21.1 (it's part of the same shader), so turning it off gains nothing.
  Clouds and entity shadows were already off in Toaster and Potato.
- **Smooth Chunk Loading** (limit chunk uploads per frame below the FPS target): measured, no gain (see above).

## Other performance mods

MineBoost checks for **Sodium, Lithium, FerriteCore, ImmediatelyFast and EntityCulling** when the game starts
and shows what it found at the top of the MineBoost screen:

- **EntityCulling** already hides things behind walls, so MineBoost's **Smart Culling** is switched off
  (greyed out, with the reason) instead of doing the same work twice.
- **Sodium** has its own chunk renderer, so the vanilla *Chunk Builder* setting is hidden.
- Lithium, FerriteCore and ImmediatelyFast speed up other things (game logic, memory, HUD drawing); no conflicts.

Tested by starting the game with **all five installed together with MineMeBoost 3.3.0**: no crash and no
mixin errors, all five detected, Smart Culling switched off automatically, and every MineMenu hack test
still passed. With Sodium installed, Simple Far Mobs still gave **+13% FPS** (36.8 → 41.4) in the same
scene: Sodium makes everything faster, so there's less left to save, but drawing armor twice is still work.
