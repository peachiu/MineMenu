package com.minemenu.ui;

import com.minemenu.core.CheatConfig;
import com.minemenu.core.Keys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** MineMenu settings: theme, accent, animations, cheat profiles (save, load, share), key conflicts, reset. */
public class MenuSettingsScreen extends Screen {
    private static final int WIDTH = 300;
    private final Screen parent;
    private TextFieldWidget profileName;
    private Text message = Text.empty();

    public MenuSettingsScreen(Screen parent) {
        super(Text.translatable("minemenu.text.minemenu_settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int half = WIDTH / 2 - 2;
        int third = (WIDTH - 8) / 3;
        int y = 22;
        CheatConfig config = CheatConfig.get();
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.theme_s", Theme.name()), b -> {
            int i = java.util.Arrays.asList(Theme.NAMES).indexOf(config.theme);
            config.theme = Theme.NAMES[(i + 1) % Theme.NAMES.length];
            CheatConfig.markDirty();
            clearAndInit();
        }).dimensions(left, y, third, 20).tooltip(Tooltip.of(Text.translatable("minemenu.text.dark_as_before_light_or_glass"))).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.accent").styled(s -> s.withColor(Theme.accent())), b -> {
            int i = 0;
            for (int k = 0; k < Theme.ACCENTS.length; k++) {
                if (Theme.ACCENTS[k] == Theme.accent()) {
                    i = k;
                }
            }
            config.accent = Theme.ACCENTS[(i + 1) % Theme.ACCENTS.length];
            CheatConfig.markDirty();
            clearAndInit();
        }).dimensions(left + third + 4, y, third, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.animations_s", Text.translatable(config.animations ? "minemenu.text.on" : "minemenu.text.off")), b -> {
            config.animations = !config.animations;
            CheatConfig.markDirty();
            clearAndInit();
        }).dimensions(left + 2 * (third + 4), y, third, 20)
                .tooltip(Tooltip.of(Text.translatable("minemenu.text.small_slide_in_when_menus_open"))).build());
        y += 30;

        // Profiles
        profileName = new TextFieldWidget(this.textRenderer, left, y, half, 20, Text.translatable("minemenu.text.profile_name"));
        profileName.setPlaceholder(Text.translatable("minemenu.text.profile_name").formatted(Formatting.DARK_GRAY));
        profileName.setMaxLength(24);
        addDrawableChild(profileName);
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.save_current_hacks"), b -> {
            String name = profileName.getText().isBlank() ? com.minemenu.core.Tr.get("minemenu.profiles.default_name", "Profile") + " " + (config.profiles.size() + 1) : profileName.getText().trim();
            Profiles.saveCurrent(name);
            message = Text.translatable("minemenu.profiles.saved", name);
            clearAndInit();
        }).dimensions(left + half + 4, y, half, 20).build());
        y += 24;
        List<String> names = new ArrayList<>(config.profiles.keySet());
        for (int i = 0; i < Math.min(4, names.size()); i++) {
            String name = names.get(i);
            addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.load_s", name), b -> {
                Profiles.load(name);
                message = Text.translatable("minemenu.profiles.loaded", name);
            }).dimensions(left, y, WIDTH - 108, 20).tooltip(Tooltip.of(Text.literal(String.join(", ", config.profiles.get(name))))).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.share"), b -> {
                this.client.keyboard.setClipboard(Profiles.export(name));
                message = Text.translatable("minemenu.profiles.copied", name);
            }).dimensions(left + WIDTH - 104, y, 52, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.delete").formatted(Formatting.RED), b -> {
                Profiles.delete(name);
                clearAndInit();
            }).dimensions(left + WIDTH - 48, y, 48, 20).build());
            y += 22;
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.import_profile_from_clipboard"), b -> {
            String name = Profiles.importCode(this.client.keyboard.getClipboard());
            message = name == null ? Text.translatable("minemenu.profiles.no_code") : Text.translatable("minemenu.profiles.imported", name);
            clearAndInit();
        }).dimensions(left, y, WIDTH, 20).build());
        y += 28;

        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.what_s_new"), b -> this.client.setScreen(new WhatsNewScreen(this)))
                .dimensions(left, y, third, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.reset_everything").formatted(Formatting.RED), b -> this.client.setScreen(new ConfirmScreen(yes -> {
                    if (yes) {
                        try {
                            message = Text.translatable("minemenu.settings.reset_done", String.valueOf(CheatConfig.resetEverything().getFileName()));
                            if (resetBoost != null) {
                                resetBoost.run();
                            }
                        } catch (Exception e) {
                            message = Text.translatable("minemenu.settings.reset_failed", String.valueOf(e.getMessage()));
                        }
                    }
                    this.client.setScreen(this);
                }, Text.translatable("minemenu.text.reset_everything_2"), Text.translatable("minemenu.text.all_minemenu_and_mineboost_settings_keys"))))
                .dimensions(left + third + 4, y, third, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.done"), b -> close()).dimensions(left + 2 * (third + 4), y, third, 20).build());
    }

    /** Set by MineMeBoost: also reset MineBoost's settings. */
    public static Runnable resetBoost;

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title.copy().formatted(Formatting.BOLD), this.width / 2, 8, Theme.accent());
        context.drawCenteredTextWithShadow(this.textRenderer, message.copy().formatted(Formatting.GREEN), this.width / 2, this.height - 22, 0xFFFFFF);
        // Key conflicts, listed so you can fix them.
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> binding : CheatConfig.get().keys.entrySet()) {
            List<String> conflicts = Keys.conflicts(this.client, binding.getValue(), binding.getKey());
            if (!conflicts.isEmpty()) {
                MenuEntry entry = MenuEntries.byId(binding.getKey());
                lines.add((entry != null ? entry.name() : binding.getKey()) + " (" + Keys.name(binding.getValue()) + ") = " + String.join(", ", conflicts));
            }
        }
        int y = this.height - 34 - lines.size() * 10;
        if (!lines.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("minemenu.text.key_conflicts").formatted(Formatting.RED), this.width / 2, y - 10, 0xFFFFFF);
        }
        for (String line : lines) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(line).formatted(Formatting.YELLOW), this.width / 2, y, 0xFFFFFF);
            y += 10;
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
