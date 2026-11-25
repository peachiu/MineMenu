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
        super(Text.translatable("mineboost.text.mineboost_tools"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 150;
        int left = this.width / 2 - w - 2;
        int right = this.width / 2 + 2;
        int y = 30;
        addDrawableChild(ButtonWidget.builder(Text.translatable("mineboost.text.lag_doctor_30_s").formatted(Formatting.AQUA), b -> {
            this.client.setScreen(null);
            LagDoctor.start(30);
        }).dimensions(left, y, w, 20).tooltip(Tooltip.of(Text.translatable("mineboost.text.play_normally_for_30_seconds_then"))).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("mineboost.text.lag_doctor_60_s").formatted(Formatting.AQUA), b -> {
            this.client.setScreen(null);
            LagDoctor.start(60);
        }).dimensions(right, y, w, 20).build());
        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.translatable("mineboost.text.last_report_spikes"), b -> this.client.setScreen(new LagDoctorScreen(this)))
                .dimensions(left, y, w, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("mineboost.text.a_b_test_every_boost"), b -> {
            this.client.setScreen(null);
            BoostAB.start(this.client);
        }).dimensions(right, y, w, 20).tooltip(Tooltip.of(Text.translatable("mineboost.text.about_6_minutes_each_boost_off"))).build());
        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.translatable("mineboost.text.memory_jvm_advisor"), b -> this.client.setScreen(new MemoryScreen(this)))
                .dimensions(left, y, w, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("mineboost.text.context_rules_2"), b -> this.client.setScreen(new ContextScreen(this)))
                .dimensions(right, y, w, 20).tooltip(Tooltip.of(Text.translatable("mineboost.text.another_preset_in_the_nether_underground"))).build());
        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.translatable("mineboost.text.fps_profiles_2"), b -> this.client.setScreen(new FpsProfilesScreen(this)))
                .dimensions(left, y, w, 20).build());
        BoostConfig c = BoostConfig.get();
        addDrawableChild(ButtonWidget.builder(Text.translatable("mineboost.text.battery_saver_s", Text.translatable(c.batterySaver ? "mineboost.text.on" : "mineboost.text.off")), b -> {
            c.batterySaver = !c.batterySaver;
            BoostConfig.save();
            clearAndInit();
        }).dimensions(right, y, w, 20).tooltip(Tooltip.of(Text.translatable("mineboost.text.for_playing_on_battery_30_fps"))).build());
        y += 32;
        addDrawableChild(ButtonWidget.builder(Text.translatable("mineboost.text.done_2"), b -> close()).dimensions(this.width / 2 - 75, y, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("mineboost.text.mineboost_tools").formatted(Formatting.GREEN, Formatting.BOLD), this.width / 2, 12, 0xFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
