package com.mineboost.tools;

import com.mineboost.BoostConfig;
import com.mineboost.BoostGuard;
import com.mineboost.Preset;
import com.mineboost.PresetState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

/**
 * Context rules: switch to another preset by situation (Nether, End, underground, near lots of mobs, flying an
 * elytra) and back to your own settings when you leave it. Checked every 2 seconds; a new situation must last
 * 3 seconds before anything changes, so walking past a cave entrance doesn't flip settings.
 */
public final class ContextRules {
    public enum Situation { NORMAL, NETHER, END, CAVE, CROWD, ELYTRA }

    private static Situation current = Situation.NORMAL;
    private static Situation candidate = Situation.NORMAL;
    private static int candidateChecks;
    private static PresetState before;
    private static int ticks;

    private ContextRules() {
    }

    public static Situation current() {
        return current;
    }

    public static void tick(MinecraftClient client) {
        BoostConfig c = BoostConfig.get();
        if (client.world == null || client.player == null || ++ticks % 40 != 0) {
            return;
        }
        if (!c.contextRules) {
            if (current != Situation.NORMAL) {
                leave(client);
            }
            return;
        }
        Situation now = detect(client);
        if (now != candidate) {
            candidate = now;
            candidateChecks = 0;
            return;
        }
        if (++candidateChecks < 2 || now == current) {
            return;
        }
        BoostGuard.run("Context rules", () -> switchTo(client, now), () -> c.contextRules = false);
    }

    static Situation detect(MinecraftClient client) {
        var player = client.player;
        if (player.isFallFlying()) {
            return Situation.ELYTRA;
        }
        int mobs = 0;
        for (var entity : client.world.getEntities()) {
            if (entity instanceof LivingEntity && entity != player && entity.squaredDistanceTo(player) < 32 * 32) {
                mobs++;
            }
        }
        if (mobs > 150) {
            return Situation.CROWD;
        }
        if (client.world.getRegistryKey() == World.NETHER) {
            return Situation.NETHER;
        }
        if (client.world.getRegistryKey() == World.END) {
            return Situation.END;
        }
        if (client.world.getLightLevel(LightType.SKY, player.getBlockPos().up()) == 0 && player.getY() < client.world.getSeaLevel()) {
            return Situation.CAVE;
        }
        return Situation.NORMAL;
    }

    private static void switchTo(MinecraftClient client, Situation next) {
        String presetName = BoostConfig.get().contextPresets.get(next.name());
        Preset preset = presetName == null ? null : find(presetName);
        if (next == Situation.NORMAL || preset == null) {
            leave(client);
            current = next;
            return;
        }
        if (before == null) {
            before = PresetState.capture(client);
        }
        preset.apply(client);
        current = next;
        client.player.sendMessage(Text.literal("MineBoost: " + preset.displayName() + " (" + next.name().toLowerCase() + ")").formatted(Formatting.GREEN), true);
    }

    private static void leave(MinecraftClient client) {
        if (before != null) {
            before.restore(client);
            before = null;
        }
        current = Situation.NORMAL;
    }

    static Preset find(String name) {
        for (Preset preset : Preset.values()) {
            if (preset.name().equals(name)) {
                return preset;
            }
        }
        return null;
    }
}
