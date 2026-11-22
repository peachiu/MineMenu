package com.minemenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Give yourself any item: search the list, pick an amount and where it goes (your inventory or the
 * ground where you're looking), then Give. Stays open so you can grab several things in a row.
 */
public class ItemGiveScreen extends Screen {
    private static final int ROW_HEIGHT = 20;
    private static final int PANEL_WIDTH = 300;

    /** Every item, sorted by name; rebuilt only when the world's enabled features change. */
    private static List<ItemEntryData> allItems;
    private static FeatureSet allItemsFeatures;

    private record ItemEntryData(Identifier id, Item item, String name, String searchText) {
    }

    private final Screen parent;
    private TextFieldWidget search;
    private ItemListWidget list;
    private ButtonWidget giveButton;
    private Text status = Text.empty();

    public ItemGiveScreen(Screen parent) {
        super(Text.literal("Give Item"));
        this.parent = parent;
    }

    private static List<ItemEntryData> items(MinecraftClient client) {
        // Skip experimental items the world doesn't have turned on (e.g. the bundle in 1.21.1).
        FeatureSet features = client.world != null ? client.world.getEnabledFeatures() : FeatureSet.empty();
        if (allItems == null || !features.equals(allItemsFeatures)) {
            List<ItemEntryData> result = new ArrayList<>();
            for (Item item : Registries.ITEM) {
                if (item == Items.AIR || !item.isEnabled(features)) {
                    continue;
                }
                Identifier id = Registries.ITEM.getId(item);
                String name = new ItemStack(item).getName().getString();
                result.add(new ItemEntryData(id, item, name, (name + " " + id.getPath()).toLowerCase(Locale.ROOT)));
            }
            result.sort(Comparator.comparing(ItemEntryData::name));
            allItems = result;
            allItemsFeatures = features;
        }
        return allItems;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = 20;

        String previousSearch = search != null ? search.getText() : "";
        search = new TextFieldWidget(this.textRenderer, left, top, PANEL_WIDTH, 18, Text.literal("Search"));
        search.setPlaceholder(Text.literal("Search items... (e.g. diamond, elytra, totem)").formatted(Formatting.DARK_GRAY));
        search.setText(previousSearch);
        addDrawableChild(search);

        int listTop = top + 22;
        int listBottom = this.height - 44;
        list = new ItemListWidget(this.client, this.width, listBottom - listTop, listTop);
        addDrawableChild(list);

        int buttonY = this.height - 26;
        int buttonWidth = (PANEL_WIDTH - 12) / 4;
        addDrawableChild(ButtonWidget.builder(amountLabel(), b -> {
                    CheatState.cycleGiveAmount();
                    b.setMessage(amountLabel());
                    updateGiveButton();
                })
                .dimensions(left, buttonY, buttonWidth, 20)
                .tooltip(Tooltip.of(Text.literal("How many: 1, 16, 32 or 64. Items that don't stack (tools, armor) "
                        + "always come one at a time.")))
                .build());
        addDrawableChild(ButtonWidget.builder(destinationLabel(), b -> {
                    CheatState.toggleGiveToGround();
                    b.setMessage(destinationLabel());
                })
                .dimensions(left + (buttonWidth + 4), buttonY, buttonWidth, 20)
                .tooltip(Tooltip.of(Text.literal("Inventory: straight into your inventory (if it's full, the rest "
                        + "drops at your feet). Ground: dropped on the block you're looking at.")))
                .build());
        giveButton = addDrawableChild(ButtonWidget.builder(Text.literal("Give"), b -> giveSelected())
                .dimensions(left + 2 * (buttonWidth + 4), buttonY, buttonWidth, 20)
                .tooltip(Tooltip.of(Text.literal("Give the selected item. Enter or double-clicking an item works too.")))
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
                .dimensions(left + 3 * (buttonWidth + 4), buttonY, buttonWidth, 20)
                .build());

        list.filter(previousSearch);
        search.setChangedListener(text -> list.filter(text));
        setInitialFocus(search);
    }

    private Text amountLabel() {
        return Text.literal("Amount: " + CheatState.giveAmount());
    }

    private Text destinationLabel() {
        return CheatState.giveToGround()
                ? Text.literal("To: ").append(Text.literal("Ground").formatted(Formatting.GOLD))
                : Text.literal("To: ").append(Text.literal("Inventory").formatted(Formatting.AQUA));
    }

    /** How many you'd actually get of the selected item (non-stackables come one at a time). */
    private int effectiveAmount() {
        ItemEntry entry = list.getSelectedOrNull();
        return entry == null ? 0 : Math.min(CheatState.giveAmount(), entry.data.item().getMaxCount());
    }

    private void updateGiveButton() {
        if (giveButton == null || list == null) {
            return;
        }
        ItemEntry entry = list.getSelectedOrNull();
        giveButton.active = entry != null;
        giveButton.setMessage(entry == null ? Text.literal("Give")
                : Text.literal("Give " + effectiveAmount()).formatted(Formatting.GREEN));
    }

    private void giveSelected() {
        ItemEntry entry = list.getSelectedOrNull();
        IntegratedServer server = this.client != null ? this.client.getServer() : null;
        if (entry == null || server == null) {
            return;
        }
        Identifier id = entry.data.id();
        int amount = CheatState.giveAmount();
        boolean ground = CheatState.giveToGround();
        server.execute(() -> {
            var player = ServerCheats.host(server);
            if (player != null) {
                ExtraCheats.giveItem(player, id, amount, ground);
            }
        });
        status = Text.literal((ground ? "Dropped " : "Gave ") + effectiveAmount() + "x " + entry.data.name())
                .formatted(Formatting.GREEN);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && list.getSelectedOrNull() != null) {
            giveSelected();
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
                Text.literal("Give Item").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                this.width / 2, 7, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, status, this.width / 2, this.height - 38, 0xFFFFFF);
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

    private class ItemListWidget extends AlwaysSelectedEntryListWidget<ItemEntry> {
        ItemListWidget(MinecraftClient client, int width, int height, int y) {
            super(client, width, height, y, ROW_HEIGHT);
        }

        void filter(String query) {
            String q = query.trim().toLowerCase(Locale.ROOT);
            List<ItemEntryData> matches = new ArrayList<>();
            for (ItemEntryData data : items(this.client)) {
                if (q.isEmpty() || data.searchText().contains(q)) {
                    matches.add(data);
                }
            }
            if (!q.isEmpty()) {
                // Best match first: exact name, then names starting with the search, then the rest.
                matches.sort(Comparator.comparingInt((ItemEntryData d) -> rank(d, q)));
            }
            clearEntries();
            ItemEntry toSelect = null;
            Identifier selectedId = CheatState.selectedItem();
            for (ItemEntryData data : matches) {
                ItemEntry entry = new ItemEntry(data);
                addEntry(entry);
                if (q.isEmpty() && data.id().equals(selectedId)) {
                    toSelect = entry;
                }
            }
            if (!q.isEmpty() && !children().isEmpty()) {
                toSelect = children().get(0);
            }
            setSelected(toSelect);
            setScrollAmount(0);
            if (toSelect != null) {
                centerScrollOn(toSelect);
            }
        }

        private int rank(ItemEntryData data, String q) {
            String name = data.name().toLowerCase(Locale.ROOT);
            if (name.equals(q) || data.id().getPath().equals(q)) {
                return 0;
            }
            if (name.startsWith(q) || data.id().getPath().startsWith(q)) {
                return 1;
            }
            return 2;
        }

        @Override
        public int getRowWidth() {
            return PANEL_WIDTH;
        }

        @Override
        public void setSelected(ItemEntry entry) {
            super.setSelected(entry);
            if (entry != null) {
                CheatState.setSelectedItem(entry.data.id());
            }
            updateGiveButton();
        }
    }

    private class ItemEntry extends AlwaysSelectedEntryListWidget.Entry<ItemEntry> {
        final ItemEntryData data;
        final ItemStack icon;
        private long lastClick;

        ItemEntry(ItemEntryData data) {
            this.data = data;
            this.icon = new ItemStack(data.item());
        }

        @Override
        public Text getNarration() {
            return Text.literal("Give " + data.name());
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawItem(icon, x + 2, y + (entryHeight - 16) / 2);
            context.drawTextWithShadow(ItemGiveScreen.this.textRenderer, data.name(), x + 24,
                    y + (entryHeight - 8) / 2, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            list.setSelected(this);
            long now = Util.getMeasuringTimeMs();
            if (now - lastClick < 250L) {
                giveSelected();
            }
            lastClick = now;
            return true;
        }
    }
}
