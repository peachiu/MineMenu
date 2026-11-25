package com.minemenu;

import com.minemenu.mixin.MobEntityAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Tame Anything: right-click a mob with an empty hand and it becomes your pet. Pets follow you, never
 * attack you, and go after whatever you hit. Mobs that can't normally attack (cows, pigs...) ram the
 * target for 4 damage a second. Sneak + right-click a pet to let it go.
 *
 * <p>Pets are marked with a command tag, which is saved with the mob, so they stay your pets after
 * you reload the world.
 */
public final class Pets {
    private static final String PET_TAG = "minemenu_pet";
    private static final double RANGE = 48;

    /** Pets whose own "find a target" AI we already removed (it isn't saved, so redo it after a reload). */
    private static final Set<MobEntity> calmed = Collections.newSetFromMap(new WeakHashMap<>());
    /** What you last hit; pets go after it (server thread only). */
    private static UUID enemy;

    private Pets() {
    }

    public static void onServerStarted(MinecraftServer server) {
        calmed.clear();
        enemy = null;
    }

    public static boolean isPet(Entity entity) {
        return entity instanceof MobEntity && entity.getCommandTags().contains(PET_TAG);
    }

    /** Right-click on a mob (UseEntityCallback). Runs on both the client and the server; both must agree. */
    public static ActionResult onUse(PlayerEntity player, World world, Hand hand, Entity entity) {
        if (!CheatState.isOn(Feature.TAME_ANYTHING) || hand != Hand.MAIN_HAND || !player.getMainHandStack().isEmpty()
                || !(entity instanceof MobEntity mob) || entity instanceof EnderDragonEntity || entity instanceof WitherEntity
                || !LocalHost.is(player)) {
            return ActionResult.PASS;
        }
        if (CheatState.isOn(Feature.GRAVITY_GUN) && player.isSneaking()) {
            return ActionResult.PASS; // sneak + right-click belongs to the Gravity Gun
        }
        boolean pet = isPet(mob);
        if (pet && !player.isSneaking()) {
            return ActionResult.PASS; // a normal right-click on your pet (ride your horse, trade...)
        }
        if (!world.isClient()) {
            if (pet) {
                mob.removeCommandTag(PET_TAG);
                player.sendMessage(Text.translatable("minemenu.text.s_is_free_again", mob.getName().getString()).formatted(Formatting.YELLOW), true);
            } else {
                tame(mob);
                player.sendMessage(Text.translatable("minemenu.text.s_is_now_your_pet", mob.getName().getString()).formatted(Formatting.GREEN), true);
            }
        }
        return ActionResult.SUCCESS;
    }

    private static void tame(MobEntity mob) {
        mob.addCommandTag(PET_TAG);
        mob.setPersistent(); // never despawns
        calm(mob);
        if (mob.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.HEART, mob.getX(), mob.getBodyY(0.8), mob.getZ(), 7, 0.4, 0.4, 0.4, 0.1);
        }
    }

    /** Removes the mob's own target-finding AI, so it never picks you (or anything else) by itself. */
    private static void calm(MobEntity mob) {
        ((MobEntityAccessor) mob).minemenu$getTargetSelector().clear(goal -> true);
        mob.setTarget(null);
        if (mob instanceof Angerable angerable) {
            angerable.stopAnger();
        }
        calmed.add(mob);
    }

    /** You hit something: your pets go after it (attack event). */
    public static void onAttack(PlayerEntity player, World world, Entity target) {
        if (!world.isClient() && CheatState.isOn(Feature.TAME_ANYTHING) && target instanceof LivingEntity
                && !isPet(target) && LocalHost.is(player)) {
            enemy = target.getUuid();
        }
    }

    public static void tick(ServerPlayerEntity player, Set<Feature> applied, int tickCounter) {
        if (!applied.contains(Feature.TAME_ANYTHING) || tickCounter % 5 != 0) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        LivingEntity target = null;
        if (enemy != null) {
            Entity found = world.getEntity(enemy);
            if (found instanceof LivingEntity living && living.isAlive() && living.squaredDistanceTo(player) < RANGE * RANGE) {
                target = living;
            } else {
                enemy = null;
            }
        }
        for (MobEntity pet : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(RANGE), Pets::isPet)) {
            if (!calmed.contains(pet)) {
                calm(pet);
            }
            if (target != null && target != pet) {
                attack(pet, target, tickCounter);
            } else {
                if (pet.getTarget() != null) {
                    pet.setTarget(null);
                }
                follow(pet, player);
            }
        }
    }

    private static void attack(MobEntity pet, LivingEntity target, int tickCounter) {
        if (pet.getTarget() != target) {
            pet.setTarget(target); // mobs that can fight (zombies, skeletons, wolves...) use their own attacks
        }
        boolean canFight = pet.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE) != null;
        double distance = pet.squaredDistanceTo(target);
        if (!canFight || distance > 6 * 6) {
            pet.getNavigation().startMovingTo(target, 1.3);
        }
        // Cows, pigs, sheep...: ram it once a second.
        if (!canFight && tickCounter % 20 == 0 && distance < 2.5 * 2.5 && pet.getWorld() instanceof ServerWorld world) {
            target.damage(world.getDamageSources().mobAttack(pet), 4.0f);
        }
    }

    private static void follow(MobEntity pet, ServerPlayerEntity player) {
        double distance = pet.squaredDistanceTo(player);
        if (distance > 24 * 24 && !pet.hasVehicle() && !pet.hasPassengers()) {
            // Too far behind: catch up, like a tamed wolf does.
            pet.refreshPositionAndAngles(player.getX() + pet.getRandom().nextBetween(-2, 2), player.getY(),
                    player.getZ() + pet.getRandom().nextBetween(-2, 2), pet.getYaw(), pet.getPitch());
            pet.getNavigation().stop();
        } else if (distance > 5 * 5) {
            pet.getNavigation().startMovingTo(player, 1.2);
        }
    }
}
