package com.minemenu.build;

import com.minemenu.core.CheatConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Fill, Clear, schematics (.nbt files) and Biome Painter. Server thread only. */
public final class BuildTools {
    public static final Path SCHEMATICS = CheatConfig.DIR.resolve("schematics");

    private BuildTools() {
    }

    public static void fill(ServerPlayerEntity player, BlockState state, String name) {
        BlockBox box = Selection.require(player);
        if (box == null) {
            return;
        }
        List<BlockChange> changes = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
            changes.add(new BlockChange(pos.toImmutable(), state, null));
        }
        BlockPlacer.submit(player.getServerWorld(), changes, name, player, true);
    }

    public static void fillWithHeld(ServerPlayerEntity player) {
        if (!(player.getMainHandStack().getItem() instanceof BlockItem blockItem)) {
            player.sendMessage(Text.translatable("minemenu.text.hold_the_block_to_fill_with").formatted(Formatting.YELLOW), true);
            return;
        }
        fill(player, blockItem.getBlock().getDefaultState(), "minemenu.job.fill");
    }

    public static void clear(ServerPlayerEntity player) {
        fill(player, Blocks.AIR.getDefaultState(), "minemenu.job.clear");
    }

    // --- schematics ---

    /** File names in the schematics folder (without .nbt), sorted. */
    public static List<String> listSchematics() {
        List<String> names = new ArrayList<>();
        try {
            Files.createDirectories(SCHEMATICS);
            try (Stream<Path> files = Files.list(SCHEMATICS)) {
                files.filter(p -> p.toString().endsWith(".nbt")).forEach(p -> {
                    String file = p.getFileName().toString();
                    names.add(file.substring(0, file.length() - 4));
                });
            }
        } catch (Exception ignored) {
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static Path schematicFile(String name) {
        String safe = name.replaceAll("[^A-Za-z0-9 _.-]", "_").trim();
        return SCHEMATICS.resolve((safe.isEmpty() ? "schematic" : safe) + ".nbt");
    }

    public static void saveSchematic(ServerPlayerEntity player, String name) {
        StructureTemplate template = Clipboard.template();
        if (template == null) {
            player.sendMessage(Text.translatable("minemenu.text.copy_something_first").formatted(Formatting.YELLOW), true);
            return;
        }
        try {
            Files.createDirectories(SCHEMATICS);
            NbtIo.writeCompressed(template.writeNbt(new NbtCompound()), schematicFile(name));
            player.sendMessage(Text.translatable("minemenu.text.saved_schematic_s", name).formatted(Formatting.GREEN), true);
        } catch (Exception e) {
            player.sendMessage(Text.translatable("minemenu.text.couldn_t_save_s", e.getMessage()).formatted(Formatting.RED), true);
        }
    }

    public static void loadSchematic(ServerPlayerEntity player, String name) {
        MinecraftServer server = player.getServer();
        try {
            NbtCompound nbt = NbtIo.readCompressed(schematicFile(name), NbtSizeTracker.ofUnlimitedBytes());
            StructureTemplate template = new StructureTemplate();
            template.readNbt(server.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK), nbt);
            Clipboard.set(template);
            Clipboard.startPreview(player);
        } catch (Exception e) {
            player.sendMessage(Text.translatable("minemenu.text.couldn_t_load_s_s", name, e.getMessage()).formatted(Formatting.RED), true);
        }
    }

    // --- Biome Painter: like /fillbiome, in pieces the command accepts ---

    public static void paintBiome(ServerPlayerEntity player, String biomeId) {
        BlockBox box = Selection.require(player);
        if (box == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        var source = player.getCommandSource().withLevel(4).withSilent();
        int pieces = 0;
        for (int x = box.getMinX(); x <= box.getMaxX(); x += 16) {
            for (int z = box.getMinZ(); z <= box.getMaxZ(); z += 16) {
                for (int y = box.getMinY(); y <= box.getMaxY(); y += 128) {
                    int x2 = Math.min(box.getMaxX(), x + 15);
                    int z2 = Math.min(box.getMaxZ(), z + 15);
                    int y2 = Math.min(box.getMaxY(), y + 127);
                    server.getCommandManager().executeWithPrefix(source,
                            "fillbiome " + x + " " + y + " " + z + " " + x2 + " " + y2 + " " + z2 + " " + biomeId);
                    pieces++;
                }
            }
        }
        player.sendMessage(Text.translatable("minemenu.text.biome_set_to_s_s_pieces", biomeId, pieces)
                .formatted(Formatting.GREEN), true);
    }
}
