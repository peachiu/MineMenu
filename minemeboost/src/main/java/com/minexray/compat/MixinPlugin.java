package com.minexray.compat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Leaves out Classic X-Ray's chunk-builder mixins when Sodium (or a fork) is installed. */
public final class MixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> CLASSIC_ONLY = Set.of(
            "com.minexray.mixin.BlockRenderManagerMixin",
            "com.minexray.mixin.BlockSideMixin",
            "com.minexray.mixin.RenderLayersMixin",
            "com.minexray.mixin.AmbientOcclusionMixin");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !CLASSIC_ONLY.contains(mixinClassName) || !SodiumCompat.present();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
