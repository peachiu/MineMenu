package com.minemenu.build;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.LocalHost;
import com.minemenu.MineMenuClient;
import com.minemenu.ServerCheats;
import com.minemenu.core.Bootstrap;
import com.minemenu.core.CheatModule;
import com.minemenu.core.Guard;
import com.minemenu.core.Modules;
import com.minemenu.esp.LineRenderer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.util.function.Consumer;

/** Wires the Build tools to the game: wand clicks, the brush, the gravity gun, and the selection/preview boxes. */
public final class BuildModules {
    private static boolean useWasDown;

    private BuildModules() {
    }

    /** Runs on the integrated server for the host (from the client thread). */
    private static void onServer(Consumer<ServerPlayerEntity> action) {
        IntegratedServer server = MinecraftClient.getInstance().getServer();
        if (server != null) {
            server.execute(() -> {
                ServerPlayerEntity player = ServerCheats.host(server);
                if (player != null) {
                    Guard.run("Build", () -> action.accept(player));
                }
            });
        }
    }

    public static void register() {
        Modules.register(Feature.SELECTION_WAND, new CheatModule() { });
        Modules.register(Feature.TERRAIN_BRUSH, new CheatModule() { });
        Modules.register(Feature.GRAVITY_GUN, new CheatModule() {
            @Override
            public void serverTick(ServerPlayerEntity player, int tick) {
                GravityGun.tick(player);
            }

            @Override
            public void clientTick(MinecraftClient client) {
                boolean down = client.options.useKey.isPressed();
                if (down && !useWasDown && client.currentScreen == null && client.player.isSneaking()
                        && client.player.getMainHandStack().isEmpty()) {
                    onServer(GravityGun::click);
                }
                useWasDown = down;
            }

            @Override
            public void reset() {
                GravityGun.reset();
            }
        });

        // Selection Wand: wooden axe, left-click = corner 1, right-click = corner 2 (the block isn't broken/used).
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient() && hand == Hand.MAIN_HAND && CheatState.isOn(Feature.SELECTION_WAND)
                    && player.getMainHandStack().isOf(Items.WOODEN_AXE) && LocalHost.is(player)) {
                BlockPos at = pos.toImmutable();
                onServer(p -> Selection.set(1, at, p));
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient() || hand != Hand.MAIN_HAND || !LocalHost.is(player)) {
                return ActionResult.PASS;
            }
            if (CheatState.isOn(Feature.SELECTION_WAND) && player.getMainHandStack().isOf(Items.WOODEN_AXE)) {
                BlockPos at = hit.getBlockPos().toImmutable();
                onServer(p -> Selection.set(2, at, p));
                return ActionResult.SUCCESS;
            }
            if (CheatState.isOn(Feature.TERRAIN_BRUSH) && player.getMainHandStack().isOf(Items.GOLDEN_SHOVEL)) {
                onServer(TerrainBrush::use);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient() && hand == Hand.MAIN_HAND && CheatState.isOn(Feature.TERRAIN_BRUSH)
                    && player.getMainHandStack().isOf(Items.GOLDEN_SHOVEL) && LocalHost.is(player)) {
                onServer(TerrainBrush::use);
                return TypedActionResult.success(player.getMainHandStack());
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });

        // Selection box (cyan) and paste preview (yellow), drawn through walls.
        LineRenderer.register((sink, camera) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || !MineMenuClient.isSingleplayer(client)) {
                return;
            }
            String dimension = client.world.getRegistryKey().getValue().toString();
            BlockBox selection = Selection.box();
            if (selection != null && dimension.equals(Selection.dimension())) {
                sink.box(selection.getMinX(), selection.getMinY(), selection.getMinZ(), selection.getMaxX() + 1, selection.getMaxY() + 1, selection.getMaxZ() + 1, 0x55FFFF);
            }
            for (int i = 1; i <= 2; i++) {
                BlockPos corner = Selection.corner(i);
                if (corner != null && dimension.equals(Selection.dimension())) {
                    sink.block(corner.getX(), corner.getY(), corner.getZ(), i == 1 ? 0xFF5555 : 0x5555FF);
                }
            }
            if (Clipboard.previewing) {
                if (client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                    Clipboard.anchor = hit.getBlockPos().offset(hit.getSide()).toImmutable();
                }
                BlockBox preview = Clipboard.previewBox(Clipboard.anchor);
                if (preview != null) {
                    sink.box(preview.getMinX(), preview.getMinY(), preview.getMinZ(), preview.getMaxX() + 1, preview.getMaxY() + 1, preview.getMaxZ() + 1, 0xFFFF55);
                }
            }
        });
        Bootstrap.onLeaveWorld(() -> {
            Selection.clear();
            Clipboard.clear();
            UndoStack.clear();
            BlockPlacer.clear();
            GravityGun.reset();
        });
    }
}
