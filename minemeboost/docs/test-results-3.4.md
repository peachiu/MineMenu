# MineMeBoost 3.4.0: test results

Run on the real production jar (Minecraft 1.21.1, Fabric Loader 0.17.3, Fabric API 0.116.7) under Xvfb with
software rendering, driven by a temporary in-game test script (not shipped).

| Run | Mods | Result |
|---|---|---|
| Full test (every new hack, build tools, fun, utility, UI, Safe Mode, config migration, Lag Doctor, Render Scale) | Fabric API only | 71/72 passed; the failing check (Bow Aimbot) was a test timing problem and passed 3/3 when re-run |
| Full test | + Sodium 0.8.13, Lithium 0.15.4, FerriteCore 7.0.3, ImmediatelyFast 1.6.15, EntityCulling 1.12.2 | **72/72 passed**, no mixin errors |
| Iris test (Render Scale lock, menus, hacks, Mod Menu button) | + Iris 1.8.14-beta.1, Mod Menu 11.0.5 and the five above | 3/3 passed |
| Multiplayer (vanilla 1.21.1 dedicated server, online-mode off) | Fabric API only | 8/8 passed: cheats stay off (Fly, Freecam, Full Bright, Ore Vision, Chest ESP, Health Bars), no MineMenu network code exists, Render Scale works |
| FPS A/B (in-game A/B tool + extra measurements) | Fabric API only | See [fps-measurements.md](../../mineboost/docs/fps-measurements.md) |
| Fabric GameTests (`./gradlew runGametest`) | Fabric API only | 6/6 passed |

Checked in the full test, among others:

- Config migration: a 3.3 `mineboost.json` keeps its values and gets `configVersion` 1 with defaults for the new options; `cheats.json` remembers enabled hacks.
- Safe Mode: a hack that throws is switched off and the game keeps running; two failed starts in a row → the next start is safe.
- Render Scale 75% with a glowing mob (outline lines up), fullscreen toggle, window resize.
- Morph on 6 mobs (spider climbs 2.8 blocks, blaze shoots), dying turns you back.
- Fill 64×64×64 (262 144 blocks): the game keeps running (worst frame 71-138 ms depending on load), Undo restores it.
- Memory over a whole run: 395 → 402 MB (vanilla run), 489 → 333 MB (compat run) after GC: no growth.
