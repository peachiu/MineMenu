package com.minemenu.op;

import com.minemenu.ui.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 6.0: Server Admin. Players (teleport, game mode, heal, kick, ban, OP), unban / whitelist by name, game rules,
 * difficulty, time, weather, world spawn and border, saving, and messages to everyone. Works in your own world and
 * on servers where you're OP (with Minecraft's own commands). Fits 683x384 (1366x768 at GUI Scale 2).
 */
public class ServerAdminScreen extends Screen {
    private enum Tab { PLAYERS, RULES, WORLD }

    private record Rule(String name, GameRules.Key<GameRules.BooleanRule> key) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("keepInventory", GameRules.KEEP_INVENTORY), new Rule("doDaylightCycle", GameRules.DO_DAYLIGHT_CYCLE),
            new Rule("doWeatherCycle", GameRules.DO_WEATHER_CYCLE), new Rule("doMobSpawning", GameRules.DO_MOB_SPAWNING),
            new Rule("mobGriefing", GameRules.DO_MOB_GRIEFING), new Rule("doFireTick", GameRules.DO_FIRE_TICK),
            new Rule("doInsomnia", GameRules.DO_INSOMNIA), new Rule("naturalRegeneration", GameRules.NATURAL_REGENERATION),
            new Rule("doImmediateRespawn", GameRules.DO_IMMEDIATE_RESPAWN), new Rule("announceAdvancements", GameRules.ANNOUNCE_ADVANCEMENTS),
            new Rule("showDeathMessages", GameRules.SHOW_DEATH_MESSAGES), new Rule("doMobLoot", GameRules.DO_MOB_LOOT),
            new Rule("doTileDrops", GameRules.DO_TILE_DROPS), new Rule("doPatrolSpawning", GameRules.DO_PATROL_SPAWNING),
            new Rule("doTraderSpawning", GameRules.DO_TRADER_SPAWNING), new Rule("disableRaids", GameRules.DISABLE_RAIDS));
    private static final int ROW = 22;

    private static Tab tab = Tab.PLAYERS;
    private static int scroll;
    private static String typedName = "";
    private static String typedMessage = "";
    private static String typedBorder = "1000";
    private final Screen parent;
    private String status = "";
    private int top;
    private int rows;
    private int listSize;

    public ServerAdminScreen(Screen parent) {
        super(Text.translatable("minemenu.admin.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        top = 28;
        int tabWidth = 100;
        int tx = (this.width - (3 * tabWidth + 8)) / 2;
        for (Tab t : Tab.values()) {
            ButtonWidget b = addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.admin.tab." + t.name().toLowerCase(Locale.ROOT)), btn -> {
                tab = t;
                scroll = 0;
                clearAndInit();
            }).dimensions(tx, top, tabWidth, 20).build());
            b.active = t != tab;
            tx += tabWidth + 4;
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close()).dimensions((this.width - 150) / 2, this.height - 26, 150, 20).build());
        if (!Cmd.available(this.client)) {
            return;
        }
        int y = top + 28;
        switch (tab) {
            case PLAYERS -> players(y);
            case RULES -> rules(y);
            case WORLD -> world(y);
        }
    }

    private void run(String... commands) {
        Cmd.run(this.client, commands);
        status = Text.translatable("minemenu.admin.done", String.join(" · ", commands)).getString();
    }

    private ButtonWidget small(String key, int x, int y, int w, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(Text.translatable(key), b -> action.run()).dimensions(x, y, w, 20).build());
    }

    // ------------------------------------------------------------------
    // Players
    // ------------------------------------------------------------------

    private void players(int y) {
        List<String> names = new ArrayList<>();
        if (this.client.getNetworkHandler() != null) {
            for (PlayerListEntry entry : this.client.getNetworkHandler().getPlayerList()) {
                names.add(entry.getProfile().getName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        listSize = names.size();
        int width = 110 + 6 * 44 + 3 * 26;
        int left = (this.width - width) / 2;
        rows = Math.max(1, (this.height - y - 100) / ROW);
        scroll = Math.max(0, Math.min(scroll, names.size() - rows));
        for (int i = scroll; i < Math.min(names.size(), scroll + rows); i++) {
            String name = names.get(i);
            int ry = y + (i - scroll) * ROW;
            int x = left + 110;
            boolean me = name.equals(this.client.player.getGameProfile().getName());
            ButtonWidget tp = small("minemenu.admin.tp_to", x, ry, 42, () -> run("tp @s " + name));
            tp.active = !me;
            x += 44;
            ButtonWidget here = small("minemenu.admin.tp_here", x, ry, 42, () -> run("tp " + name + " @s"));
            here.active = !me;
            x += 44;
            small("minemenu.admin.survival", x, ry, 24, () -> run("gamemode survival " + name)).setTooltip(Tooltip.of(Text.translatable("minemenu.admin.survival.tooltip")));
            x += 26;
            small("minemenu.admin.creative", x, ry, 24, () -> run("gamemode creative " + name)).setTooltip(Tooltip.of(Text.translatable("minemenu.admin.creative.tooltip")));
            x += 26;
            small("minemenu.admin.spectator", x, ry, 24, () -> run("gamemode spectator " + name)).setTooltip(Tooltip.of(Text.translatable("minemenu.admin.spectator.tooltip")));
            x += 26;
            small("minemenu.admin.heal", x, ry, 42, () -> run("effect give " + name + " minecraft:instant_health 1 10 true",
                    "effect give " + name + " minecraft:saturation 1 10 true"));
            x += 44;
            small("minemenu.admin.op", x, ry, 42, () -> run("op " + name));
            x += 44;
            ButtonWidget kick = small("minemenu.admin.kick", x, ry, 42, () -> run("kick " + name));
            kick.active = !me;
            x += 44;
            ButtonWidget ban = small("minemenu.admin.ban", x, ry, 42, () -> run("ban " + name));
            ban.active = !me;
        }
        // By name (also players who aren't online): unban, whitelist, OP.
        int by = this.height - 92;
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, left, by + 1, 150, 18, Text.translatable("minemenu.admin.name"));
        field.setPlaceholder(Text.translatable("minemenu.admin.name").formatted(Formatting.DARK_GRAY));
        field.setMaxLength(16);
        field.setText(typedName);
        field.setChangedListener(t -> typedName = t);
        addDrawableChild(field);
        int x = left + 154;
        small("minemenu.admin.unban", x, by, 70, () -> named("pardon %s"));
        small("minemenu.admin.unban_ip", x + 74, by, 70, () -> named("pardon-ip %s"));
        small("minemenu.admin.whitelist_add", x + 148, by, 70, () -> named("whitelist add %s"));
        small("minemenu.admin.whitelist_remove", x + 222, by, 70, () -> named("whitelist remove %s"));
        int by2 = by + 24;
        small("minemenu.admin.op_name", left, by2, 70, () -> named("op %s"));
        small("minemenu.admin.deop", left + 74, by2, 70, () -> named("deop %s"));
        small("minemenu.admin.whitelist_on", left + 154, by2, 70, () -> run("whitelist on"));
        small("minemenu.admin.whitelist_off", left + 228, by2, 70, () -> run("whitelist off"));
        small("minemenu.admin.banlist", left + 302, by2, 70, () -> run("banlist"))
                .setTooltip(Tooltip.of(Text.translatable("minemenu.admin.banlist.tooltip")));
    }

    private void named(String format) {
        String name = typedName.contains(".") ? typedName.trim().replaceAll("[^0-9.:a-fA-F]", "") : Cmd.name(typedName); // pardon-ip takes an IP
        if (name.isEmpty()) {
            status = Text.translatable("minemenu.admin.type_name").getString();
            return;
        }
        run(String.format(format, name));
    }

    // ------------------------------------------------------------------
    // Game rules
    // ------------------------------------------------------------------

    private void rules(int y) {
        IntegratedServer local = Cmd.localServer(this.client);
        int columnWidth = 300;
        int left = (this.width - (2 * columnWidth + 10)) / 2;
        for (int i = 0; i < RULES.size(); i++) {
            Rule rule = RULES.get(i);
            int x = left + (i % 2) * (columnWidth + 10);
            int ry = y + (i / 2) * ROW;
            Text label = Text.translatable("minemenu.admin.rule." + rule.name());
            if (local != null) {
                boolean on = local.getGameRules().getBoolean(rule.key());
                label = label.copy().append(": ").append(Text.translatable(on ? "minemenu.text.on" : "minemenu.text.off").formatted(on ? Formatting.GREEN : Formatting.RED));
            }
            addDrawableChild(ButtonWidget.builder(label, b -> {
            }).dimensions(x, ry, columnWidth - 92, 20).tooltip(Tooltip.of(Text.literal("/gamerule " + rule.name()))).build()).active = false;
            small("minemenu.admin.on", x + columnWidth - 88, ry, 42, () -> {
                run("gamerule " + rule.name() + " true");
                clearAndInit();
            });
            small("minemenu.admin.off", x + columnWidth - 44, ry, 42, () -> {
                run("gamerule " + rule.name() + " false");
                clearAndInit();
            });
        }
        int ry = y + ((RULES.size() + 1) / 2) * ROW + 4;
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.admin.sleeping"), b -> {
        }).dimensions(left, ry, 150, 20).build()).active = false;
        int[] percents = {0, 1, 50, 100};
        for (int i = 0; i < percents.length; i++) {
            int p = percents[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(p == 1 ? "1 " + Text.translatable("minemenu.admin.player").getString() : p + "%"),
                    b -> run("gamerule playersSleepingPercentage " + p)).dimensions(left + 154 + i * 64, ry, 60, 20).build());
        }
    }

    // ------------------------------------------------------------------
    // World
    // ------------------------------------------------------------------

    private void world(int y) {
        int left = (this.width - 4 * 104) / 2;
        String[] difficulties = {"peaceful", "easy", "normal", "hard"};
        label(left, y, "minemenu.admin.difficulty");
        for (int i = 0; i < 4; i++) {
            String d = difficulties[i];
            small("minemenu.admin.difficulty." + d, left + i * 104, y + 12, 100, () -> run("difficulty " + d));
        }
        y += 40;
        label(left, y, "minemenu.admin.time");
        String[][] times = {{"day", "1000"}, {"noon", "6000"}, {"night", "13000"}, {"midnight", "18000"}};
        for (int i = 0; i < 4; i++) {
            String t = times[i][1];
            small("minemenu.admin.time." + times[i][0], left + i * 104, y + 12, 100, () -> run("time set " + t));
        }
        y += 40;
        label(left, y, "minemenu.admin.weather");
        String[] weathers = {"clear", "rain", "thunder"};
        for (int i = 0; i < 3; i++) {
            String w = weathers[i];
            small("minemenu.admin.weather." + w, left + i * 104, y + 12, 100, () -> run("weather " + w));
        }
        small("minemenu.admin.save", left + 3 * 104, y + 12, 100, () -> run("save-all"));
        y += 40;
        label(left, y, "minemenu.admin.world");
        small("minemenu.admin.spawn_here", left, y + 12, 152, () -> run("setworldspawn ~ ~ ~"));
        TextFieldWidget border = new TextFieldWidget(this.textRenderer, left + 156, y + 13, 70, 18, Text.translatable("minemenu.admin.border"));
        border.setText(typedBorder);
        border.setChangedListener(t -> typedBorder = t.replaceAll("[^0-9]", ""));
        addDrawableChild(border);
        small("minemenu.admin.border_set", left + 230, y + 12, 90, () -> {
            String size = typedBorder.isEmpty() ? "1000" : typedBorder;
            run("worldborder center ~ ~", "worldborder set " + size);
        });
        small("minemenu.admin.border_off", left + 324, y + 12, 92, () -> run("worldborder set 59999968"));
        y += 40;
        label(left, y, "minemenu.admin.message");
        TextFieldWidget message = new TextFieldWidget(this.textRenderer, left, y + 13, 220, 18, Text.translatable("minemenu.admin.message"));
        message.setMaxLength(200);
        message.setText(typedMessage);
        message.setChangedListener(t -> typedMessage = t);
        addDrawableChild(message);
        small("minemenu.admin.say", left + 224, y + 12, 94, () -> {
            if (!typedMessage.isBlank()) {
                run("say " + typedMessage.replace("\n", " ").trim());
            }
        });
        small("minemenu.admin.title_all", left + 322, y + 12, 94, () -> {
            if (!typedMessage.isBlank()) {
                run("title @a title {\"text\":\"" + Cmd.text(typedMessage) + "\",\"color\":\"gold\"}");
            }
        });
    }

    private final List<int[]> labels = new ArrayList<>();
    private final List<String> labelKeys = new ArrayList<>();

    private void label(int x, int y, String key) {
        labels.add(new int[]{x, y});
        labelKeys.add(key);
    }

    @Override
    protected void clearAndInit() {
        labels.clear();
        labelKeys.clear();
        super.clearAndInit();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (tab == Tab.PLAYERS && listSize > rows) {
            scroll = Math.max(0, Math.min(listSize - rows, scroll - (int) Math.signum(vertical)));
            clearAndInit();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title.copy().formatted(Formatting.BOLD), this.width / 2, 10, Theme.accent());
        if (!Cmd.available(this.client)) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("minemenu.admin.needs_op").formatted(Formatting.RED), this.width / 2, this.height / 2, 0xFFFFFF);
            return;
        }
        for (int i = 0; i < labels.size(); i++) {
            context.drawTextWithShadow(this.textRenderer, Text.translatable(labelKeys.get(i)).formatted(Formatting.GRAY), labels.get(i)[0], labels.get(i)[1], 0xFFFFFF);
        }
        if (tab == Tab.PLAYERS && this.client.getNetworkHandler() != null) {
            List<String> names = new ArrayList<>();
            for (PlayerListEntry entry : this.client.getNetworkHandler().getPlayerList()) {
                names.add(entry.getProfile().getName());
            }
            names.sort(String.CASE_INSENSITIVE_ORDER);
            int width = 110 + 6 * 44 + 3 * 26;
            int left = (this.width - width) / 2;
            for (int i = scroll; i < Math.min(names.size(), scroll + rows); i++) {
                context.drawTextWithShadow(this.textRenderer, names.get(i), left, top + 28 + (i - scroll) * ROW + 6, 0xFFFFFF);
            }
            context.drawTextWithShadow(this.textRenderer, Text.translatable("minemenu.admin.by_name").formatted(Formatting.GRAY), left, this.height - 104, 0xFFFFFF);
        }
        if (!status.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(status, this.width - 20)).formatted(Formatting.AQUA),
                    this.width / 2, this.height - 40, 0xFFFFFF);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        Theme.overlay(context, this.width, this.height);
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
