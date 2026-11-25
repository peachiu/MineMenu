package com.minemenu.hud;

import com.minemenu.Category;
import com.minemenu.core.CheatConfig;
import com.minemenu.core.Keys;
import com.minemenu.ui.MenuEntries;
import com.minemenu.ui.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Macros: a name, a key and a list of steps. Type steps by hand (one per comma: "action:DAY, feature:FLY,
 * cmd:time set noon, wait:20") or press Record, use the menu or keys, then Stop.
 */
public class MacroScreen extends Screen {
    private final Screen parent;
    private int waitingKeyFor = -1;
    private final TextFieldWidget[] stepFields = new TextFieldWidget[4];

    public MacroScreen(Screen parent) {
        super(Text.translatable("minemenu.text.macros"));
        this.parent = parent;
    }

    public static void register() {
        MenuEntries.screen("macros", Category.UTILITY, "Macros...",
                "Record a few actions (day + clear weather + heal...) and put them on one key.", MacroScreen::new);
    }

    @Override
    protected void init() {
        List<CheatConfig.Macro> macros = CheatConfig.get().macros;
        int left = this.width / 2 - 170;
        int y = 30;
        for (int i = 0; i < Math.min(4, macros.size()); i++) {
            CheatConfig.Macro macro = macros.get(i);
            int index = i;
            TextFieldWidget steps = new TextFieldWidget(this.textRenderer, left, y, 200, 20, Text.translatable("minemenu.text.steps"));
            steps.setMaxLength(1000);
            steps.setText(String.join(", ", macro.steps));
            steps.setChangedListener(text -> {
                macro.steps.clear();
                for (String step : text.split(",")) {
                    if (!step.isBlank()) {
                        macro.steps.add(step.trim());
                    }
                }
                CheatConfig.markDirty();
            });
            stepFields[i] = addDrawableChild(steps);
            addDrawableChild(ButtonWidget.builder((waitingKeyFor == i ? Text.translatable("minemenu.text.key") : Text.translatable("minemenu.text.key_s", Keys.name(macro.key))), b -> {
                waitingKeyFor = index;
                clearAndInit();
            }).dimensions(left + 204, y, 70, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.run"), b -> Macros.run(macro)).dimensions(left + 278, y, 30, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("X").formatted(Formatting.RED), b -> {
                macros.remove(index);
                CheatConfig.markDirty();
                clearAndInit();
            }).dimensions(left + 312, y, 28, 20).build());
            y += 24;
        }
        if (macros.size() < 4) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.new_macro"), b -> {
                CheatConfig.Macro macro = new CheatConfig.Macro();
                macro.name = com.minemenu.core.Tr.get("minemenu.macro.default_name", "Macro") + " " + (macros.size() + 1);
                macro.steps.add("action:DAY");
                macro.steps.add("action:CLEAR_WEATHER");
                macro.steps.add("action:HEAL");
                macros.add(macro);
                CheatConfig.markDirty();
                clearAndInit();
            }).dimensions(left, y, 168, 20).build());
        }
        addDrawableChild(ButtonWidget.builder((Macros.isRecording() ? Text.translatable("minemenu.text.stop_recording") : Text.translatable("minemenu.text.record_new_macro")).formatted(Formatting.RED), b -> {
            if (Macros.isRecording()) {
                List<String> steps = Macros.stopRecording();
                if (!steps.isEmpty() && macros.size() < 4) {
                    CheatConfig.Macro macro = new CheatConfig.Macro();
                    macro.name = com.minemenu.core.Tr.get("minemenu.macro.default_name", "Macro") + " " + (macros.size() + 1);
                    macro.steps.addAll(steps);
                    macros.add(macro);
                    CheatConfig.markDirty();
                }
                clearAndInit();
            } else {
                Macros.startRecording();
                close();
            }
        }).dimensions(left + 172, y, 168, 20).tooltip(Tooltip.of(Text.translatable("minemenu.text.then_use_hacks_and_actions_from"))).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.done"), b -> close()).dimensions(this.width / 2 - 75, this.height - 26, 150, 20).build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (waitingKeyFor >= 0) {
            List<CheatConfig.Macro> macros = CheatConfig.get().macros;
            if (waitingKeyFor < macros.size()) {
                macros.get(waitingKeyFor).key = keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE ? -1 : keyCode;
                CheatConfig.markDirty();
            }
            waitingKeyFor = -1;
            clearAndInit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("minemenu.text.macros").formatted(Formatting.BOLD), this.width / 2, 8, Theme.accent());
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("minemenu.text.steps_action_day_feature_fly_cmd")
                .formatted(Formatting.GRAY), this.width / 2, 18, 0xFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
