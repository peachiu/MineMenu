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
    /** Item names follow the game language, so a language change means rebuilding the list too. */
    private static String allItemsLanguage;

    private record ItemEntryData(Identifier id, Item item, String name, String searchText) {
    }

    private final Screen parent;
    private TextFieldWidget search;
    private ItemListWidget list;
    private ButtonWidget giveButton;
    private Text status = Text.empty();
    /** What the current search found, e.g. "Category: Armor (40 items)". */
    private Text searchInfo = Text.empty();

    public ItemGiveScreen(Screen parent) {
        super(Text.translatable("minemenu.text.give_item"));
        this.parent = parent;
    }

    private static List<ItemEntryData> items(MinecraftClient client) {
        // Skip experimental items the world doesn't have turned on (e.g. the bundle in 1.21.1).
        FeatureSet features = client.world != null ? client.world.getEnabledFeatures() : FeatureSet.empty();
        String language = client.options.language;
        if (allItems == null || !features.equals(allItemsFeatures) || !language.equals(allItemsLanguage)) {
            ItemCategories.refreshGroups(client);
            List<ItemEntryData> result = new ArrayList<>();
            for (Item item : Registries.ITEM) {
                if (item == Items.AIR || !item.isEnabled(features)) {
                    continue;
                }
                Identifier id = Registries.ITEM.getId(item);
                String name = new ItemStack(item).getName().getString();
                // Search both the name in your language and the English id, without accents.
                result.add(new ItemEntryData(id, item, name,
                        ItemCategories.normalize(name + " " + id.getPath().replace('_', ' ') + " " + id.getPath())));
            }
            result.sort(Comparator.comparing(ItemEntryData::name));
            allItems = result;
            allItemsFeatures = features;
            allItemsLanguage = language;
        }
        return allItems;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = 20;

        String previousSearch = search != null ? search.getText() : "";
        search = new TextFieldWidget(this.textRenderer, left, top, PANEL_WIDTH, 18, Text.translatable("minemenu.text.search"));
        search.setPlaceholder(Text.translatable("minemenu.text.search_an_item_or_a_category").formatted(Formatting.DARK_GRAY));
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
                .tooltip(Tooltip.of(Text.translatable("minemenu.text.how_many_1_16_32_or")))
                .build());
        addDrawableChild(ButtonWidget.builder(destinationLabel(), b -> {
                    CheatState.toggleGiveToGround();
                    b.setMessage(destinationLabel());
                })
                .dimensions(left + (buttonWidth + 4), buttonY, buttonWidth, 20)
                .tooltip(Tooltip.of(Text.translatable("minemenu.text.inventory_straight_into_your_inventory_if")))
                .build());
        giveButton = addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.give"), b -> giveSelected())
                .dimensions(left + 2 * (buttonWidth + 4), buttonY, buttonWidth, 20)
                .tooltip(Tooltip.of(Text.translatable("minemenu.text.give_the_selected_item_enter_or")))
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.back"), b -> close())
                .dimensions(left + 3 * (buttonWidth + 4), buttonY, buttonWidth, 20)
                .build());

        list.filter(previousSearch);
        search.setChangedListener(text -> list.filter(text));
        setInitialFocus(search);
    }

    private Text amountLabel() {
        return Text.translatable("minemenu.text.amount_s", CheatState.giveAmount());
    }

    private Text destinationLabel() {
        return CheatState.giveToGround()
                ? Text.translatable("minemenu.text.to").append(Text.translatable("minemenu.text.ground").formatted(Formatting.GOLD))
                : Text.translatable("minemenu.text.to").append(Text.translatable("minemenu.text.inventory").formatted(Formatting.AQUA));
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
        giveButton.setMessage(entry == null ? Text.translatable("minemenu.text.give")
                : Text.translatable("minemenu.text.give_s", effectiveAmount()).formatted(Formatting.GREEN));
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
                ItemCheats.giveItem(player, id, amount, ground);
            }
        });
        status = Text.translatable(ground ? "minemenu.give.dropped" : "minemenu.give.gave", effectiveAmount(), entry.data.name())
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
                Text.translatable("minemenu.text.give_item").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                this.width / 2, 7, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, status.getString().isEmpty() ? searchInfo : status,
                this.width / 2, this.height - 38, 0xFFFFFF);
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

        /**
         * Words are matched separately, so "sword diamond" works as well as "diamond sword". A category
         * word ("comida", "armadura", "tools"...) shows that whole category; any other words then
         * narrow it down ("armadura diamante" = diamond armor).
         */
        void filter(String query) {
            String q = ItemCategories.normalize(query.trim());
            List<String> words = new ArrayList<>(List.of(q.isEmpty() ? new String[0] : q.split("\\s+")));
            ItemCategories.ItemCategory category = null;
            for (String word : words) {
                category = ItemCategories.match(word);
                if (category != null) {
                    words.remove(word);
                    break;
                }
            }
            List<ItemEntryData> all = items(this.client);
            List<ItemEntryData> matches = new ArrayList<>();
            if (category != null) {
                for (ItemEntryData data : all) {
                    if (category.test().test(data.item()) && containsAll(data, words)) {
                        matches.add(data);
                    }
                }
                // A lone word that's also part of item names ("redstone", "la"): add those items after the category.
                if (words.isEmpty()) {
                    for (ItemEntryData data : all) {
                        if (!matches.contains(data) && data.searchText().contains(q)) {
                            matches.add(data);
                        }
                    }
                }
            } else {
                List<String> allWords = q.isEmpty() ? List.of() : List.of(q.split("\\s+"));
                for (ItemEntryData data : all) {
                    if (containsAll(data, allWords)) {
                        matches.add(data);
                    }
                }
                if (!q.isEmpty()) {
                    // Best match first: exact name, then names starting with the search, then the rest.
                    matches.sort(Comparator.comparingInt((ItemEntryData d) -> rank(d, q)));
                }
            }
            status = Text.empty();
            searchInfo = category != null
                    ? Text.translatable("minemenu.text.category").formatted(Formatting.GRAY)
                            .append(Text.literal(category.name()).formatted(Formatting.AQUA))
                            .append(Text.translatable("minemenu.text.s_items", matches.size()).formatted(Formatting.GRAY))
                    : Text.translatable("minemenu.text.s_items_2", matches.size()).formatted(Formatting.GRAY);

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

        private boolean containsAll(ItemEntryData data, List<String> words) {
            for (String word : words) {
                if (!data.searchText().contains(word)) {
                    return false;
                }
            }
            return true;
        }

        private int rank(ItemEntryData data, String q) {
            String name = ItemCategories.normalize(data.name());
            String id = data.id().getPath().replace('_', ' ');
            if (name.equals(q) || id.equals(q)) {
                return 0;
            }
            if (name.startsWith(q) || id.startsWith(q)) {
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
            return Text.translatable("minemenu.text.give_s", data.name());
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
