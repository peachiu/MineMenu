package com.mineboost.doctor;

import com.mineboost.Tr;

import com.mineboost.BoostConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** The Lag Doctor report: what costs the most per frame, with a Fix button for each, plus recent spikes. */
public class LagDoctorScreen extends Screen {
    private static final int WIDTH = 400;
    private final Screen parent;
    private final java.util.Set<Integer> fixed = new java.util.HashSet<>();

    public LagDoctorScreen(Screen parent) {
        super(Text.translatable("mineboost.text.lag_doctor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        LagDoctor.Report report = LagDoctor.lastReport();
        int left = (this.width - WIDTH) / 2;
        int y = 44;
        if (report != null) {
            List<LagDoctor.Finding> findings = report.findings();
            for (int i = 0; i < Math.min(6, findings.size()); i++) {
                LagDoctor.Finding finding = findings.get(i);
                int index = i;
                if (finding.fixLabel() != null && finding.fix() != null) {
                    ButtonWidget fix = addDrawableChild(ButtonWidget.builder((fixed.contains(i) ? Text.translatable("mineboost.text.done") : Text.translatable("mineboost.text.fix_s", Tr.auto("mineboost.doctor.fix.", finding.fixLabel()))), b -> {
                        finding.fix().run();
                        BoostConfig.save();
                        this.client.options.write();
                        fixed.add(index);
                        clearAndInit();
                    }).dimensions(left + WIDTH - 120, y - 4, 120, 18).build());
                    fix.active = !fixed.contains(i);
                } else if ("JVM Advisor".equals(finding.fixLabel())) {
                    addDrawableChild(ButtonWidget.builder(Text.translatable("mineboost.text.open_jvm_advisor"), b -> this.client.setScreen(new MemoryScreen(this)))
                            .dimensions(left + WIDTH - 120, y - 4, 120, 18).build());
                }
                y += 22;
            }
        }
        int bottom = this.height - 26;
        addDrawableChild(ButtonWidget.builder(Text.translatable("mineboost.text.run_again_30s"), b -> {
            this.client.setScreen(null);
            LagDoctor.start(30);
        }).dimensions(this.width / 2 - 154, bottom, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("mineboost.text.run_again_60s"), b -> {
            this.client.setScreen(null);
            LagDoctor.start(60);
        }).dimensions(this.width / 2 - 50, bottom, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("mineboost.text.done_2"), b -> close()).dimensions(this.width / 2 + 54, bottom, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int left = (this.width - WIDTH) / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("mineboost.text.lag_doctor").formatted(Formatting.AQUA, Formatting.BOLD), this.width / 2, 8, 0xFFFFFF);
        LagDoctor.Report report = LagDoctor.lastReport();
        if (report == null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("mineboost.text.no_report_yet_press_run_then")
                    .formatted(Formatting.GRAY), this.width / 2, 50, 0xFFFFFF);
            return;
        }
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("mineboost.doctor.summary",
                report.frames(), String.format("%.0f", report.avgFps()), String.format("%.1f", report.avgMs())).formatted(Formatting.GRAY), this.width / 2, 22, 0xFFFFFF);
        int y = 44;
        List<LagDoctor.Finding> findings = report.findings();
        for (int i = 0; i < Math.min(6, findings.size()); i++) {
            LagDoctor.Finding finding = findings.get(i);
            int percent = report.avgMs() > 0 ? (int) Math.round(finding.msPerFrame() * 100 / report.avgMs()) : 0;
            String text = (i + 1) + ") " + finding.text();
            int max = WIDTH - 128 - 60;
            while (this.textRenderer.getWidth(text) > max && text.length() > 10) {
                text = text.substring(0, text.length() - 2);
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal(text), left, y, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal(String.format("%.1f ms %d%%", finding.msPerFrame(), percent))
                    .formatted(percent >= 20 ? Formatting.RED : percent >= 8 ? Formatting.YELLOW : Formatting.GRAY), left + max + 4, y, 0xFFFFFF);
            y += 22;
        }
        List<String> spikes = SpikeDetector.recent();
        y += 4;
        context.drawTextWithShadow(this.textRenderer, Text.translatable("mineboost.text.recent_spikes_frames_over_50_ms")
                .formatted(Formatting.YELLOW), left, y, 0xFFFFFF);
        y += 11;
        for (int i = 0; i < Math.min(spikes.size(), (this.height - 40 - y) / 10); i++) {
            String line = spikes.get(i);
            while (this.textRenderer.getWidth(line) > WIDTH && line.length() > 10) {
                line = line.substring(0, line.length() - 2);
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal(line).formatted(Formatting.GRAY), left, y, 0xFFFFFF);
            y += 10;
        }
        if (spikes.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.translatable("mineboost.text.none_so_far").formatted(Formatting.GRAY), left, y, 0xFFFFFF);
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
