package com.minemenu.player;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.MineMenuClient;
import com.minemenu.ServerCheats;
import com.minemenu.core.CheatConfig;
import com.minemenu.core.CheatModule;
import com.minemenu.core.Guard;
import com.minemenu.core.KeyBinds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Set;

/**
 * Morph: you look like the mob you picked (right-click Morph to choose) and get some of its powers. On your screen
 * and in third person your body is drawn as that mob (a copy that's never added to the world, animated like you).
 * Spiders and cave spiders climb walls; bats, phantoms, blazes, ghasts, bees, vexes, allays and parrots fly;
 * R: blazes shoot small fireballs, ghasts big ones, endermen teleport where you look, creepers explode without
 * hurting you. U (or dying, leaving, changing dimension) turns you back. Your hitbox stays a player's.
 */
public final class Morph implements CheatModule {
    private static final Set<String> FLYERS = Set.of("bat", "phantom", "blaze", "ghast", "bee", "vex", "allay", "parrot", "ender_dragon", "wither");
    private static LivingEntity body;
    private static String bodyId;
    private static volatile String activeId;
    private static boolean grantedFlight;

    public static String mobId() {
        String text = CheatConfig.get().text("morph.mob", "spider").trim().toLowerCase().replace(' ', '_');
        return text.startsWith("minecraft:") ? text.substring(10) : text;
    }

    /** Read by the Spider Climb mixin (both threads). */
    public static boolean climbs() {
        String id = activeId;
        return id != null && (id.equals("spider") || id.equals("cave_spider"));
    }

    /** The copy drawn instead of you, or null. */
    public static LivingEntity body() {
        return body;
    }

    @Override
    public void clientTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        String id = mobId();
        activeId = id;
        if (body == null || !id.equals(bodyId) || body.getWorld() != client.world) {
            Identifier typeId = Identifier.tryParse("minecraft:" + id);
            EntityType<?> type = typeId == null ? null : Registries.ENTITY_TYPE.getOrEmpty(typeId).orElse(null);
            Entity created = type == null ? null : type.create(client.world);
            if (!(created instanceof LivingEntity living)) {
                player.sendMessage(Text.translatable("minemenu.text.morph_s_isn_t_a_mob", id).formatted(Formatting.RED), true);
                CheatState.toggle(Feature.MORPH);
                return;
            }
            body = living;
            bodyId = id;
        }
        // Copy your pose: where you look, how you walk.
        body.age = player.age;
        body.prevYaw = player.prevYaw;
        body.setYaw(player.getYaw());
        body.prevPitch = player.prevPitch;
        body.setPitch(player.getPitch());
        body.prevBodyYaw = player.prevBodyYaw;
        body.bodyYaw = player.bodyYaw;
        body.prevHeadYaw = player.prevHeadYaw;
        body.headYaw = player.headYaw;
        body.setOnGround(player.isOnGround());
        body.setSneaking(player.isSneaking());
        double speed = Math.min(1.0, Math.sqrt((player.getX() - player.prevX) * (player.getX() - player.prevX)
                + (player.getZ() - player.prevZ) * (player.getZ() - player.prevZ)) * 4);
        body.limbAnimator.updateLimbs((float) speed, 0.4f);
        while (KeyBinds.morphPower.wasPressed()) {
            onServer(client, Morph::power);
        }
        while (KeyBinds.unmorph.wasPressed()) {
            if (CheatState.isOn(Feature.MORPH)) {
                CheatState.toggle(Feature.MORPH);
            }
        }
    }

    private static void onServer(MinecraftClient client, java.util.function.Consumer<ServerPlayerEntity> action) {
        IntegratedServer server = client.getServer();
        if (server != null && MineMenuClient.isSingleplayer(client)) {
            server.execute(() -> {
                ServerPlayerEntity player = ServerCheats.host(server);
                if (player != null) {
                    Guard.run(Feature.MORPH, () -> action.accept(player));
                }
            });
        }
    }

    @Override
    public void serverTick(ServerPlayerEntity player, int tick) {
        boolean fly = FLYERS.contains(mobId());
        if (fly && !player.getAbilities().allowFlying) {
            player.getAbilities().allowFlying = true;
            player.sendAbilitiesUpdate();
            grantedFlight = true;
        } else if (!fly && grantedFlight) {
            dropFlight(player);
        }
        if (climbs()) {
            player.fallDistance = 0;
        }
    }

    @Override
    public void disable(MinecraftServer server, ServerPlayerEntity player) {
        activeId = null;
        if (player != null && grantedFlight) {
            dropFlight(player);
        }
    }

    private static void dropFlight(ServerPlayerEntity player) {
        grantedFlight = false;
        if (!player.isCreative() && !player.isSpectator() && !CheatState.isOn(Feature.FLY)) {
            player.getAbilities().allowFlying = false;
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();
        }
    }

    @Override
    public void reset() {
        body = null;
        bodyId = null;
        activeId = null;
    }

    /** Called every client tick: drop the copy when Morph is off. */
    public static void checkOff() {
        if (body != null && !CheatState.isOn(Feature.MORPH)) {
            body = null;
            bodyId = null;
            activeId = null;
        }
    }

    /** R: the mob's power. */
    private static void power(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVector();
        World world = player.getWorld();
        switch (mobId()) {
            case "blaze" -> {
                SmallFireballEntity fireball = new SmallFireballEntity(world, player, look.multiply(1.5));
                fireball.setPosition(player.getEyePos().add(look));
                world.spawnEntity(fireball);
            }
            case "ghast" -> {
                FireballEntity fireball = new FireballEntity(world, player, look.multiply(1.5), 2);
                fireball.setPosition(player.getEyePos().add(look.multiply(2)));
                world.spawnEntity(fireball);
            }
            case "enderman" -> ServerCheats.run(player.getServer(), ServerCheats.Action.TELEPORT);
            case "creeper" -> {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 5, 4, false, false));
                world.createExplosion(player, player.getX(), player.getY(), player.getZ(), 3.0f, World.ExplosionSourceType.MOB);
            }
            default -> player.sendMessage(Text.translatable("minemenu.text.this_mob_has_no_power_to").formatted(Formatting.YELLOW), true);
        }
    }

    /** Drawn instead of you (MorphRenderMixin). */
    public static void render(float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(body).render(body, yaw, tickDelta, matrices, consumers, light);
    }
}
