package com.minemenu.mixin;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.LocalHost;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Explosive Arrows: your arrows (and tridents) explode when they hit something. */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntityMixin {
    @Inject(method = "onCollision", at = @At("HEAD"), cancellable = true)
    private void minemenu$explode(HitResult hitResult, CallbackInfo ci) {
        ProjectileEntity self = (ProjectileEntity) (Object) this;
        World world = self.getWorld();
        // Minecraft also calls this while the arrow is still flying (type MISS): only a real hit counts.
        if (hitResult.getType() == HitResult.Type.MISS || world.isClient() || !(self instanceof PersistentProjectileEntity)
                || !CheatState.isOn(Feature.EXPLOSIVE_ARROWS) || !LocalHost.is(self.getOwner())) {
            return;
        }
        var pos = hitResult.getPos();
        world.createExplosion(self, pos.x, pos.y, pos.z, 3.0f, World.ExplosionSourceType.TNT); // creeper-sized
        self.discard();
        ci.cancel();
    }
}
