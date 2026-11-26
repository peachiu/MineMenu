package com.minemenu.player;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.core.CheatModule;
import com.minemenu.core.FeatureSettings;
import com.minemenu.core.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** The small 3.4 player hacks: Full Bright, Auto-Sprint, Double Jump, Dash, Scaffold, Lava Swim, Gravity, Phase, Nuker. */
public final class PlayerModules {
    private static final Identifier GRAVITY_ID = Identifier.of("minemenu", "gravity_control");
    /** Read in the collision hot path (PhaseMixin), so a plain volatile flag instead of the synchronized state. */
    public static volatile boolean phaseOn;
    public static volatile boolean fullBrightOn;
    /** 3.6: set by MineXray's Classic X-Ray while it's on (full brightness without the Full Bright hack). */
    public static volatile boolean forceFullBright;
    public static volatile boolean lavaSwimOn;

    private PlayerModules() {
    }

    public static void register() {
        Modules.register(Feature.FREECAM, new Freecam());
        Modules.register(Feature.FULL_BRIGHT, new CheatModule() { });
        Modules.register(Feature.PHASE, new CheatModule() { });
        Modules.register(Feature.AUTO_SPRINT, new CheatModule() {
            @Override
            public void clientTick(MinecraftClient client) {
                ClientPlayerEntity p = client.player;
                if (client.options.forwardKey.isPressed() && !p.isSneaking() && !p.isUsingItem() && !p.horizontalCollision
                        && (p.getHungerManager().getFoodLevel() > 6 || p.getAbilities().allowFlying)) {
                    p.setSprinting(true);
                }
            }
        });
        Modules.register(Feature.DOUBLE_JUMP, new DoubleJump());
        Modules.register(Feature.DASH, new CheatModule() {
            @Override
            public void clientTick(MinecraftClient client) {
                Dash.tick(client);
            }
        });
        Modules.register(Feature.SCAFFOLD, new CheatModule() {
            @Override
            public void serverTick(ServerPlayerEntity player, int tick) {
                scaffold(player);
            }
        });
        Modules.register(Feature.LAVA_SWIM, new CheatModule() {
            @Override
            public void serverTick(ServerPlayerEntity player, int tick) {
                if (!player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, StatusEffectInstance.INFINITE, 0, false, false, true));
                }
            }

            @Override
            public void disable(MinecraftServer server, ServerPlayerEntity player) {
                if (player != null) {
                    StatusEffectInstance effect = player.getStatusEffect(StatusEffects.FIRE_RESISTANCE);
                    if (effect != null && effect.isInfinite()) {
                        player.removeStatusEffect(StatusEffects.FIRE_RESISTANCE);
                    }
                }
            }

            @Override
            public void clientTick(MinecraftClient client) {
                ClientPlayerEntity p = client.player;
                if (!p.isInLava()) {
                    return;
                }
                // Lava is thick: move about as fast as in water.
                Vec3d v = p.getVelocity();
                double vy = client.options.jumpKey.isPressed() ? 0.18 : client.options.sneakKey.isPressed() ? -0.18 : v.y;
                double max = 0.25;
                p.setVelocity(Math.max(-max, Math.min(max, v.x * 1.9)), vy, Math.max(-max, Math.min(max, v.z * 1.9)));
            }
        });
        Modules.register(Feature.GRAVITY_CONTROL, new CheatModule() {
            @Override
            public void serverTick(ServerPlayerEntity player, int tick) {
                if (tick % 10 == 0) {
                    setGravity(player, FeatureSettings.get(Feature.GRAVITY_CONTROL, "gravity"));
                }
            }

            @Override
            public void enable(MinecraftServer server, ServerPlayerEntity player) {
                setGravity(player, FeatureSettings.get(Feature.GRAVITY_CONTROL, "gravity"));
            }

            @Override
            public void disable(MinecraftServer server, ServerPlayerEntity player) {
                if (player != null) {
                    setGravity(player, 1.0);
                }
            }
        });
        Modules.register(Feature.NUKER, new CheatModule() {
            @Override
            public void serverTick(ServerPlayerEntity player, int tick) {
                if (tick % 2 == 0 && !player.isSneaking()) {
                    nuke(player, (int) FeatureSettings.get(Feature.NUKER, "radius"));
                }
            }
        });
    }

    /** Every client tick: refresh the flags read in hot code. */
    public static void refreshFlags(MinecraftClient client) {
        boolean singleplayer = com.minemenu.MineMenuClient.isSingleplayer(client);
        phaseOn = singleplayer && CheatState.isOn(Feature.PHASE);
        fullBrightOn = singleplayer && CheatState.isOn(Feature.FULL_BRIGHT);
        lavaSwimOn = singleplayer && CheatState.isOn(Feature.LAVA_SWIM);
        Freecam.checkOff(client);
    }

    private static void setGravity(ServerPlayerEntity player, double factor) {
        EntityAttributeInstance gravity = player.getAttributeInstance(EntityAttributes.GENERIC_GRAVITY);
        if (gravity == null) {
            return;
        }
        double value = factor - 1.0;
        EntityAttributeModifier existing = gravity.getModifier(GRAVITY_ID);
        if (existing != null && existing.value() == value) {
            return;
        }
        gravity.removeModifier(GRAVITY_ID);
        if (value != 0) {
            gravity.addTemporaryModifier(new EntityAttributeModifier(GRAVITY_ID, value, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        player.fallDistance = 0;
    }

    /** Scaffold: a block from the hotbar under your feet when there's air there (not while flying or sneaking). */
    private static void scaffold(ServerPlayerEntity player) {
        if (player.getAbilities().flying || player.isSneaking() || player.isSpectator()) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        BlockPos below = BlockPos.ofFloored(player.getX(), player.getY() - 0.5, player.getZ());
        if (!world.getBlockState(below).isReplaceable()) {
            return;
        }
        var inventory = player.getInventory();
        int slot = inventory.getMainHandStack().getItem() instanceof BlockItem ? inventory.selectedSlot : -1;
        for (int i = 0; i < 9 && slot < 0; i++) {
            if (inventory.getStack(i).getItem() instanceof BlockItem) {
                slot = i;
            }
        }
        if (slot < 0) {
            return;
        }
        ItemStack stack = inventory.getStack(slot);
        var state = ((BlockItem) stack.getItem()).getBlock().getDefaultState();
        if (state.canPlaceAt(world, below)) {
            world.setBlockState(below, state);
            if (!player.isCreative()) {
                stack.decrement(1);
            }
        }
    }

    /** Nuker: breaks blocks around you (with drops), from your feet up, at most 48 a go. */
    private static void nuke(ServerPlayerEntity player, int radius) {
        ServerWorld world = player.getServerWorld();
        BlockPos feet = player.getBlockPos();
        int broken = 0;
        for (BlockPos pos : BlockPos.iterate(feet.add(-radius, 0, -radius), feet.add(radius, radius + 1, radius))) {
            if (broken >= 48) {
                return;
            }
            var state = world.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.getHardness(world, pos) < 0) {
                continue;
            }
            if (world.breakBlock(pos, !player.isCreative(), player)) {
                broken++;
            }
        }
    }
}
