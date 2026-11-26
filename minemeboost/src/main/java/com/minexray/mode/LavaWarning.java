package com.minexray.mode;

import com.minexray.XrayConfig;
import com.minexray.hud.SessionStats;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Lava Warning: while you mine a block, checks up to 3 blocks behind it (the way you're digging), up to 3 below
 * it and right next to it. If there's lava, a warning shows and a low note plays. Works with the other modes off.
 */
public final class LavaWarning {
    private static final int DEPTH = 3;
    private static long warningUntil;
    private static long lastSound;
    private static BlockPos lavaAt;

    private LavaWarning() {
    }

    public static boolean showing() {
        return System.currentTimeMillis() < warningUntil;
    }

    public static BlockPos lavaAt() {
        return lavaAt;
    }

    public static void tick(MinecraftClient client, boolean on) {
        if (!on || client.world == null || client.player == null || !client.options.attackKey.isPressed()) {
            return;
        }
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos found = check(client, blockHit.getBlockPos(), blockHit.getSide().getOpposite());
        if (found == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!showing()) {
            SessionStats.lavaWarnings++;
        }
        warningUntil = now + 1500;
        lavaAt = found;
        if (XrayConfig.get().lavaWarningSound && now - lastSound > 2000) {
            lastSound = now;
            client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
        }
    }

    /** The first lava block within reach of {@code mined}, digging towards {@code into}; null if none. */
    public static BlockPos check(MinecraftClient client, BlockPos mined, Direction into) {
        for (Direction side : Direction.values()) {
            if (lava(client, mined.offset(side))) {
                return mined.offset(side);
            }
        }
        for (int k = 1; k <= DEPTH; k++) {
            if (lava(client, mined.offset(into, k))) {
                return mined.offset(into, k);
            }
            if (lava(client, mined.down(k))) {
                return mined.down(k);
            }
        }
        return null;
    }

    private static boolean lava(MinecraftClient client, BlockPos pos) {
        return client.world.getBlockState(pos).isOf(Blocks.LAVA);
    }
}
