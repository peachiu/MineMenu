package com.minemenu.op;

import com.minemenu.MineMenuClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * 6.0: runs Minecraft commands for the Server Admin panel. In your own world they go straight to its server (as you,
 * with full rights); on a server where you're OP they're sent as normal commands. Elsewhere nothing happens.
 */
public final class Cmd {
    private Cmd() {
    }

    /** Your own world, or a server where you're OP. */
    public static boolean available(MinecraftClient client) {
        return client.player != null && (MineMenuClient.isSingleplayer(client) || OpCommands.active(client));
    }

    public static void run(MinecraftClient client, String... commands) {
        if (client.player == null) {
            return;
        }
        if (MineMenuClient.isSingleplayer(client)) {
            IntegratedServer server = client.getServer();
            java.util.UUID id = client.player.getUuid();
            server.execute(() -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                ServerCommandSource source = player != null ? player.getCommandSource().withLevel(4) : server.getCommandSource();
                for (String command : commands) {
                    server.getCommandManager().executeWithPrefix(source, command);
                }
            });
        } else if (OpCommands.active(client)) {
            OpCommands.send(client, List.of(commands));
        }
    }

    /** Your own world only: the server itself, to read game rules (null on other servers). */
    public static IntegratedServer localServer(MinecraftClient client) {
        return MineMenuClient.isSingleplayer(client) ? client.getServer() : null;
    }

    /** Keeps names safe inside a command (letters, digits, underscore: Minecraft names). */
    public static String name(String text) {
        return text == null ? "" : text.trim().replaceAll("[^A-Za-z0-9_]", "");
    }

    /** Text for /say and /title: no line breaks, quotes escaped for JSON. */
    public static String text(String text) {
        return text == null ? "" : text.replace("\n", " ").replace("\\", "\\\\").replace("\"", "\\\"").trim();
    }

}
