package com.minexray;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * What MineXray gets from the mod it runs in. On its own (the MineXray jar) these are plain defaults; inside
 * MineMeBoostXray they're set to MineMenu's and MineBoost's (themes, waypoints, FPS Target, the A/B cost test,
 * the tab row...) before {@link MineXrayClient#init()} runs.
 */
public final class XrayHost {
    /** Where minexray.json and xray-presets/ go. */
    public static Path configDir = FabricLoader.getInstance().getConfigDir().resolve("minexray");
    /** FPS to aim for (Adaptive range); 0 = none set (then 30). */
    public static IntSupplier fpsTarget = () -> 0;
    /** First start without a minexray.json: take over settings from elsewhere (MineMenu's old toggles). */
    public static Consumer<XrayConfig> firstStart = config -> {
    };
    /** Saves a waypoint (name, dimension, x, y, z) for the host player; returns the slot or -1. Null = no waypoints. */
    public static WaypointSaver waypoints;
    /** Starts the A/B cost test; null = not available (MineBoost isn't there). */
    public static Consumer<MinecraftClient> costTest;
    public static BooleanSupplier costTestRunning = () -> false;
    /** The tab row above the screen (MineMenu / MineBoost / MineXray); null = none. */
    public static Function<Screen, List<ButtonWidget>> tabs;
    /** Menu look. */
    public static Theme theme = new Theme() {
    };
    /** Called every client tick while MineXray may run (MineMeBoostXray keeps its old toggles in step here). */
    public static Runnable tick = () -> {
    };

    private XrayHost() {
    }

    public interface WaypointSaver {
        int save(String name, String dimension, double x, double y, double z);
    }

    public interface Theme {
        default void overlay(DrawContext context, int width, int height) {
        }

        default void panel(DrawContext context, int x1, int y1, int x2, int y2) {
            context.fill(x1, y1, x2, y2, 0x50000000);
            context.drawBorder(x1, y1, x2 - x1, y2 - y1, 0x6055FFFF);
        }

        default int accent() {
            return 0x55FFFF;
        }
    }

    /** True when running as part of MineMeBoostXray. */
    public static boolean embedded() {
        return FabricLoader.getInstance().isModLoaded("minemeboost");
    }

    // ------------------------------------------------------------------
    // Small helpers MineXray needs on its own
    // ------------------------------------------------------------------

    /** Singleplayer, or a LAN world you host. */
    public static boolean singleplayer(MinecraftClient client) {
        return client.player != null && client.isIntegratedServerRunning() && client.getServer() != null;
    }

    public static String tr(String key, String fallback) {
        return net.minecraft.util.Language.getInstance().get(key, fallback);
    }

    public static String trf(String key, String fallback, Object... args) {
        return String.format(tr(key, fallback), args);
    }

    /** A small notification in the corner (any thread). */
    public static void toast(String title, String description) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION, Text.literal(title),
                    description == null ? null : Text.literal(description)));
        }
    }

    /** Runs something for you on the integrated server thread. */
    public static void onServer(Consumer<net.minecraft.server.network.ServerPlayerEntity> action) {
        MinecraftClient client = MinecraftClient.getInstance();
        var server = client.getServer();
        if (server == null || client.player == null) {
            return;
        }
        java.util.UUID id = client.player.getUuid();
        server.execute(() -> {
            var player = server.getPlayerManager().getPlayer(id);
            if (player != null) {
                try {
                    action.accept(player);
                } catch (Throwable t) {
                    org.slf4j.LoggerFactory.getLogger("MineXray").error("[MineXray] server action failed", t);
                }
            }
        });
    }
}
