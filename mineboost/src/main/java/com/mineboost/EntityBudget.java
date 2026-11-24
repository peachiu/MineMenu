package com.mineboost;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Max Entities: with hundreds of mobs around (farms, mob grinders), only the closest ones are drawn.
 * The list of closest entities is worked out twice a second, so it costs almost nothing per frame.
 */
public final class EntityBudget {
    /** Entity ids, as a primitive set: checked for every entity every frame, so no Integer boxing. */
    private static it.unimi.dsi.fastutil.ints.IntSet allowed = it.unimi.dsi.fastutil.ints.IntSets.EMPTY_SET;
    private static int ticks;

    private EntityBudget() {
    }

    /** True for anything that is always drawn, whatever the limit. */
    private static boolean alwaysDrawn(MinecraftClient client, Entity entity) {
        return entity == client.getCameraEntity() || entity instanceof PlayerEntity || entity instanceof EnderDragonEntity
                || entity instanceof WitherEntity || entity.isGlowing() || entity.hasPassengers() || entity.hasVehicle();
    }

    public static boolean isAllowed(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        return alwaysDrawn(client, entity) || allowed.contains(entity.getId());
    }

    public static void clear() {
        allowed = it.unimi.dsi.fastutil.ints.IntSets.EMPTY_SET;
    }

    /** Called every client tick. */
    public static void tick(MinecraftClient client) {
        int limit = BoostConfig.get().maxEntities;
        if (limit <= 0 || client.world == null || client.gameRenderer == null) {
            return;
        }
        if (ticks++ % 10 != 0 && !allowed.isEmpty()) {
            return;
        }
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        List<Entity> candidates = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            if (!alwaysDrawn(client, entity)) {
                candidates.add(entity);
            }
        }
        candidates.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(camera)));
        it.unimi.dsi.fastutil.ints.IntOpenHashSet next = new it.unimi.dsi.fastutil.ints.IntOpenHashSet(limit * 2);
        for (int i = 0; i < Math.min(limit, candidates.size()); i++) {
            next.add(candidates.get(i).getId());
        }
        allowed = next;
    }
}
