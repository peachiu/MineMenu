package com.mineboost.mixin;

import com.mineboost.BoostConfig;
import com.mineboost.Idle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    /** Whether the window is minimized, asked once a tick instead of every frame (a native call). */
    @org.spongepowered.asm.mixin.Unique
    private boolean mineboost$iconifiedCache;
    @org.spongepowered.asm.mixin.Unique
    private long mineboost$iconifiedCheckedAt;

    @org.spongepowered.asm.mixin.Unique
    private boolean mineboost$iconified() {
        long now = System.nanoTime();
        if (now - mineboost$iconifiedCheckedAt > 50_000_000L) {
            mineboost$iconifiedCheckedAt = now;
            mineboost$iconifiedCache = GLFW.glfwGetWindowAttrib(window.getHandle(), GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
        }
        return mineboost$iconifiedCache;
    }

    /** Lag Doctor: turn on the game's own profiler while recording (it's off normally: zero cost). */
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "run",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/DebugHud;shouldShowRenderingChart()Z"))
    private boolean mineboost$profileForDoctor(boolean showChart) {
        return showChart || com.mineboost.doctor.LagDoctor.recording();
    }

    @Inject(method = "endMonitor", at = @At("TAIL"))
    private void mineboost$doctorFrame(boolean active, net.minecraft.util.TickDurationMonitor monitor, CallbackInfo ci) {
        if (!com.mineboost.doctor.LagDoctor.recording()) {
            return;
        }
        MinecraftClient self = (MinecraftClient) (Object) this;
        com.mineboost.mixin.ProfilerResultAccessor accessor = (com.mineboost.mixin.ProfilerResultAccessor) self;
        com.mineboost.BoostGuard.run("Lag Doctor", () -> com.mineboost.doctor.LagDoctor.onFrame(accessor.mineboost$getProfileResult()), () -> { });
        if (!self.getDebugHud().shouldShowRenderingChart()) {
            accessor.mineboost$setProfileResult(null); // we turned it on: don't draw the pie chart
        }
    }

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void mineboost$backgroundFps(CallbackInfoReturnable<Integer> cir) {
        // Menu FPS: while an inventory or the pause menu is open there's nothing moving that needs full speed.
        int menuCap = BoostConfig.get().menuFps;
        MinecraftClient self = (MinecraftClient) (Object) this;
        if (menuCap > 0 && self.world != null && self.currentScreen != null
                && !(self.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) {
            cir.setReturnValue(Math.min(cir.getReturnValueI(), menuCap));
        }
        // Battery Saver: 30 FPS.
        if (BoostConfig.get().batterySaver) {
            cir.setReturnValue(Math.min(cir.getReturnValueI(), 30));
        }
        // Idle Mode: nobody has touched the keyboard or mouse for a while.
        int idleCap = Idle.cap(self);
        if (idleCap > 0) {
            cir.setReturnValue(Math.min(cir.getReturnValueI(), idleCap));
        }
        int cap = BoostConfig.get().backgroundFps;
        if (cap <= 0) {
            return;
        }
        if (mineboost$iconified()) {
            cir.setReturnValue(1); // minimized: nothing is visible at all
        } else if (!isWindowFocused()) {
            cir.setReturnValue(Math.min(cir.getReturnValueI(), cap));
        }
    }
}
