package com.mineboost.tools;

import com.mineboost.BoostConfig;
import com.mineboost.Preset;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Pick a preset for each situation (or leave it as is). */
public class ContextScreen extends Screen {
    private final Screen parent;

    public ContextScreen(Screen parent) {
        super(Text.translatable("mineboost.text.context_rules"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        BoostConfig c = BoostConfig.get();
        int left = this.width / 2 - 150;
        int y = 30;
        addDrawableChild(ButtonWidget.builder(Text.translatable("mineboost.text.context_rules_s", Text.translatable(c.contextRules ? "mineboost.text.on" : "mineboost.text.off")), b -> {
            c.contextRules = !c.contextRules;
            BoostConfig.save();
            clearAndInit();
        }).dimensions(left, y, 300, 20).build());
        y += 26;
        for (ContextRules.Situation situation : ContextRules.Situation.values()) {
            if (situation == ContextRules.Situation.NORMAL) {
                continue;
            }
            String current = c.contextPresets.get(situation.name());
            Preset preset = current == null ? null : ContextRules.find(current);
            String label = com.mineboost.Tr.get("mineboost.context." + situation.name().toLowerCase(java.util.Locale.ROOT), situation.name());
            addDrawableChild(ButtonWidget.builder(Text.literal(label + ": " + (preset == null ? com.mineboost.Tr.get("mineboost.context.leave", "leave as is") : preset.displayName())), b -> {
                Preset[] all = Preset.values();
                int index = preset == null ? 0 : preset.ordinal() + 1;
                if (index >= all.length) {
                    c.contextPresets.remove(situation.name());
                } else {
                    c.contextPresets.put(situation.name(), all[index].name());
                }
                BoostConfig.save();
                clearAndInit();
            }).dimensions(left, y, 300, 20).build());
            y += 22;
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("mineboost.text.done_2"), b -> close()).dimensions(this.width / 2 - 75, y + 8, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("mineboost.text.context_rules").formatted(Formatting.GREEN, Formatting.BOLD), this.width / 2, 8, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("mineboost.text.leaving_a_situation_puts_your_own", ContextRules.current().name().toLowerCase()).formatted(Formatting.GRAY), this.width / 2, 18, 0xFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
