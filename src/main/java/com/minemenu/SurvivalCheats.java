package com.minemenu;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.Set;

/**
 * The MineMenu 3.3 player, combat and item hacks that run on the singleplayer server: Noclip, Step-Up,
 * Fast Swim, Auto-Eat, Auto-Totem, Always Crit and Infinite Durability. Server thread only, host only.
 */
public final class SurvivalCheats {
    private static final Identifier STEP_ID = Identifier.of("minemenu", "step_up");
    private static final Identifier SWIM_ID = Identifier.of("minemenu", "fast_swim");
    /** Food that's bad for you, or too precious to eat by accident. */
    private static final Set<net.minecraft.item.Item> NEVER_EAT = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE,
            Items.POISONOUS_POTATO, Items.PUFFERFISH, Items.CHICKEN, Items.SUSPICIOUS_STEW, Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE, Items.CHORUS_FRUIT, Items.GOLDEN_CARROT);

    /** The game mode to go back to when Noclip turns off (server thread only). */
    private static GameMode beforeNoclip;

    private SurvivalCheats() {
    }

    public static void onServerStarted(MinecraftServer server) {
        beforeNoclip = null;
    }

    /** {@code player} may be null when the host has already left. */
    public static void enable(MinecraftServer server, ServerPlayerEntity player, Feature feature) {
        switch (feature) {
            case NOCLIP -> {
                if (!player.isSpectator()) {
                    beforeNoclip = player.interactionManager.getGameMode();
                    // Written to the world folder first: if the game crashes now, the next join puts it back.
                    writeNoclipFile(server, beforeNoclip);
                    player.changeGameMode(GameMode.SPECTATOR);
                }
            }
            case STEP_UP -> setModifier(player, true, STEP_ID);
            case FAST_SWIM -> {
                setModifier(player, true, SWIM_ID);
                addDolphinsGrace(player);
            }
            default -> {
            }
        }
    }

    public static void disable(MinecraftServer server, ServerPlayerEntity player, Feature feature) {
        if (player == null) {
            return;
        }
        switch (feature) {
            case NOCLIP -> {
                GameMode back = beforeNoclip != null ? beforeNoclip : readNoclipFile(server);
                if (player.isSpectator()) {
                    player.changeGameMode(back != null ? back : server.getDefaultGameMode());
                    player.fallDistance = 0;
                }
                beforeNoclip = null;
                deleteNoclipFile(server);
            }
            case STEP_UP -> setModifier(player, false, STEP_ID);
            case FAST_SWIM -> {
                setModifier(player, false, SWIM_ID);
                player.removeStatusEffect(StatusEffects.DOLPHINS_GRACE);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // Noclip safety: the game mode from before Noclip is kept in the world folder
    // ------------------------------------------------------------------

    private static java.nio.file.Path noclipFile(MinecraftServer server) {
        return server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("minemenu_noclip.txt");
    }

    private static void writeNoclipFile(MinecraftServer server, GameMode mode) {
        try {
            java.nio.file.Files.writeString(noclipFile(server), mode.asString());
        } catch (Exception ignored) {
        }
    }

    private static GameMode readNoclipFile(MinecraftServer server) {
        try {
            java.nio.file.Path file = noclipFile(server);
            return java.nio.file.Files.exists(file) ? GameMode.byName(java.nio.file.Files.readString(file).trim(), null) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void deleteNoclipFile(MinecraftServer server) {
        try {
            java.nio.file.Files.deleteIfExists(noclipFile(server));
        } catch (Exception ignored) {
        }
    }

    /**
     * The host joined: if the last session ended (crash, killed game) while Noclip was on, they'd still be a
     * spectator. Put the old game mode back, unless Noclip is meant to be on now.
     */
    public static void onHostJoin(MinecraftServer server, ServerPlayerEntity player) {
        GameMode saved = readNoclipFile(server);
        if (saved == null || CheatState.isOn(Feature.NOCLIP)) {
            return;
        }
        if (player.isSpectator()) {
            player.changeGameMode(saved);
        }
        deleteNoclipFile(server);
    }

    public static void tick(ServerPlayerEntity player, Set<Feature> applied, int tickCounter) {
        if (applied.contains(Feature.FAST_SWIM) && !player.hasStatusEffect(StatusEffects.DOLPHINS_GRACE)) {
            addDolphinsGrace(player); // milk, death...
        }
        if (tickCounter % 20 == 0) {
            // Attribute modifiers are lost when you respawn.
            if (applied.contains(Feature.STEP_UP)) {
                setModifier(player, true, STEP_ID);
            }
            if (applied.contains(Feature.FAST_SWIM)) {
                setModifier(player, true, SWIM_ID);
            }
        }
        if (applied.contains(Feature.AUTO_EAT) && tickCounter % 10 == 0) {
            autoEat(player);
        }
        if (applied.contains(Feature.AUTO_TOTEM)) {
            autoTotem(player);
        }
        if (applied.contains(Feature.INFINITE_DURABILITY)) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = player.getEquippedStack(slot);
                if (stack.isDamageable() && stack.getDamage() > 0) {
                    stack.setDamage(0);
                }
            }
        }
    }

    private static void addDolphinsGrace(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE,
                StatusEffectInstance.INFINITE, 0, false, false, true));
    }

    /** Step-Up: step height 0.6 -> 1.1 blocks. Fast Swim: no slowdown walking in water. */
    private static void setModifier(ServerPlayerEntity player, boolean on, Identifier id) {
        var attribute = id == STEP_ID ? EntityAttributes.GENERIC_STEP_HEIGHT : EntityAttributes.GENERIC_WATER_MOVEMENT_EFFICIENCY;
        double value = id == STEP_ID ? 0.5 : 1.0;
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance == null) {
            return;
        }
        if (!on) {
            instance.removeModifier(id);
        } else if (instance.getModifier(id) == null) {
            instance.addTemporaryModifier(new EntityAttributeModifier(id, value, EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }

    /**
     * Auto-Eat: when your hunger bar has room for the food (so nothing is wasted), or you're low on health
     * and can't heal, eat the best food from your inventory, instantly.
     */
    private static void autoEat(ServerPlayerEntity player) {
        if (player.isCreative() || player.isSpectator() || player.isDead()) {
            return;
        }
        int hunger = player.getHungerManager().getFoodLevel();
        if (hunger >= 20) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int best = -1;
        float bestScore = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            FoodComponent food = stack.get(DataComponentTypes.FOOD);
            if (food == null || NEVER_EAT.contains(stack.getItem()) || !food.effects().isEmpty()) {
                continue;
            }
            // Wait until the whole food fits, unless you're starving (hunger 6 = can't sprint).
            if (hunger + food.nutrition() > 20 && hunger > 6) {
                continue;
            }
            float score = food.nutrition() + food.saturation();
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best < 0) {
            return;
        }
        ItemStack stack = inventory.getStack(best);
        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        // eatFood plays the sounds, feeds you and uses up one item (bowls come back for stews).
        ItemStack left = player.eatFood(player.getWorld(), stack, food);
        if (left != stack) {
            if (stack.isEmpty()) {
                inventory.setStack(best, left);
            } else if (!inventory.insertStack(left)) {
                player.dropItem(left, false);
            }
        }
    }

    /** Auto-Totem: a Totem of Undying in your off hand at all times, as long as you have one. */
    private static void autoTotem(ServerPlayerEntity player) {
        if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) || player.isDead()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.main.size(); i++) {
            if (inventory.main.get(i).isOf(Items.TOTEM_OF_UNDYING)) {
                ItemStack totem = inventory.main.get(i);
                // Swap: whatever was in your off hand goes where the totem was.
                inventory.main.set(i, player.getOffHandStack());
                player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, totem);
                return;
            }
        }
    }

    /**
     * Always Crit: called just before your hit is worked out (attack event). A critical hit needs you to be
     * falling; this makes the game think you're just past the top of a jump, for this one hit.
     */
    public static void onAttack(PlayerEntity player, World world, Entity target) {
        if (world.isClient() || !CheatState.isOn(Feature.ALWAYS_CRIT) || !(target instanceof LivingEntity)
                || !LocalHost.is(player)) {
            return;
        }
        if (player.fallDistance <= 0) {
            player.fallDistance = 0.1f;
        }
        player.setOnGround(false);
        player.setSprinting(false); // a sprint hit is a knockback hit, never a crit
    }
}
