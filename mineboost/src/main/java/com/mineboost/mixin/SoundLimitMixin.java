package com.mineboost.mixin;

import com.mineboost.BoostConfig;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Max Sounds: new sounds are skipped while this many are already playing (big farms, explosions). */
@Mixin(SoundSystem.class)
public class SoundLimitMixin {
    @Shadow
    @Final
    private Map<SoundInstance, Channel.SourceManager> sources;

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void mineboost$limitSounds(SoundInstance sound, CallbackInfo ci) {
        int limit = BoostConfig.get().maxSounds;
        if (limit > 0 && sources.size() >= limit) {
            ci.cancel();
        }
    }
}
