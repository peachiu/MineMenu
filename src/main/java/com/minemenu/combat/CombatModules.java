package com.minemenu.combat;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.LocalHost;
import com.minemenu.core.CheatModule;
import com.minemenu.core.FeatureSettings;
import com.minemenu.core.Guard;
import com.minemenu.core.Modules;
import com.minemenu.mixin.ProjectileAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bow Aimbot, Lifesteal, Force Field, Thorns Aura, Trident Storm and the Mob War action. Server side. */
public final class CombatModules {
    private static final Set<UUID> stormedTridents = new HashSet<>();

    private CombatModules() {
    }

    public static void register() {
        Modules.register(Feature.BOW_AIMBOT, new CheatModule() {
            @Override
            public void serverTick(ServerPlayerEntity player, int tick) {
                aimArrows(player);
            }
        });
        Modules.register(Feature.FORCE_FIELD, new CheatModule() {
            @Override
            public void serverTick(ServerPlayerEntity player, int tick) {
                double radius = FeatureSettings.get(Feature.FORCE_FIELD, "radius");
                for (LivingEntity mob : player.getServerWorld().getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius),
                        e -> e instanceof Monster && e.isAlive())) {
                    Vec3d away = mob.getPos().subtract(player.getPos()).multiply(1, 0, 1);
                    if (away.lengthSquared() < 1e-4) {
                        away = new Vec3d(1, 0, 0);
                    }
                    mob.setVelocity(away.normalize().multiply(0.8).add(0, 0.2, 0));
                    mob.velocityModified = true;
                }
            }
        });
        Modules.register(Feature.TRIDENT_STORM, new CheatModule() {
            @Override
            public void serverTick(ServerPlayerEntity player, int tick) {
                tridentStorm(player);
            }

            @Override
            public void reset() {
                stormedTridents.clear();
            }
        });
        Modules.register(Feature.LIFESTEAL, new CheatModule() { });
        Modules.register(Feature.THORNS_AURA, new CheatModule() { });
        Modules.register(Feature.MIND_CONTROL, new MindControl());
    }

    /** Lifesteal: called after any living entity took damage. */
    public static void afterDamage(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0 || !CheatState.isOn(Feature.LIFESTEAL) || !(source.getAttacker() instanceof ServerPlayerEntity player)
                || !LocalHost.is(player) || target == player) {
            return;
        }
        Guard.run(Feature.LIFESTEAL, () -> player.heal(amount * (float) (FeatureSettings.get(Feature.LIFESTEAL, "percent") / 100.0)));
    }

    /** Thorns Aura: called before the host takes damage (even if God Mode then blocks it). */
    public static void beforeHostDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!CheatState.isOn(Feature.THORNS_AURA) || !(entity instanceof ServerPlayerEntity player) || !LocalHost.is(player)) {
            return;
        }
        if (source.getAttacker() instanceof LivingEntity attacker && attacker != player && attacker.isAlive()) {
            Guard.run(Feature.THORNS_AURA, () -> attacker.damage(player.getDamageSources().thorns(player), amount * 2));
        }
    }

    /** Bow Aimbot: your flying arrows steer gently towards the hostile mob closest to their path. */
    private static void aimArrows(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        for (PersistentProjectileEntity arrow : world.getEntitiesByClass(PersistentProjectileEntity.class, player.getBoundingBox().expand(64),
                a -> a.getOwner() == player && !(a instanceof TridentEntity) && !((ProjectileAccessor) a).minemenu$isInGround() && a.age < 100)) {
            Vec3d velocity = arrow.getVelocity();
            double speed = velocity.length();
            if (speed < 0.3) {
                continue;
            }
            Vec3d direction = velocity.normalize();
            LivingEntity best = null;
            double bestScore = Double.MAX_VALUE;
            for (LivingEntity mob : world.getEntitiesByClass(LivingEntity.class, arrow.getBoundingBox().expand(32),
                    e -> e instanceof Monster && e.isAlive())) {
                Vec3d to = mob.getPos().add(0, mob.getHeight() * 0.6, 0).subtract(arrow.getPos());
                double along = to.dotProduct(direction);
                if (along <= 0) {
                    continue; // behind the arrow
                }
                double off = to.subtract(direction.multiply(along)).length();
                double score = off / along;
                if (score < 0.5 && score < bestScore) {
                    bestScore = score;
                    best = mob;
                }
            }
            if (best != null) {
                Vec3d to = best.getPos().add(0, best.getHeight() * 0.6, 0).subtract(arrow.getPos()).normalize();
                Vec3d steered = direction.multiply(0.7).add(to.multiply(0.3)).normalize().multiply(speed);
                arrow.setVelocity(steered);
                arrow.velocityModified = true;
            }
        }
    }

    /** Trident Storm: where your trident lands, lightning and a ring of tridents falling from the sky. */
    private static void tridentStorm(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        List<TridentEntity> tridents = world.getEntitiesByClass(TridentEntity.class, player.getBoundingBox().expand(96),
                t -> t.getOwner() == player && !stormedTridents.contains(t.getUuid()) && t.age > 2
                        && (((ProjectileAccessor) t).minemenu$isInGround() || t.getVelocity().lengthSquared() < 0.09));
        for (TridentEntity trident : tridents) {
            stormedTridents.add(trident.getUuid());
            Vec3d at = trident.getPos();
            for (int i = 0; i < 3; i++) {
                LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
                if (bolt != null) {
                    bolt.refreshPositionAfterTeleport(at.add(world.random.nextGaussian() * 2, 0, world.random.nextGaussian() * 2));
                    bolt.setChanneler(player);
                    world.spawnEntity(bolt);
                }
            }
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2 * i / 8;
                TridentEntity rain = new TridentEntity(world, player, new ItemStack(Items.TRIDENT));
                rain.refreshPositionAndAngles(at.x + Math.cos(angle) * 3, at.y + 15, at.z + Math.sin(angle) * 3, 0, 90);
                rain.setVelocity(0, -2.5, 0);
                rain.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
                stormedTridents.add(rain.getUuid());
                world.spawnEntity(rain);
            }
        }
        if (stormedTridents.size() > 512) {
            stormedTridents.clear();
        }
    }

    private static void fight(HostileEntity mob, HostileEntity enemy) {
        mob.setTarget(enemy);
        mob.setAttacker(enemy); // makes it keep fighting instead of going back to you
    }

    /** Mob War: the hostile mobs within 48 blocks pair up at random and fight each other. */
    public static void mobWar(ServerPlayerEntity player) {
        List<HostileEntity> mobs = new java.util.ArrayList<>(player.getServerWorld().getEntitiesByClass(HostileEntity.class, player.getBoundingBox().expand(48), e -> e.isAlive() && !e.isAiDisabled()));
        if (mobs.size() < 2) {
            player.sendMessage(Text.literal("Need at least 2 hostile mobs nearby").formatted(Formatting.YELLOW), true);
            return;
        }
        // Pairs that fight each other (a mob only hits back at its own target), the odd one out joins a pair.
        java.util.Collections.shuffle(mobs, new java.util.Random(player.getRandom().nextLong()));
        for (int i = 0; i + 1 < mobs.size(); i += 2) {
            fight(mobs.get(i), mobs.get(i + 1));
            fight(mobs.get(i + 1), mobs.get(i));
        }
        if (mobs.size() % 2 == 1) {
            fight(mobs.get(mobs.size() - 1), mobs.get(0));
        }
        player.sendMessage(Text.literal("Mob War! " + mobs.size() + " mobs are fighting each other").formatted(Formatting.RED), true);
    }
}
