package com.terraformersmc.modmenu.api;

/**
 * Compile-time copy of Mod Menu's public API (only what MineMeBoost uses). It is NOT packaged in the jar:
 * when Mod Menu is installed, its real classes are used; when it isn't, this entrypoint is never loaded.
 */
public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return screen -> null;
    }
}
