package com.mineboost.mixin;

import com.mineboost.BoostConfig;
import com.mineboost.Compat;
import com.mineboost.Culling;
import com.mineboost.EntityBudget;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
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
    /** Impostors: far mobs as a flat picture (one config check while off). */
    @Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void mineboost$impostor(Entity entity, double x, double y, double z, float yaw, float tickDelta,
                                    net.minecraft.client.util.math.MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider consumers,
                                    int light, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (com.mineboost.Impostors.enabled()) {
            try {
                if (com.mineboost.Impostors.draw(entity, x, y, z, matrices, consumers, light)) {
                    ci.cancel();
                }
            } catch (Throwable t) {
                com.mineboost.BoostGuard.fail("Impostors", t, () -> BoostConfig.get().impostorDistance = 0);
            }
        }
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void mineboost$limitDistance(E entity, Frustum frustum, double x, double y, double z,
                                                         CallbackInfoReturnable<Boolean> cir) {
        BoostConfig config = BoostConfig.get();
        // Hide Decorations: armor stands, item frames and paintings.
        if (config.hideDecorations && (entity instanceof ArmorStandEntity || entity instanceof ItemFrameEntity
                || entity instanceof PaintingEntity)) {
            cir.setReturnValue(false);
            return;
        }
        // Dropped Items Distance: farms and explosions leave lots of items around.
        if (config.itemDistance > 0 && entity instanceof ItemEntity
                && entity.squaredDistanceTo(x, y, z) > (double) config.itemDistance * config.itemDistance) {
            cir.setReturnValue(false);
            return;
        }
        // Max Entities: only the closest ones.
        if (config.maxEntities > 0 && !EntityBudget.isAllowed(entity)) {
            cir.setReturnValue(false);
            return;
        }
        double max = config.entityDistanceBlocks();
        // Always draw players and bosses: you want to see those.
        if (max >= 0 && !(entity instanceof PlayerEntity || entity instanceof EnderDragonEntity || entity instanceof WitherEntity)
                && entity.squaredDistanceTo(x, y, z) > max * max) {
            cir.setReturnValue(false);
            return;
        }
        // Section Culling: in a chunk section the game knows can't be seen (caves, behind hills)?
        if (com.mineboost.SectionCulling.enabled() && !(entity instanceof PlayerEntity) && !entity.isGlowing()
                && !com.mineboost.SectionCulling.isVisible(entity)) {
            cir.setReturnValue(false);
            return;
        }
        // Smart Culling: hidden behind solid blocks? Then don't draw it. Only worth checking for things
        // in view; vanilla already skips the rest.
        if (Compat.smartCulling() && frustum.isVisible(entity.getVisibilityBoundingBox())
                && !Culling.isEntityVisible(entity, x, y, z)) {
            cir.setReturnValue(false);
        }
    }
}
