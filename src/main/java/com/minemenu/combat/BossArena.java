package com.minemenu.combat;

import com.minemenu.build.BlockChange;
import com.minemenu.build.BlockPlacer;
import com.minemenu.ui.MenuEntries;
import com.minemenu.ui.Theme;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Boss Arena: summon the Wither, the Ender Dragon, the Warden or an Elder Guardian 16 blocks in front of you,
 * with 0.5x to 5x health, optionally inside a closed arena (glass walls, a water tank for the guardian).
 * The arena can be undone with Undo. The Ender Dragon outside the End has no portal to circle, so it mostly
 * hovers and dives at you: still a fight, just not the vanilla one.
 */
public class BossArena extends Screen {
    private static final String[] BOSSES = {"Wither", "Ender Dragon", "Warden", "Elder Guardian"};
    private static final double[] HEALTH = {0.5, 1, 2, 5};
    private static int boss;
    private static int health = 1;
    private static boolean arena = true;
    private final Screen parent;

    public BossArena(Screen parent) {
        super(Text.literal("Boss Arena"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 100;
        int y = 36;
        addDrawableChild(ButtonWidget.builder(Text.literal("Boss: " + BOSSES[boss]), b -> {
            boss = (boss + 1) % BOSSES.length;
            clearAndInit();
        }).dimensions(left, y, 200, 20).build());
        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Health: " + HEALTH[health] + "x"), b -> {
            health = (health + 1) % HEALTH.length;
            clearAndInit();
        }).dimensions(left, y, 200, 20).build());
        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Arena: " + (arena ? "ON" : "OFF")), b -> {
            arena = !arena;
            clearAndInit();
        }).dimensions(left, y, 200, 20).build());
        y += 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("Fight!").formatted(Formatting.RED), b -> {
            int which = boss;
            double multiplier = HEALTH[health];
            boolean withArena = arena;
            MenuEntries.onServer(p -> summon(p, which, multiplier, withArena)).accept(MinecraftClient.getInstance(), null);
            this.client.setScreen(null);
        }).dimensions(left, y, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close()).dimensions(left + 102, y, 98, 20).build());
    }

    public static void summon(ServerPlayerEntity player, int which, double multiplier, boolean withArena) {
        ServerWorld world = player.getServerWorld();
        BlockPos center = player.getBlockPos().offset(player.getHorizontalFacing(), 16);
        if (withArena) {
            BlockState wall = Blocks.GLASS.getDefaultState();
            BlockState inside = which == 3 ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
            List<BlockChange> changes = new ArrayList<>();
            int r = 14;
            int h = which == 1 ? 30 : 14;
            for (BlockPos pos : BlockPos.iterate(center.add(-r, -1, -r), center.add(r, h, r))) {
                int dx = Math.abs(pos.getX() - center.getX());
                int dz = Math.abs(pos.getZ() - center.getZ());
                int dy = pos.getY() - center.getY();
                boolean shell = dx == r || dz == r || dy == -1 || dy == h;
                changes.add(new BlockChange(pos.toImmutable(), shell ? (dy == -1 ? Blocks.STONE_BRICKS.getDefaultState() : wall) : inside, null));
            }
            BlockPlacer.submit(world, changes, "Boss arena", player, true);
        }
        EntityType<?> type = switch (which) {
            case 0 -> EntityType.WITHER;
            case 1 -> EntityType.ENDER_DRAGON;
            case 2 -> EntityType.WARDEN;
            default -> EntityType.ELDER_GUARDIAN;
        };
        var entity = type.spawn(world, center.up(which == 1 ? 10 : 1), SpawnReason.COMMAND);
        if (entity instanceof LivingEntity living) {
            var max = living.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(max.getBaseValue() * multiplier);
                living.setHealth(living.getMaxHealth());
            }
            player.sendMessage(Text.literal(BOSSES[which] + " summoned with " + Math.round(living.getMaxHealth()) + " health. Good luck!").formatted(Formatting.RED), true);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Boss Arena").formatted(Formatting.BOLD), this.width / 2, 12, Theme.accent());
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Summoned 16 blocks in front of you. God Mode recommended.").formatted(Formatting.GRAY), this.width / 2, 23, 0xFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
