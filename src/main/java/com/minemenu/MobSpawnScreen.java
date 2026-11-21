package com.minemenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Pick a mob from a searchable list, then spawn it where you're looking. */
public class MobSpawnScreen extends Screen {
    private static final int ROW_HEIGHT = 20;
    private static final int PANEL_WIDTH = 220;

    private final Screen parent;
    private TextFieldWidget search;
    private MobListWidget list;
    private ButtonWidget spawnButton;
    private ButtonWidget amountButton;

    public MobSpawnScreen(Screen parent) {
        super(Text.literal("Spawn Mob"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = 20;

        String previousSearch = search != null ? search.getText() : "";
        search = new TextFieldWidget(this.textRenderer, left, top, PANEL_WIDTH, 18, Text.literal("Search"));
        search.setPlaceholder(Text.literal("Search mobs...").formatted(Formatting.DARK_GRAY));
        search.setText(previousSearch);
        addDrawableChild(search);

        int listTop = top + 22;
        int listBottom = this.height - 32;
        list = new MobListWidget(this.client, this.width, listBottom - listTop, listTop);
        list.filter(previousSearch);
        addDrawableChild(list);
        search.setChangedListener(text -> list.filter(text));

        int buttonY = this.height - 26;
        int buttonWidth = (PANEL_WIDTH - 8) / 3;
        amountButton = addDrawableChild(ButtonWidget.builder(amountLabel(), b -> {
                    CheatState.cycleSpawnAmount();
                    b.setMessage(amountLabel());
                    spawnButton.setMessage(spawnLabel());
                })
                .dimensions(left, buttonY, buttonWidth, 20)
                .tooltip(Tooltip.of(Text.literal("How many to spawn at once: 1, 5 or 10.")))
                .build());
        spawnButton = addDrawableChild(ButtonWidget.builder(spawnLabel(), b -> spawnSelected())
                .dimensions(left + buttonWidth + 4, buttonY, buttonWidth, 20)
                .tooltip(Tooltip.of(Text.literal("Spawn on the block you're looking at. Double-clicking a mob works too.")))
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
                .dimensions(left + 2 * (buttonWidth + 4), buttonY, buttonWidth, 20)
                .build());
        updateSpawnButton();
        setInitialFocus(search);
    }

    private Text amountLabel() {
        return Text.literal("Amount: " + CheatState.spawnAmount());
    }

    private Text spawnLabel() {
        return Text.literal("Spawn").formatted(Formatting.GREEN);
    }

    private void updateSpawnButton() {
        if (spawnButton != null) {
            spawnButton.active = list.getSelectedOrNull() != null;
        }
    }

    private void spawnSelected() {
        MobEntry entry = list.getSelectedOrNull();
        if (entry == null || this.client == null) {
            return;
        }
        CheatState.setSelectedMob(entry.id);
        spawnSelectedMob(this.client);
        // Close everything so you can see the mob appear.
        this.client.setScreen(null);
    }

    /** Spawns the currently selected mob at the crosshair. Used by the button and the keybind. */
    public static void spawnSelectedMob(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null) {
            return;
        }
        Identifier id = CheatState.selectedMob();
        int amount = CheatState.spawnAmount();
        server.execute(() -> ServerCheats.spawnMob(server, id, amount));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter spawns the selected mob, so you can search and hit Enter.
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && list.getSelectedOrNull() != null) {
            spawnSelected();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        if (this.client != null && !MineMenuClient.isSingleplayer(this.client)) {
            this.client.setScreen(null);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Spawn Mob").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                this.width / 2, 7, 0xFFFFFF);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------

    private class MobListWidget extends AlwaysSelectedEntryListWidget<MobEntry> {
        MobListWidget(MinecraftClient client, int width, int height, int y) {
            super(client, width, height, y, ROW_HEIGHT);
        }

        void filter(String query) {
            String q = query.trim().toLowerCase(Locale.ROOT);
            Identifier selectedId = CheatState.selectedMob();
            clearEntries();
            MobEntry toSelect = null;
            for (EntityType<?> type : MobList.all()) {
                MobEntry entry = new MobEntry(type);
                if (q.isEmpty() || entry.searchText.contains(q)) {
                    addEntry(entry);
                    if (entry.id.equals(selectedId)) {
                        toSelect = entry;
                    }
                }
            }
            if (toSelect == null && !children().isEmpty() && !q.isEmpty()) {
                toSelect = children().get(0);
            }
            setSelected(toSelect);
            setScrollAmount(0);
            if (toSelect != null) {
                centerScrollOn(toSelect);
            }
            updateSpawnButton();
        }

        @Override
        public int getRowWidth() {
            return PANEL_WIDTH;
        }

        @Override
        public void setSelected(MobEntry entry) {
            super.setSelected(entry);
            if (entry != null) {
                CheatState.setSelectedMob(entry.id);
            }
            updateSpawnButton();
        }
    }

    private class MobEntry extends AlwaysSelectedEntryListWidget.Entry<MobEntry> {
        final Identifier id;
        final Text name;
        final String searchText;
        final ItemStack icon;
        private long lastClick;

        MobEntry(EntityType<?> type) {
            this.id = Registries.ENTITY_TYPE.getId(type);
            this.name = type.getName();
            this.searchText = (name.getString() + " " + id.getPath()).toLowerCase(Locale.ROOT);
            SpawnEggItem egg = SpawnEggItem.forEntity(type);
            // Mobs without an egg (Giant, Illusioner) get a stand-in icon.
            this.icon = new ItemStack(egg != null ? egg : Items.SPAWNER);
        }

        @Override
        public Text getNarration() {
            return Text.literal("Spawn " + name.getString());
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawItem(icon, x + 2, y + (entryHeight - 16) / 2);
            context.drawTextWithShadow(MobSpawnScreen.this.textRenderer, name, x + 24,
                    y + (entryHeight - 8) / 2, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            list.setSelected(this);
            long now = Util.getMeasuringTimeMs();
            if (now - lastClick < 250L) {
                spawnSelected();
            }
            lastClick = now;
            return true;
        }
    }
}
