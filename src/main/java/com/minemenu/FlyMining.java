package com.minemenu;

import net.minecraft.entity.player.PlayerEntity;

/** Decides when mining in the air should be as fast as on the ground: Fly is on, and it's you. */
public final class FlyMining {
    private FlyMining() {
    }

    public static boolean applies(PlayerEntity player) {
        return CheatState.isOn(Feature.FLY) && LocalHost.is(player);
    }
}
