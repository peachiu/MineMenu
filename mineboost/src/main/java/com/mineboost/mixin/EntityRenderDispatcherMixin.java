package com.mineboost.mixin;

import com.mineboost.BoostConfig;
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
        double max = BoostConfig.get().entityDistanceBlocks();
        if (max < 0) {
            return;
        }
        // Always draw players and bosses: you want to see those.
        if (entity instanceof PlayerEntity || entity instanceof EnderDragonEntity || entity instanceof WitherEntity) {
            return;
        }
        if (entity.squaredDistanceTo(x, y, z) > max * max) {
            cir.setReturnValue(false);
        }
    }
}
