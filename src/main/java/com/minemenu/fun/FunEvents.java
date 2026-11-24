package com.minemenu.fun;

import com.minemenu.core.ServerTasks;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/** Meteor Strike, Tornado and the Chaos Mode events. Server thread only. */
public final class FunEvents {
    private FunEvents() {
    }

    private static Vec3d lookTarget(ServerPlayerEntity player) {
        HitResult hit = player.raycast(128, 1.0f, false);
        return hit.getPos();
    }

    /** A meteor falls where you look: a burning trail, a big explosion, then ores in the crater. */
    public static void meteor(ServerPlayerEntity player, Vec3d at) {
        ServerWorld world = player.getServerWorld();
        Vec3d start = at.add(world.random.nextGaussian() * 6, 60, world.random.nextGaussian() * 6);
        int[] t = {0};
        ServerTasks.repeat("Meteor", server -> {
            double progress = Math.min(1, ++t[0] / 30.0);
            Vec3d pos = start.lerp(at, progress);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 12, 0.6, 0.6, 0.6, 0.02);
            world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 20, 0.5, 0.5, 0.5, 0.05);
            if (progress < 1) {
                return true;
            }
            world.createExplosion(null, at.x, at.y, at.z, 6.0f, true, World.ExplosionSourceType.TNT);
            BlockPos center = BlockPos.ofFloored(at).down(2);
            for (int i = 0; i < 14; i++) {
                BlockPos pos2 = center.add(world.random.nextInt(7) - 3, world.random.nextInt(3) - 1, world.random.nextInt(7) - 3);
                var ore = switch (world.random.nextInt(6)) {
                    case 0 -> Blocks.DIAMOND_ORE;
                    case 1 -> Blocks.GOLD_ORE;
                    case 2 -> Blocks.EMERALD_ORE;
                    case 3 -> Blocks.ANCIENT_DEBRIS;
                    default -> Blocks.MAGMA_BLOCK;
                };
                world.setBlockState(pos2, ore.getDefaultState());
            }
            return false;
        });
        player.sendMessage(Text.literal("Incoming meteor!").formatted(Formatting.GOLD), true);
    }

    public static void meteorAtLook(ServerPlayerEntity player) {
        meteor(player, lookTarget(player));
    }

    /** A tornado for 20 seconds: pulls mobs and items into a spiral; never breaks blocks; never takes you. */
    public static void tornado(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d[] center = {lookTarget(player)};
        int[] t = {0};
        ServerTasks.repeat("Tornado", server -> {
            if (++t[0] > 400) {
                return false;
            }
            center[0] = center[0].add(world.random.nextGaussian() * 0.15, 0, world.random.nextGaussian() * 0.15);
            Vec3d c = center[0];
            for (int i = 0; i < 6; i++) {
                double h = world.random.nextDouble() * 12;
                double angle = t[0] * 0.5 + i;
                double r = 1 + h * 0.35;
                world.spawnParticles(ParticleTypes.CLOUD, c.x + Math.cos(angle) * r, c.y + h, c.z + Math.sin(angle) * r, 1, 0, 0, 0, 0);
            }
            for (Entity entity : world.getOtherEntities(null, new net.minecraft.util.math.Box(c, c).expand(10, 12, 10),
                    e -> (e instanceof LivingEntity || e instanceof ItemEntity) && !(e instanceof PlayerEntity))) {
                Vec3d to = c.subtract(entity.getPos()).multiply(1, 0, 1);
                Vec3d tangent = new Vec3d(-to.z, 0, to.x).normalize().multiply(0.5);
                entity.setVelocity(to.normalize().multiply(0.25).add(tangent).add(0, entity.getY() < c.y + 10 ? 0.35 : -0.1, 0));
                entity.velocityModified = true;
                entity.fallDistance = 0;
            }
            return true;
        });
        player.sendMessage(Text.literal("A tornado appears!").formatted(Formatting.GRAY), true);
    }

    /** Chaos events by id (see ChaosMode.EVENTS). */
    public static void chaos(ServerPlayerEntity player, String event) {
        ServerWorld world = player.getServerWorld();
        Vec3d here = player.getPos();
        switch (event) {
            case "meteors" -> {
                for (int i = 0; i < 4; i++) {
                    Vec3d at = here.add(world.random.nextGaussian() * 25, 0, world.random.nextGaussian() * 25);
                    BlockPos top = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, BlockPos.ofFloored(at));
                    int delay = i * 30;
                    ServerTasks.later("Meteor", delay, server -> meteor(player, Vec3d.ofCenter(top)));
                }
            }
            case "tnt" -> {
                for (int i = 0; i < 10; i++) {
                    TntEntity tnt = new TntEntity(world, here.x + world.random.nextGaussian() * 6, here.y + 15, here.z + world.random.nextGaussian() * 6, null);
                    tnt.setFuse(50);
                    tnt.addCommandTag("minemenu_harmless");
                    world.spawnEntity(tnt);
                }
                // Harmless: just before they'd go off, they're replaced by a firework-like pop.
                ServerTasks.later("Harmless TNT", 48, server -> {
                    for (TntEntity tnt : world.getEntitiesByClass(TntEntity.class, player.getBoundingBox().expand(40),
                            e -> e.getCommandTags().contains("minemenu_harmless"))) {
                        world.spawnParticles(ParticleTypes.EXPLOSION, tnt.getX(), tnt.getY(), tnt.getZ(), 3, 0.5, 0.5, 0.5, 0);
                        world.playSound(null, tnt.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, net.minecraft.sound.SoundCategory.NEUTRAL, 2, 1);
                        tnt.discard();
                    }
                });
            }
            case "babies" -> {
                for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(48), Entity::isAlive)) {
                    mob.setBaby(true);
                }
            }
            case "low_gravity" -> {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 30 * 20, 0));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 30 * 20, 3));
            }
            case "diamonds" -> {
                for (int i = 0; i < 24; i++) {
                    ItemEntity item = new ItemEntity(world, here.x + world.random.nextGaussian() * 8, here.y + 20 + world.random.nextInt(10),
                            here.z + world.random.nextGaussian() * 8, new ItemStack(Items.DIAMOND));
                    world.spawnEntity(item);
                }
            }
            case "invasion" -> {
                List<EntityType<?>> types = List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER);
                for (int i = 0; i < 10; i++) {
                    double angle = Math.PI * 2 * i / 10;
                    BlockPos at = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,
                            BlockPos.ofFloored(here.x + Math.cos(angle) * 14, here.y, here.z + Math.sin(angle) * 14));
                    types.get(world.random.nextInt(types.size())).spawn(world, at, SpawnReason.EVENT);
                }
            }
            case "darkness" -> {
                world.setTimeOfDay(world.getTimeOfDay() / 24000 * 24000 + 18000);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 20 * 20, 0));
            }
            case "swap" -> swapWithMob(player);
            default -> {
            }
        }
    }

    /** Swap places with a random mob within 32 blocks. */
    public static boolean swapWithMob(ServerPlayerEntity player) {
        List<MobEntity> mobs = player.getServerWorld().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(32), Entity::isAlive);
        if (mobs.isEmpty()) {
            return false;
        }
        MobEntity mob = mobs.get(player.getRandom().nextInt(mobs.size()));
        Vec3d me = player.getPos();
        Vec3d it = mob.getPos();
        mob.refreshPositionAndAngles(me.x, me.y, me.z, mob.getYaw(), mob.getPitch());
        player.networkHandler.requestTeleport(it.x, it.y, it.z, player.getYaw(), player.getPitch());
        player.fallDistance = 0;
        player.sendMessage(Text.literal("Swapped places with a " + mob.getName().getString() + "!").formatted(Formatting.LIGHT_PURPLE), true);
        return true;
    }
}
