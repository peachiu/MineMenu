package com.mineboost.mixin;

import com.mineboost.BoostConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Background FPS: when you alt-tab away, Minecraft keeps drawing at full speed for nobody.
 * Cap it so your PC stays cool and other apps (like OBS or your browser) get the power.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow
    @Final
    private Window window;

    @Shadow
    public abstract boolean isWindowFocused();

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void mineboost$backgroundFps(CallbackInfoReturnable<Integer> cir) {
        // Menu FPS: while an inventory or the pause menu is open there's nothing moving that needs full speed.
        int menuCap = BoostConfig.get().menuFps;
        MinecraftClient self = (MinecraftClient) (Object) this;
        if (menuCap > 0 && self.world != null && self.currentScreen != null
                && !(self.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) {
            cir.setReturnValue(Math.min(cir.getReturnValueI(), menuCap));
        }
        int cap = BoostConfig.get().backgroundFps;
        if (cap <= 0) {
            return;
        }
        if (GLFW.glfwGetWindowAttrib(window.getHandle(), GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE) {
            cir.setReturnValue(1); // minimized: nothing is visible at all
        } else if (!isWindowFocused()) {
            cir.setReturnValue(Math.min(cir.getReturnValueI(), cap));
        }
    }
}
