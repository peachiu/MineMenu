# MineMeBoost 3.5.0: test results

Run with the in-game test suite (`./gradlew runClientTests`, see the README): the real production jar in a Minecraft
1.21.1 client under Xvfb with software rendering.

| Run | Mods | Result |
|---|---|---|
| Full suite (`-Psuite=full`) | Fabric API only | 86/87; the failing check expected the old, longer Portuguese label and was updated |
| Full suite | + Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling | 86/87 (same label check), no mixin errors |
| Iris suite | + Iris and Mod Menu | 3/3: Render Scale locks itself, Mod Menu button works, hacks run |
| Multiplayer suite | vanilla 1.21.1 dedicated server | 8/8: cheats stay off, nothing sent, Render Scale works |
| FPS (`-Psuite=ab`, `-Psuite=quick`) | Fabric API only | see [fps-measurements.md](../../mineboost/docs/fps-measurements.md) |
| Fabric GameTests (`./gradlew runGametest`) | Fabric API only | 6/6 |

New in 3.5, checked in the full suite:

- **Sharpen (CAS)**: the shader loads; edge contrast at Render Scale 75% goes from 3.87 to 9.57 (100% strength).
- **Presets from A/B results**: Toaster/Potato/Intel iGPU pick 50/75/100% from three made-up A/B results.
- **Impostors**: 4 kinds of mob photographed from 8 sides; turning it off frees every picture.
- **Undo for vanilla structures**: an igloo changed 152-456 blocks (it lands at a different height each run); after Undo, 0 blocks differ.
- **Schematics browser**: lists the saved schematic; preview with size 5x5x3 and 75 blocks.
- **Portuguese (Portugal)**: switching the game language translates hacks, options and menu entries; screenshots of every page at 1366x768, GUI Scale 2, in the three themes; switching back to English works.
- **Config migration**: a 3.3 `mineboost.json` keeps its values and gets `configVersion` 2 with the new defaults.
