package com.minemenu.build;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla structures (villages, temples, igloos...) placed the same way as {@code /place structure}, while
 * {@link BlockRecorder} remembers every block they change, so Undo can put the area back exactly. Mobs that come
 * with a structure (villagers...) stay after Undo.
 */
public final class VanillaStructures {
    /** Bigger changes are kept without Undo (it would need too much memory). */
    private static final int MAX_UNDO_BLOCKS = 3_000_000;

    private VanillaStructures() {
    }

    public static void place(ServerPlayerEntity player, String id, BlockPos at) {
        ServerWorld world = player.getServerWorld();
        Identifier structureId = Identifier.tryParse(id);
        var entry = structureId == null ? null
                : world.getRegistryManager().get(RegistryKeys.STRUCTURE).getEntry(RegistryKey.of(RegistryKeys.STRUCTURE, structureId)).orElse(null);
        if (entry == null) {
            player.sendMessage(Text.translatable("minemenu.structures.unknown", id).formatted(Formatting.RED), true);
            return;
        }
        Structure structure = entry.value();
        ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
        StructureStart start = structure.createStructureStart(world.getRegistryManager(), generator, generator.getBiomeSource(),
                world.getChunkManager().getNoiseConfig(), world.getStructureTemplateManager(), world.getSeed(), new ChunkPos(at), 0,
                world, biome -> true);
        if (!start.hasChildren()) {
            player.sendMessage(Text.translatable("minemenu.structures.cant_place").formatted(Formatting.YELLOW), true);
            return;
        }
        BlockBox box = start.getBoundingBox();
        ChunkPos from = new ChunkPos(ChunkSectionPos.getSectionCoord(box.getMinX()), ChunkSectionPos.getSectionCoord(box.getMinZ()));
        ChunkPos to = new ChunkPos(ChunkSectionPos.getSectionCoord(box.getMaxX()), ChunkSectionPos.getSectionCoord(box.getMaxZ()));
        for (int cx = from.x; cx <= to.x; cx++) {
            for (int cz = from.z; cz <= to.z; cz++) {
                if (!world.getChunkManager().isChunkLoaded(cx, cz)) {
                    player.sendMessage(Text.translatable("minemenu.structures.not_loaded").formatted(Formatting.YELLOW), true);
                    return;
                }
            }
        }
        String name = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        // Record every block the structure changes while it's placed (it picks its final height only then).
        List<BlockChange> before = BlockRecorder.record(world, () -> ChunkPos.stream(from, to).forEach(chunk ->
                start.place(world, world.getStructureAccessor(), generator, world.getRandom(),
                        new BlockBox(chunk.getStartX(), world.getBottomY(), chunk.getStartZ(), chunk.getEndX(), world.getTopY(), chunk.getEndZ()),
                        chunk)));
        if (before.size() > MAX_UNDO_BLOCKS) {
            player.sendMessage(Text.translatable("minemenu.structures.placed_no_undo", name).formatted(Formatting.YELLOW), true);
            return;
        }
        List<BlockChange> after = new ArrayList<>(before.size());
        for (BlockChange change : before) {
            after.add(new BlockChange(change.pos(), world.getBlockState(change.pos()), data(world, change.pos())));
        }
        UndoStack.push(new UndoStack.Entry(name, world, before, after));
        player.sendMessage(Text.translatable("minemenu.structures.placed", name, before.size()).formatted(Formatting.GREEN), true);
    }

    private static NbtCompound data(ServerWorld world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity == null ? null : blockEntity.createNbtWithIdentifyingData(world.getRegistryManager());
    }
}
