package com.minemenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

/** Time &amp; Weather: set the exact time, how fast days go by, and sun / rain / thunder. */
public class TimeWeatherScreen extends Screen {
    private static final int WIDTH = 304;
    /** Day speed choices: 0 = time stops. */
    private static final int[] SPEEDS = {0, 1, 2, 5, 10, 20, 60};

    private final Screen parent;

    public TimeWeatherScreen(Screen parent) {
        super(Text.literal("Time & Weather"));
        this.parent = parent;
    }

    /** Minecraft time (0 = 6:00 in the morning) as a clock time, e.g. 6000 -> "12:00". */
    private static String clock(int ticks) {
        int minutes = (int) ((ticks / 1000.0 + 6) * 60) % (24 * 60);
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    private int currentTicks() {
        return this.client != null && this.client.world != null ? (int) Math.floorMod(this.client.world.getTimeOfDay(), 24000L) : 0;
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int y = 40;

        // Exact time: 0-24000 ticks, shown as a clock. Rounded to 10 minutes of game time.
        addDrawableChild(new SliderWidget(left, y, WIDTH, 20, Text.empty(), currentTicks() / 24000.0) {
            {
                updateMessage();
            }

            private int ticks() {
                return (int) Math.round(this.value * 24000 / 1000.0 * 6) * 1000 / 6 % 24000;
            }

            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Time: " + clock(ticks())));
            }

            @Override
            protected void applyValue() {
                int t = ticks();
                onServer(server -> WorldCheats.setTime(server, t));
            }
        }).setTooltip(Tooltip.of(Text.literal("Drag to set the exact time of day. (Always Day overrides this.)")));
        y += 24;
        int quarter = (WIDTH - 12) / 4;
        String[] names = {"Sunrise", "Noon", "Sunset", "Midnight"};
        int[] times = {0, 6000, 12000, 18000};
        for (int i = 0; i < 4; i++) {
            int t = times[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(names[i]), b -> {
                        onServer(server -> WorldCheats.setTime(server, t));
                        this.client.setScreen(new TimeWeatherScreen(parent)); // move the slider too
                    })
                    .dimensions(left + i * (quarter + 4), y, quarter, 20)
                    .build());
        }
        y += 30;

        int speedIndex = 1;
        for (int i = 0; i < SPEEDS.length; i++) {
            if (SPEEDS[i] == WorldCheats.daySpeed()) {
                speedIndex = i;
            }
        }
        addDrawableChild(new SliderWidget(left, y, WIDTH, 20, Text.empty(), speedIndex / (double) (SPEEDS.length - 1)) {
            {
                updateMessage();
            }

            private int speed() {
                return SPEEDS[(int) Math.round(this.value * (SPEEDS.length - 1))];
            }

            @Override
            protected void updateMessage() {
                int s = speed();
                setMessage(Text.literal("Day Speed: " + (s == 0 ? "Stopped" : s == 1 ? "Normal" : s + "x faster")));
            }

            @Override
            protected void applyValue() {
                int s = speed();
                onServer(server -> WorldCheats.setDaySpeed(server, s));
            }
        }).setTooltip(Tooltip.of(Text.literal("How fast the sun moves. Normal is a 20 minute day. "
                + "Goes back to Normal when you leave the world.")));
        y += 30;

        int third = (WIDTH - 8) / 3;
        addDrawableChild(ButtonWidget.builder(Text.literal("Sun").formatted(Formatting.YELLOW),
                        b -> onServer(server -> WorldCheats.setWeather(server, WorldCheats.Weather.CLEAR)))
                .dimensions(left, y, third, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Rain").formatted(Formatting.AQUA),
                        b -> onServer(server -> WorldCheats.setWeather(server, WorldCheats.Weather.RAIN)))
                .dimensions(left + third + 4, y, third, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Thunder").formatted(Formatting.GOLD),
                        b -> onServer(server -> WorldCheats.setWeather(server, WorldCheats.Weather.THUNDER)))
                .dimensions(left + 2 * (third + 4), y, third, 20).build());
        y += 34;
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions((this.width - 150) / 2, y, 150, 20).build());
    }

    private void onServer(Consumer<MinecraftServer> action) {
        IntegratedServer server = this.client != null ? this.client.getServer() : null;
        if (server != null) {
            server.execute(() -> action.accept(server));
        }
    }

    @Override
    public void tick() {
        if (this.client != null && !MineMenuClient.isSingleplayer(this.client)) {
            this.client.setScreen(null);
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Time & Weather").formatted(Formatting.GREEN, Formatting.BOLD),
                this.width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Now: " + clock(currentTicks())).formatted(Formatting.GRAY),
                this.width / 2, 25, 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
