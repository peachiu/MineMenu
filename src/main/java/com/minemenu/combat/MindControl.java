package com.minemenu.combat;

import com.minemenu.MineMenuClient;
import com.minemenu.ServerCheats;
import com.minemenu.core.CheatModule;
import com.minemenu.core.Guard;
import com.minemenu.core.KeyBinds;
import com.minemenu.core.Look;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Mind Control: look at a mob and press N. For 60 seconds it fights the hostile mobs around it instead of you,
 * bosses included (except the Ender Dragon, whose flight path isn't target-based). Unlike Tame Anything it
 * wears off, and the mob keeps its own AI.
 */
public final class MindControl implements CheatModule {
    private static final int DURATION = 20 * 60;
    private static final Map<UUID, Integer> controlled = new HashMap<>();
    private static int now;

    @Override
    public void clientTick(MinecraftClient client) {
        while (KeyBinds.mindControl.wasPressed()) {
            IntegratedServer server = client.getServer();
            if (server != null && MineMenuClient.isSingleplayer(client)) {
                server.execute(() -> {
                    ServerPlayerEntity player = ServerCheats.host(server);
                    if (player != null) {
                        Guard.run("Mind Control", () -> take(player));
                    }
                });
            }
        }
    }

    private static void take(ServerPlayerEntity player) {
        LivingEntity target = Look.livingEntity(player, 32);
        if (!(target instanceof MobEntity mob)) {
            player.sendMessage(Text.literal("Look at a mob, then press the Mind Control key").formatted(Formatting.YELLOW), true);
            return;
        }
        if (mob instanceof EnderDragonEntity) {
            player.sendMessage(Text.literal("The Ender Dragon can't be mind-controlled").formatted(Formatting.YELLOW), true);
            return;
        }
        controlled.put(mob.getUuid(), now + DURATION);
        mob.setTarget(null);
        player.getServerWorld().spawnParticles(ParticleTypes.WITCH, mob.getX(), mob.getBodyY(1.0), mob.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
        player.sendMessage(Text.literal(mob.getName().getString() + " is under your control for 60 seconds").formatted(Formatting.LIGHT_PURPLE), true);
    }

    @Override
    public void serverTick(ServerPlayerEntity player, int tick) {
        now = tick;
        if (controlled.isEmpty() || tick % 5 != 0) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        Iterator<Map.Entry<UUID, Integer>> it = controlled.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            Entity entity = world.getEntity(entry.getKey());
            if (!(entity instanceof MobEntity mob) || !mob.isAlive() || tick >= entry.getValue()) {
                if (entity instanceof MobEntity mob && mob.getTarget() != null && !(mob.getTarget() instanceof Monster)) {
                    mob.setTarget(null);
                }
                it.remove();
                continue;
            }
            LivingEntity enemy = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity other : world.getEntitiesByClass(LivingEntity.class, mob.getBoundingBox().expand(24),
                    e -> e instanceof Monster && e != mob && e.isAlive() && !controlled.containsKey(e.getUuid()))) {
                double d = other.squaredDistanceTo(mob);
                if (d < best) {
                    best = d;
                    enemy = other;
                }
            }
            if (enemy != null) {
                mob.setTarget(enemy);
                if (mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE) == null) {
                    mob.getNavigation().startMovingTo(enemy, 1.3);
                    if (best < 2.5 * 2.5 && tick % 20 == 0) {
                        enemy.damage(world.getDamageSources().mobAttack(mob), 4);
                    }
                }
            } else if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
        }
    }

    @Override
    public void reset() {
        controlled.clear();
    }

    /** For tests. */
    public static int count() {
        return controlled.size();
    }
}
