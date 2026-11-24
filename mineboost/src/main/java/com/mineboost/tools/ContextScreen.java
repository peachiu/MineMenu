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
        super(Text.literal("Context Rules"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        BoostConfig c = BoostConfig.get();
        int left = this.width / 2 - 150;
        int y = 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("Context rules: " + (c.contextRules ? "ON" : "OFF")), b -> {
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
            String label = switch (situation) {
                case NETHER -> "In the Nether";
                case END -> "In the End";
                case CAVE -> "Underground (no sky)";
                case CROWD -> "150+ mobs within 32 blocks";
                case ELYTRA -> "Flying an elytra";
                default -> situation.name();
            };
            addDrawableChild(ButtonWidget.builder(Text.literal(label + ": " + (preset == null ? "leave as is" : preset.displayName())), b -> {
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
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(this.width / 2 - 75, y + 8, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Context Rules").formatted(Formatting.GREEN, Formatting.BOLD), this.width / 2, 8, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Leaving a situation puts your own settings back. Now: "
                + ContextRules.current().name().toLowerCase()).formatted(Formatting.GRAY), this.width / 2, 18, 0xFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
