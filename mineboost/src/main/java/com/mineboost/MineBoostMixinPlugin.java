package com.mineboost;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Decides, before the game starts, which MineBoost mixins to load. A mixin whose job another
 * performance mod already does (or whose target that mod replaces) isn't loaded at all, so the
 * two mods never fight over the same code.
 *
 * <p>It only uses Fabric Loader (no Minecraft classes): it runs before Minecraft is loaded.
 */
public class MineBoostMixinPlugin implements IMixinConfigPlugin {
    /** {mixin, mod}: don't load that MineBoost mixin when that mod is installed. */
    private static final String[][] SKIP = {
            {"RenderScaleMixin", "iris"},        // shaders replace the world rendering path
            {"SectionCullingMixin", "sodium"},   // Sodium doesn't fill vanilla's visible-section list
    };

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        for (String[] rule : SKIP) {
            if (mixinClassName.endsWith("." + rule[0]) && FabricLoader.getInstance().isModLoaded(rule[1])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
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
