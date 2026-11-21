package com.minemenu;

import net.minecraft.entity.EntityType;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** The mobs the "Spawn Mob" picker offers. */
public final class MobList {
    /** Mobs without a spawn egg that are still fun to spawn. */
    private static final Set<EntityType<?>> EXTRAS = Set.of(EntityType.GIANT, EntityType.ILLUSIONER);

    private MobList() {
    }

    /** Every mob with a spawn egg, plus a few hidden ones. */
    public static boolean isSpawnable(EntityType<?> type) {
        return type.isSummonable() && (SpawnEggItem.forEntity(type) != null || EXTRAS.contains(type));
    }

    /** All spawnable mobs, sorted by name. */
    public static List<EntityType<?>> all() {
        return Registries.ENTITY_TYPE.stream()
                .filter(MobList::isSpawnable)
                .sorted(Comparator.comparing(type -> type.getName().getString()))
                .toList();
    }
}
