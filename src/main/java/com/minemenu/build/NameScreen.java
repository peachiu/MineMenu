package com.minemenu.build;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Asks for a name (schematic, snapshot...). Enter or OK confirms. */
public class NameScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> onDone;
    private TextFieldWidget field;

    public NameScreen(Screen parent, String title, Consumer<String> onDone) {
        super(Text.literal(title));
        this.parent = parent;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        field = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, this.height / 2 - 20, 200, 20, this.title);
        field.setMaxLength(48);
        addDrawableChild(field);
        addDrawableChild(ButtonWidget.builder(Text.literal("OK"), b -> done()).dimensions(this.width / 2 - 100, this.height / 2 + 6, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close()).dimensions(this.width / 2 + 2, this.height / 2 + 6, 98, 20).build());
        setInitialFocus(field);
    }

    private void done() {
        if (!field.getText().isBlank()) {
            onDone.accept(field.getText().trim());
        }
        close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            done();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 36, 0xFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
