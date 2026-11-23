package com.minemenu;

import com.minemenu.mixin.FishingBobberEntityAccessor;
import com.minemenu.mixin.MinecraftClientAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/**
 * The MineMenu 3.3 hacks that are about what your own client does: Zoom, Elytra Boost, Auto-Tool,
 * Auto-Fish and Instant Build. Your movement and your clicks are decided by the client (then sent to
 * the server as usual), so these run on the client thread, and only in a private singleplayer world.
 */
public final class ClientCheats {
    private static final double MIN_ZOOM = 1.5;
    private static final double MAX_ZOOM = 50;

    private static double zoom = 4;
    private static int recastIn = -1;

    private ClientCheats() {
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null || !MineMenuClient.isSingleplayer(client)) {
            return;
        }
        if (CheatState.isOn(Feature.ELYTRA_BOOST)) {
            elytraBoost(client, player);
        }
        if (CheatState.isOn(Feature.AUTO_TOOL)) {
            autoTool(client, player);
        }
        if (CheatState.isOn(Feature.AUTO_FISH)) {
            autoFish(client, player);
        } else {
            recastIn = -1;
        }
        if (CheatState.isOn(Feature.INSTANT_BUILD) && (player.getMainHandStack().getItem() instanceof BlockItem
                || player.getOffHandStack().getItem() instanceof BlockItem)) {
            // Vanilla waits 4 ticks between placements while you hold right-click.
            ((MinecraftClientAccessor) client).minemenu$setItemUseCooldown(0);
        }
    }

    // ------------------------------------------------------------------
    // Zoom (see GameRendererMixin and the zoom part of MouseMixin)
    // ------------------------------------------------------------------

    /** True while the zoom key is held (and Zoom is on, in singleplayer, with no menu open). */
    public static boolean isZooming() {
        MinecraftClient client = MinecraftClient.getInstance();
        return CheatState.isOn(Feature.ZOOM) && MineMenuClient.zoomKey != null && MineMenuClient.zoomKey.isPressed()
                && client.currentScreen == null && MineMenuClient.isSingleplayer(client);
    }

    /** How many times closer things look (4x by default). */
    public static double zoomFactor() {
        return zoom;
    }

    /** Scroll wheel while zooming: up = closer, down = farther. */
    public static void scrollZoom(double amount) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * Math.pow(1.25, amount)));
    }

    // ------------------------------------------------------------------

    /** Like a firework every tick while you hold forward: the same push a firework rocket gives. */
    private static void elytraBoost(MinecraftClient client, ClientPlayerEntity player) {
        if (!player.isFallFlying() || !client.options.forwardKey.isPressed()) {
            return;
        }
        Vec3d look = player.getRotationVector();
        Vec3d v = player.getVelocity();
        player.setVelocity(v.add(look.x * 0.1 + (look.x * 1.5 - v.x) * 0.5,
                look.y * 0.1 + (look.y * 1.5 - v.y) * 0.5,
                look.z * 0.1 + (look.z * 1.5 - v.z) * 0.5));
    }

    /** While you hold attack on a block: switch to the hotbar slot that breaks it fastest. */
    private static void autoTool(MinecraftClient client, ClientPlayerEntity player) {
        if (client.currentScreen != null || !client.options.attackKey.isPressed() || client.world == null
                || !(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockState state = client.world.getBlockState(hit.getBlockPos());
        if (state.isAir()) {
            return;
        }
        var inventory = player.getInventory();
        int best = inventory.selectedSlot;
        double bestScore = toolScore(inventory.getStack(best), state);
        for (int slot = 0; slot < 9; slot++) {
            double score = toolScore(inventory.getStack(slot), state);
            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        inventory.selectedSlot = best; // sent to the server by the next mining packet
    }

    private static double toolScore(ItemStack stack, BlockState state) {
        double score = stack.getMiningSpeedMultiplier(state);
        if (state.isToolRequired() && stack.isSuitableFor(state)) {
            score += 1000; // the right tool, or the block drops nothing
        }
        if (stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() <= 1) {
            score = 0; // about to break: don't use it up
        }
        return score;
    }

    /** Reel in when a fish bites (the bobber dips), then cast again half a second later. */
    private static void autoFish(MinecraftClient client, ClientPlayerEntity player) {
        Hand hand = player.getMainHandStack().getItem() instanceof FishingRodItem ? Hand.MAIN_HAND
                : player.getOffHandStack().getItem() instanceof FishingRodItem ? Hand.OFF_HAND : null;
        if (hand == null) {
            recastIn = -1;
            return;
        }
        if (player.fishHook != null && ((FishingBobberEntityAccessor) player.fishHook).minemenu$hasCaughtFish()) {
            client.interactionManager.interactItem(player, hand);
            player.swingHand(hand);
            recastIn = 10;
            return;
        }
        if (recastIn > 0) {
            recastIn--;
        } else if (recastIn == 0) {
            recastIn = -1;
            if (player.fishHook == null) {
                client.interactionManager.interactItem(player, hand);
                player.swingHand(hand);
            }
        }
    }
}
