# MineBoost FPS measurements (2.3.0)

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
