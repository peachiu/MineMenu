package com.mineboost.mixin;

import com.mineboost.SectionCulling;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Section Culling: right before the entity loop (its getEntities() call) take the list of sections that will be
 * drawn this frame. Not loaded with Sodium (MineBoostMixinPlugin): Sodium doesn't fill this list.
 */
@Mixin(WorldRenderer.class)
public class SectionCullingMixin {
    @Shadow
    @Final
    private ObjectArrayList<ChunkBuilder.BuiltChunk> builtChunks;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getEntities()Ljava/lang/Iterable;"))
    private void mineboost$visibleSections(CallbackInfo ci) {
        if (SectionCulling.enabled()) {
            SectionCulling.rebuild(builtChunks);
        } else {
            SectionCulling.clear();
        }
    }
}
