package com.mineboost.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Particle Range read where a particle is. */
@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("x")
    double mineboost$getX();

    @Accessor("y")
    double mineboost$getY();

    @Accessor("z")
    double mineboost$getZ();
}
