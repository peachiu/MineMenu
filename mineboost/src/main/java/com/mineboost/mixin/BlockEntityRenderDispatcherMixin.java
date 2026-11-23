package com.mineboost.mixin;

import com.mineboost.BoostConfig;
import com.mineboost.Culling;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block entity render distance: chests, signs, banners, heads, beds... beyond the limit aren't drawn. */
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
    @Shadow
    public Camera camera;

    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void mineboost$limitDistance(E blockEntity, float tickDelta, MatrixStack matrices,
                                                              VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        BoostConfig config = BoostConfig.get();
        if (camera == null) {
            return;
        }
        // Hide Beacon Beams: the beacon block stays, only its beam isn't drawn.
        if (config.hideBeaconBeams && blockEntity instanceof BeaconBlockEntity) {
            ci.cancel();
            return;
        }
        if (config.blockEntityDistance < BoostConfig.BLOCK_ENTITY_DISTANCE_VANILLA) {
            double max = config.blockEntityDistanceBlocks();
            if (Vec3d.ofCenter(blockEntity.getPos()).squaredDistanceTo(camera.getPos()) > max * max) {
                ci.cancel();
                return;
            }
        }
        // Smart Culling: a chest or sign behind solid blocks isn't drawn.
        if (config.smartCulling && !Culling.isBlockEntityVisible(blockEntity.getPos(), camera.getPos())) {
            ci.cancel();
        }
    }
}
