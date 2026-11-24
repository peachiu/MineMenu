package com.mineboost.tools;

import com.mineboost.BoostConfig;
import com.mineboost.doctor.LagDoctor;
import com.mineboost.doctor.LagDoctorScreen;
import com.mineboost.doctor.MemoryScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** MineBoost tools: Lag Doctor, A/B test, memory, context rules, FPS profiles, Battery Saver. */
public class BoostToolsScreen extends Screen {
    private final Screen parent;

    public BoostToolsScreen(Screen parent) {
        super(Text.literal("MineBoost Tools"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 150;
        int left = this.width / 2 - w - 2;
        int right = this.width / 2 + 2;
        int y = 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("Lag Doctor (30 s)").formatted(Formatting.AQUA), b -> {
            this.client.setScreen(null);
            LagDoctor.start(30);
        }).dimensions(left, y, w, 20).tooltip(Tooltip.of(Text.literal("Play normally for 30 seconds; then it shows what costs you the most FPS, with a Fix button for each."))).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Lag Doctor (60 s)").formatted(Formatting.AQUA), b -> {
            this.client.setScreen(null);
            LagDoctor.start(60);
        }).dimensions(right, y, w, 20).build());
        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Last report & spikes"), b -> this.client.setScreen(new LagDoctorScreen(this)))
                .dimensions(left, y, w, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("A/B test every boost"), b -> {
            this.client.setScreen(null);
            BoostAB.start(this.client);
        }).dimensions(right, y, w, 20).tooltip(Tooltip.of(Text.literal("About 6 minutes: each boost off and on, twice. Stand still looking at "
                + "a busy spot. The table goes to config/minemeboost/benchmark-results.md. Send it to compare PCs!"))).build());
        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Memory & JVM Advisor"), b -> this.client.setScreen(new MemoryScreen(this)))
                .dimensions(left, y, w, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Context Rules..."), b -> this.client.setScreen(new ContextScreen(this)))
                .dimensions(right, y, w, 20).tooltip(Tooltip.of(Text.literal("Another preset in the Nether, underground, near crowds or on an elytra."))).build());
        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("FPS Profiles..."), b -> this.client.setScreen(new FpsProfilesScreen(this)))
                .dimensions(left, y, w, 20).build());
        BoostConfig c = BoostConfig.get();
        addDrawableChild(ButtonWidget.builder(Text.literal("Battery Saver: " + (c.batterySaver ? "ON" : "OFF")), b -> {
            c.batterySaver = !c.batterySaver;
            BoostConfig.save();
            clearAndInit();
        }).dimensions(right, y, w, 20).tooltip(Tooltip.of(Text.literal("For playing on battery: 30 FPS, Render Scale at most 70%, at most 500 particles. "
                + "Also on a key (Controls > MineBoost)."))).build());
        y += 32;
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(this.width / 2 - 75, y, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("MineBoost Tools").formatted(Formatting.GREEN, Formatting.BOLD), this.width / 2, 12, 0xFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
