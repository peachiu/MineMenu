package com.minemeboost.test;

import com.minemenu.ExtraCheats;
import com.minemenu.ServerCheats;
import com.minemenu.Waypoints;
import com.minemenu.build.BlockPlacer;
import com.minemenu.build.BuildTools;
import com.minemenu.build.Clipboard;
import com.minemenu.build.Selection;
import com.minemenu.build.UndoStack;
import com.minemenu.items.Creators;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Map;

/**
 * Server-side tests of the most important hacks, run in a real (dedicated test) server by ./gradlew runGametest.
 * They call the hacks' logic directly: the client parts (menus, keys, the singleplayer host check) can't run on
 * a test server.
 */
@SuppressWarnings("removal") // the mock player helper is deprecated in 1.21.1 but still the simplest way in a GameTest
public class MineMeBoostGameTests implements FabricGameTest {
    private static void finishBuilding() {
        for (int i = 0; i < 200 && BlockPlacer.busy(); i++) {
            BlockPlacer.tick();
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void godModeHealsAndFeeds(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.setHealth(2);
        player.getHungerManager().setFoodLevel(3);
        player.setOnFireFor(5);
        ServerCheats.keepAlive(player);
        context.assertTrue(player.getHealth() == player.getMaxHealth(), "God Mode should restore full health");
        context.assertTrue(player.getHungerManager().getFoodLevel() == 20, "God Mode should fill the hunger bar");
        context.assertTrue(!player.isOnFire(), "God Mode should put out fire");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void veinMinerTakesTheWholeTree(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        ServerWorld world = context.getWorld();
        for (int y = 1; y <= 5; y++) {
            context.setBlockState(1, y, 1, Blocks.OAK_LOG);
        }
        BlockPos first = context.getAbsolutePos(new BlockPos(1, 1, 1));
        var state = world.getBlockState(first);
        world.breakBlock(first, false);
        ExtraCheats.veinMine(world, player, first, state, new ArrayList<>());
        for (int y = 2; y <= 5; y++) {
            context.assertTrue(world.getBlockState(context.getAbsolutePos(new BlockPos(1, y, 1))).isAir(), "log " + y + " should be gone");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void copyPasteAndUndo(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        ServerWorld world = context.getWorld();
        for (BlockPos pos : BlockPos.iterate(0, 1, 0, 1, 2, 1)) {
            context.setBlockState(pos, Blocks.STONE);
        }
        Selection.set(1, context.getAbsolutePos(new BlockPos(0, 1, 0)), player);
        Selection.set(2, context.getAbsolutePos(new BlockPos(1, 2, 1)), player);
        Clipboard.copy(player);
        Clipboard.previewing = true;
        Clipboard.anchor = context.getAbsolutePos(new BlockPos(3, 1, 0));
        Clipboard.paste(player);
        finishBuilding();
        context.assertTrue(world.getBlockState(context.getAbsolutePos(new BlockPos(4, 2, 1))).isOf(Blocks.STONE), "the copy should be pasted");
        UndoStack.undo(player);
        finishBuilding();
        context.assertTrue(world.getBlockState(context.getAbsolutePos(new BlockPos(4, 2, 1))).isAir(), "Undo should remove the paste");
        UndoStack.redo(player);
        finishBuilding();
        context.assertTrue(world.getBlockState(context.getAbsolutePos(new BlockPos(4, 2, 1))).isOf(Blocks.STONE), "Redo should put it back");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fillAndClear(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        ServerWorld world = context.getWorld();
        Selection.set(1, context.getAbsolutePos(new BlockPos(0, 1, 0)), player);
        Selection.set(2, context.getAbsolutePos(new BlockPos(2, 3, 2)), player);
        BuildTools.fill(player, Blocks.GLASS.getDefaultState(), "Fill");
        finishBuilding();
        context.assertTrue(world.getBlockState(context.getAbsolutePos(new BlockPos(1, 2, 1))).isOf(Blocks.GLASS), "Fill should fill the selection");
        BuildTools.clear(player);
        finishBuilding();
        context.assertTrue(world.getBlockState(context.getAbsolutePos(new BlockPos(1, 2, 1))).isAir(), "Clear should empty it");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void waypointsSaveAndTeleport(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        Waypoints.onServerStarted(context.getWorld().getServer());
        BlockPos home = context.getAbsolutePos(new BlockPos(1, 1, 1));
        player.refreshPositionAndAngles(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0, 0);
        Waypoints.setHere(player, 0, "Test");
        context.assertTrue(Waypoints.get(0) != null && "Test".equals(Waypoints.get(0).name), "the waypoint should be saved");
        player.refreshPositionAndAngles(home.getX() + 30, home.getY(), home.getZ(), 0, 0);
        Waypoints.go(player, 0);
        context.assertTrue(player.squaredDistanceTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) < 1, "Go should teleport back");
        Waypoints.delete(0);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itemCreatorMakesTheItem(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.getInventory().clear();
        Creators.createItem(player, Map.of("item", "diamond_sword", "name", "&6Excalibur", "enchantments", "sharpness 255, looting 10",
                "damage", "50", "Unbreakable", "true"));
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(Items.DIAMOND_SWORD)) {
                found = player.getInventory().getStack(i);
            }
        }
        context.assertTrue(!found.isEmpty(), "the sword should be in the inventory");
        context.assertTrue(found.getName().getString().contains("Excalibur"), "it should have the name");
        var enchantments = found.get(DataComponentTypes.ENCHANTMENTS);
        context.assertTrue(enchantments != null && enchantments.getEnchantments().stream().anyMatch(e -> enchantments.getLevel(e) == 255),
                "it should have sharpness 255");
        context.assertTrue(found.contains(DataComponentTypes.UNBREAKABLE), "it should be unbreakable");
        context.complete();
    }
}
