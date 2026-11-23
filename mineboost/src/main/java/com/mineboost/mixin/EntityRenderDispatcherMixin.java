package com.mineboost.mixin;

import com.mineboost.BoostConfig;
import com.mineboost.Culling;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity render distance: skip drawing mobs, items, item frames etc. beyond the limit. */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void mineboost$limitDistance(E entity, Frustum frustum, double x, double y, double z,
                                                         CallbackInfoReturnable<Boolean> cir) {
        BoostConfig config = BoostConfig.get();
        double max = config.entityDistanceBlocks();
        // Always draw players and bosses: you want to see those.
        if (max >= 0 && !(entity instanceof PlayerEntity || entity instanceof EnderDragonEntity || entity instanceof WitherEntity)
                && entity.squaredDistanceTo(x, y, z) > max * max) {
            cir.setReturnValue(false);
            return;
        }
        // Smart Culling: hidden behind solid blocks? Then don't draw it. Only worth checking for things
        // in view; vanilla already skips the rest.
        if (config.smartCulling && frustum.isVisible(entity.getVisibilityBoundingBox())
                && !Culling.isEntityVisible(entity, x, y, z)) {
            cir.setReturnValue(false);
        }
    }
}
