package com.minemenu;

import com.mojang.datafixers.util.Pair;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureKeys;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The MineMenu 3.3 world hacks (Chest ESP moved to the client ESP in 3.4): Growth Aura, Fast Breed, the Time &amp; Weather controls and
 * Structure Finder. Server thread only, host only.
 */
public final class WorldCheats {
    private static final int GROW_RADIUS = 8;
    private static final int BREED_RADIUS = 16;


    /** Day speed: 0 = time stopped, 1 = normal, 2+ = that many times faster. */
    private static volatile int daySpeed = 1;

    private WorldCheats() {
    }

    public static void onServerStarted(MinecraftServer server) {
        daySpeed = 1;
    }

    public static void disable(MinecraftServer server, ServerPlayerEntity player, Feature feature) {
    }

    /** Puts the day speed back to normal (before the world saves, so a stopped clock doesn't stick). */
    public static void revert(MinecraftServer server) {
        setDaySpeed(server, 1);
    }

    public static void tick(ServerPlayerEntity player, Set<Feature> applied, int tickCounter) {
        MinecraftServer server = player.getServer();
        if (daySpeed > 1 && server != null && !applied.contains(Feature.ALWAYS_DAY)
                && server.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) {
            ServerWorld overworld = server.getOverworld();
            overworld.setTimeOfDay(overworld.getTimeOfDay() + daySpeed - 1); // the normal +1 happens anyway
        }
        if (tickCounter % 200 == 0) {
            removeStrayMarkers(player, 64);
        }
        if (applied.contains(Feature.GROWTH_AURA) && tickCounter % 20 == 0) {
            growthAura(player);
        }
        if (applied.contains(Feature.FAST_BREED) && tickCounter % 20 == 0) {
            for (AnimalEntity animal : player.getServerWorld().getEntitiesByClass(AnimalEntity.class,
                    player.getBoundingBox().expand(BREED_RADIUS), AnimalEntity::isAlive)) {
                // Babies grow up, and parents are ready to breed again right away.
                if (animal.getBreedingAge() != 0) {
                    animal.setBreedingAge(0);
                }
            }
        }
    }

    /** Old Ore Vision / Chest ESP markers (3.3 used entities): never saved, but remove any that are left over. */
    private static void removeStrayMarkers(ServerPlayerEntity player, double radius) {
        for (DisplayEntity.BlockDisplayEntity stray : player.getServerWorld().getEntitiesByType(EntityType.BLOCK_DISPLAY,
                player.getBoundingBox().expand(radius), e -> e.getCommandTags().contains("minemenu_chest_marker")
                        || e.getCommandTags().contains("minemenu_ore_marker"))) {
            stray.discard();
        }
    }

    // ------------------------------------------------------------------
    // Growth Aura: one bone meal step for plants around you, every second
    // ------------------------------------------------------------------

    private static void growthAura(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos center = player.getBlockPos();
        for (BlockPos pos : BlockPos.iterate(center.add(-GROW_RADIUS, -3, -GROW_RADIUS), center.add(GROW_RADIUS, 3, GROW_RADIUS))) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof NetherWartBlock && state.get(NetherWartBlock.AGE) < NetherWartBlock.MAX_AGE) {
                if (world.random.nextInt(3) == 0) {
                    world.setBlockState(pos, state.with(NetherWartBlock.AGE, state.get(NetherWartBlock.AGE) + 1));
                }
                continue;
            }
            // Half of the plants each second, so a field grows over a few seconds instead of all at once.
            if (MoreCheats.isGrowable(state) && state.getBlock() instanceof Fertilizable fertilizable
                    && fertilizable.isFertilizable(world, pos, state) && world.random.nextBoolean()) {
                fertilizable.grow(world, world.random, pos.toImmutable(), state);
            }
        }
    }

    // ------------------------------------------------------------------
    // Time & Weather
    // ------------------------------------------------------------------

    public static int daySpeed() {
        return daySpeed;
    }

    public static void setDaySpeed(MinecraftServer server, int speed) {
        if (speed == 0) {
            com.minemenu.core.DayCycle.stop(server, com.minemenu.core.DayCycle.Reason.DAY_SPEED_STOPPED);
        } else {
            com.minemenu.core.DayCycle.release(server, com.minemenu.core.DayCycle.Reason.DAY_SPEED_STOPPED);
        }
        daySpeed = speed;
    }

    /** Sets the clock to {@code ticks} (0-23999) of the current day. */
    public static void setTime(MinecraftServer server, int ticks) {
        ServerWorld overworld = server.getOverworld();
        long day = Math.floorDiv(overworld.getTimeOfDay(), 24000L);
        overworld.setTimeOfDay(day * 24000L + ticks);
    }

    public enum Weather { CLEAR, RAIN, THUNDER }

    public static void setWeather(MinecraftServer server, Weather weather) {
        ServerWorld overworld = server.getOverworld();
        switch (weather) {
            case CLEAR -> overworld.setWeather(24000 * 5, 0, false, false);
            case RAIN -> overworld.setWeather(0, 24000, true, false);
            case THUNDER -> overworld.setWeather(0, 12000, true, true);
        }
    }

    // ------------------------------------------------------------------
    // Structure Finder
    // ------------------------------------------------------------------

    /** The structures you can search for, with the game's structure ids behind each one. */
    public enum Target {
        VILLAGE("Village", StructureKeys.VILLAGE_PLAINS, StructureKeys.VILLAGE_DESERT, StructureKeys.VILLAGE_SAVANNA,
                StructureKeys.VILLAGE_SNOWY, StructureKeys.VILLAGE_TAIGA),
        STRONGHOLD("Stronghold", StructureKeys.STRONGHOLD),
        MANSION("Woodland Mansion", StructureKeys.MANSION),
        MONUMENT("Ocean Monument", StructureKeys.MONUMENT),
        ANCIENT_CITY("Ancient City", StructureKeys.ANCIENT_CITY),
        TRIAL_CHAMBERS("Trial Chambers", StructureKeys.TRIAL_CHAMBERS),
        DESERT_TEMPLE("Desert Temple", StructureKeys.DESERT_PYRAMID),
        JUNGLE_TEMPLE("Jungle Temple", StructureKeys.JUNGLE_PYRAMID),
        PILLAGER_OUTPOST("Pillager Outpost", StructureKeys.PILLAGER_OUTPOST),
        WITCH_HUT("Witch Hut", StructureKeys.SWAMP_HUT),
        IGLOO("Igloo", StructureKeys.IGLOO),
        SHIPWRECK("Shipwreck", StructureKeys.SHIPWRECK, StructureKeys.SHIPWRECK_BEACHED),
        BURIED_TREASURE("Buried Treasure", StructureKeys.BURIED_TREASURE),
        RUINED_PORTAL("Ruined Portal", StructureKeys.RUINED_PORTAL, StructureKeys.RUINED_PORTAL_DESERT,
                StructureKeys.RUINED_PORTAL_JUNGLE, StructureKeys.RUINED_PORTAL_SWAMP, StructureKeys.RUINED_PORTAL_MOUNTAIN,
                StructureKeys.RUINED_PORTAL_OCEAN, StructureKeys.RUINED_PORTAL_NETHER),
        MINESHAFT("Mineshaft", StructureKeys.MINESHAFT, StructureKeys.MINESHAFT_MESA),
        TRAIL_RUINS("Trail Ruins", StructureKeys.TRAIL_RUINS),
        FORTRESS("Nether Fortress", StructureKeys.FORTRESS),
        BASTION("Bastion", StructureKeys.BASTION_REMNANT),
        END_CITY("End City", StructureKeys.END_CITY);

        private final String displayName;
        private final List<RegistryKey<Structure>> keys;

        @SafeVarargs
        Target(String displayName, RegistryKey<Structure>... keys) {
            this.displayName = displayName;
            this.keys = List.of(keys);
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * Finds the nearest structure of that kind in your dimension (the same search as /locate, up to
     * 100 chunks away) and makes it the Structure Finder target in your HUD.
     */
    public static void findStructure(ServerPlayerEntity player, Target target) {
        ServerWorld world = player.getServerWorld();
        var registry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
        List<RegistryEntry<Structure>> entries = new ArrayList<>();
        for (RegistryKey<Structure> key : target.keys) {
            registry.getEntry(key).ifPresent(entries::add);
        }
        Pair<BlockPos, RegistryEntry<Structure>> found = entries.isEmpty() ? null
                : world.getChunkManager().getChunkGenerator().locateStructure(world, RegistryEntryList.of(entries),
                player.getBlockPos(), 100, false);
        if (found == null) {
            Waypoints.setFound(null);
            player.sendMessage(Text.literal("No " + target.displayName() + " found in this dimension (searched about 1600 blocks)")
                    .formatted(Formatting.YELLOW), false);
            return;
        }
        BlockPos pos = found.getFirst();
        int distance = (int) Math.sqrt(player.getBlockPos().getSquaredDistance(pos.getX(), player.getY(), pos.getZ()));
        Waypoints.setFound(new Waypoints.Found(target.displayName(), world.getRegistryKey().getValue().toString(),
                pos.getX() + 0.5, pos.getZ() + 0.5));
        player.sendMessage(Text.literal(target.displayName() + " found at " + pos.getX() + ", " + pos.getZ() + " (" + distance
                + " blocks away). Follow the arrow in your HUD.").formatted(Formatting.GREEN), false);
    }
}
