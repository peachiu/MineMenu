package com.mineboost.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.profiler.ProfileResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lag Doctor: reads each frame's profiler result, and clears it so the F3 pie chart isn't drawn. */
@Mixin(MinecraftClient.class)
public interface ProfilerResultAccessor {
    @Accessor("tickProfilerResult")
    ProfileResult mineboost$getProfileResult();

    @Accessor("tickProfilerResult")
    void mineboost$setProfileResult(ProfileResult result);
}
