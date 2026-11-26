package com.minexray.compat;

import com.minexray.mode.ClassicXray;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.function.Supplier;

/**
 * Classic X-Ray's opacity with Fabric's renderer (Indigo): each block model gets a thin wrapper that, only while
 * a hidden block is drawn see-through, lowers its quads' alpha. Otherwise it passes everything straight on, so
 * models render exactly as before. (Minecraft's own block renderer is handled in BlockRenderManagerMixin.)
 */
public final class XrayModels {
    private XrayModels() {
    }

    public static void register() {
        if (SodiumCompat.present()) {
            return; // Classic X-Ray is off with Sodium: nothing to wrap
        }
        ModelLoadingPlugin.register(context -> context.modifyModelAfterBake().register((model, ctx) -> {
            if (model == null || ctx.topLevelId() == null || "inventory".equals(ctx.topLevelId().getVariant())) {
                return model; // only block-state models
            }
            return new SeeThrough(model);
        }));
    }

    private static final class SeeThrough extends ForwardingBakedModel {
        SeeThrough(BakedModel wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
            if (!ClassicXray.seeThrough(state)) {
                super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
                return;
            }
            float alpha = ClassicXray.alpha();
            context.pushTransform(quad -> {
                for (int i = 0; i < 4; i++) {
                    int color = quad.color(i);
                    quad.color(i, Math.round(((color >>> 24) & 0xFF) * alpha) << 24 | (color & 0xFFFFFF));
                }
                return true;
            });
            super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
            context.popTransform();
        }
    }
}
