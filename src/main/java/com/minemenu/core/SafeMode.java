package com.minemenu.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Crash-loop protection. Every start writes a counter; reaching the title screen (or a world) clears it.
 * If the game didn't get that far twice in a row, the third start keeps every hack off and says why.
 */
public final class SafeMode {
    private static final Logger LOGGER = LoggerFactory.getLogger("MineMenu");
    private static final Path FILE = CheatConfig.DIR.resolve("safemode-minemenu.txt");
    private static boolean active;
    private static boolean confirmed;

    private SafeMode() {
    }

    /** Returns true when this start should be a safe one (all hacks off). */
    public static boolean onStart() {
        int failed = 0;
        try {
            if (Files.exists(FILE)) {
                failed = Integer.parseInt(Files.readString(FILE).trim());
            }
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, String.valueOf(failed + 1));
        } catch (Exception ignored) {
        }
        active = failed >= 2;
        if (active) {
            LOGGER.warn("[Safe Mode] The game didn't start properly {} times in a row: all MineMenu hacks start OFF this time.", failed);
        }
        return active;
    }

    public static boolean isActive() {
        return active;
    }

    public static void tick(MinecraftClient client) {
        if (confirmed || !(client.currentScreen instanceof TitleScreen || client.world != null)) {
            return;
        }
        confirmed = true;
        try {
            Files.deleteIfExists(FILE);
        } catch (Exception ignored) {
        }
        if (active) {
            Guard.toast("MineMenu Safe Mode", "The game crashed while starting twice, so all hacks are off. Turn them back on in the menu.");
        }
    }
}
