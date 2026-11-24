package com.minemenu.mixin;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Bow Aimbot / Trident Storm: whether an arrow or trident is stuck in a block. */
@Mixin(PersistentProjectileEntity.class)
public interface ProjectileAccessor {
    @Accessor("inGround")
    boolean minemenu$isInGround();
}
