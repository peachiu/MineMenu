package com.minemenu.ui;

import com.minemenu.Feature;
import com.minemenu.core.CheatConfig;
import com.minemenu.core.FeatureSettings;
import com.minemenu.core.Keys;
import com.minemenu.core.Setting;
import net.minecraft.client.font.MultilineText;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Right-click on any menu button: its description, favorite star, key, and (for some hacks) settings. */
public class FeatureScreen extends Screen {
    private static final int WIDTH = 260;
    private final Screen parent;
    private final MenuEntry entry;
    private final Feature feature;
    private boolean waitingForKey;
    private MultilineText description;
    private int descriptionBottom;

    /** Text settings some hacks have: key in CheatConfig.texts, label, default. */
    private record TextSetting(String key, String label, String defaultValue) {
    }

    public FeatureScreen(Screen parent, MenuEntry entry) {
        super(Text.literal(entry.name()));
        this.parent = parent;
        this.entry = entry;
        this.feature = entry.id().startsWith("feature:") ? CheatConfig.feature(entry.id().substring(8)) : null;
    }

    private List<TextSetting> textSettings() {
        if (feature == null) {
            return List.of();
        }
        return switch (feature) {
            case MAGNET_FILTER -> List.of(new TextSetting("magnet.items", "Items (ids, commas)", "diamond, emerald, iron_ingot, gold_ingot, netherite_scrap"));
            case LUCKY_BLOCKS -> List.of(new TextSetting("lucky.block", "Lucky block", "sponge"));
            case MORPH -> List.of(new TextSetting("morph.mob", "Mob (spider, bat, blaze, enderman, creeper, phantom...)", "spider"));
            default -> List.of();
        };
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        description = MultilineText.create(this.textRenderer, Text.literal(entry.description()).formatted(Formatting.GRAY), WIDTH);
        int y = 24 + description.count() * 9 + 6;
        descriptionBottom = y;

        boolean favorite = CheatConfig.get().isFavorite(entry.id());
        addDrawableChild(ButtonWidget.builder(Text.literal(favorite ? "★ Favorite" : "☆ Add to favorites").formatted(Formatting.YELLOW), b -> {
            CheatConfig.get().toggleFavorite(entry.id());
            clearAndInit();
        }).dimensions(left, y, WIDTH / 2 - 2, 20).build());
        Integer key = CheatConfig.get().keys.get(entry.id());
        addDrawableChild(ButtonWidget.builder(Text.literal(waitingForKey ? "> Press a key <" : "Key: " + Keys.name(key == null ? -1 : key)), b -> {
                    waitingForKey = true;
                    clearAndInit();
                })
                .dimensions(left + WIDTH / 2 + 2, y, WIDTH / 2 - 2, 20)
                .tooltip(Tooltip.of(Text.literal("Click, then press the key you want (Esc or Backspace = no key). "
                        + "Works when no menu is open.")))
                .build());
        y += 24;

        if (feature != null) {
            for (Setting setting : FeatureSettings.of(feature)) {
                if (setting.isToggle()) {
                    boolean on = FeatureSettings.is(feature, setting.key());
                    addDrawableChild(ButtonWidget.builder(Text.literal(setting.label() + ": " + (on ? "ON" : "OFF")), b -> {
                        FeatureSettings.set(feature, setting, on ? 0 : 1);
                        clearAndInit();
                    }).dimensions(left, y, WIDTH, 20).build());
                } else {
                    addDrawableChild(slider(setting, left, y));
                }
                y += 22;
            }
            for (TextSetting text : textSettings()) {
                TextFieldWidget field = new TextFieldWidget(this.textRenderer, left, y + 10, WIDTH, 18, Text.literal(text.label()));
                field.setMaxLength(256);
                field.setText(CheatConfig.get().text(text.key(), text.defaultValue()));
                field.setChangedListener(value -> CheatConfig.get().setText(text.key(), value.trim()));
                addDrawableChild(field);
                y += 32;
            }
            if (feature == Feature.MAGNET_FILTER) {
                boolean except = com.minemenu.MagnetFilter.exceptMode();
                addDrawableChild(ButtonWidget.builder(Text.literal(except ? "Mode: pull everything EXCEPT these" : "Mode: pull ONLY these"), b -> {
                    CheatConfig.get().setText("magnet.mode", except ? "only" : "except");
                    clearAndInit();
                }).dimensions(left, y, WIDTH, 20).build());
                y += 24;
            }
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions((this.width - 150) / 2, Math.min(y + 6, this.height - 24), 150, 20).build());
    }

    private SliderWidget slider(Setting setting, int x, int y) {
        double value = FeatureSettings.get(feature, setting.key());
        double start = (value - setting.min()) / (setting.max() - setting.min());
        return new SliderWidget(x, y, WIDTH, 20, Text.empty(), start) {
            {
                updateMessage();
            }

            private double current() {
                double raw = setting.min() + this.value * (setting.max() - setting.min());
                return Math.round(raw / setting.step()) * setting.step();
            }

            @Override
            protected void updateMessage() {
                setMessage(Text.literal(setting.label() + ": " + setting.format(current())));
            }

            @Override
            protected void applyValue() {
                FeatureSettings.set(feature, setting, current());
            }
        };
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (waitingForKey) {
            waitingForKey = false;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                CheatConfig.get().keys.remove(entry.id());
            } else {
                CheatConfig.get().keys.put(entry.id(), keyCode);
            }
            CheatConfig.markDirty();
            clearAndInit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title.copy().formatted(Formatting.BOLD), this.width / 2, 10, Theme.accent());
        description.drawCenterWithShadow(context, this.width / 2, 24);
        Integer key = CheatConfig.get().keys.get(entry.id());
        if (key != null) {
            List<String> conflicts = Keys.conflicts(this.client, key, entry.id());
            if (!conflicts.isEmpty()) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("⚠ This key is also: " + String.join(", ", conflicts))
                        .formatted(Formatting.RED), this.width / 2, descriptionBottom + 22, 0xFFFFFF);
            }
        }
        if (feature != null) {
            int y = descriptionBottom + 24 + FeatureSettings.of(feature).size() * 22;
            for (TextSetting text : textSettings()) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(text.label()).formatted(Formatting.GRAY), (this.width - WIDTH) / 2, y, 0xFFFFFF);
                y += 32;
            }
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
