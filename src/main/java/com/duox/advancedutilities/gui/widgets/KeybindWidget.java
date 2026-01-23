package com.duox.advancedutilities.gui.widgets;
/*
 * Widget for configuring keybinds.
 * Supports keyboard and mouse inputs.
 */
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class KeybindWidget extends Button {
    private final KeyMapping keyMapping;
    private boolean listening = false;

    public KeybindWidget(int x, int y, int width, int height, KeyMapping keyMapping) {
        // Pass a dummy action, we override onPress
        super(x, y, width, height, Component.empty(), b -> {}, DEFAULT_NARRATION);
        this.keyMapping = keyMapping;
        this.updateMessage();
    }

    @Override
    public void onPress() {
        this.listening = !this.listening;
        this.updateMessage();
    }

    private void updateMessage() {
        if (listening) {
            this.setMessage(Component.literal("> Press Key <").withStyle(net.minecraft.ChatFormatting.YELLOW));
        } else {
            InputConstants.Key key = keyMapping.getKey();
            String keyName = key.getDisplayName().getString();
            this.setMessage(Component.literal("Bind: " + keyName).withStyle(net.minecraft.ChatFormatting.WHITE));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                listening = false;
            } else {
                InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
                keyMapping.setKey(key);
                Minecraft.getInstance().options.save();
                KeyMapping.resetMapping();
                listening = false;
            }
            updateMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listening) {
            InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
            keyMapping.setKey(key);
            Minecraft.getInstance().options.save();
            KeyMapping.resetMapping();
            listening = false;
            updateMessage();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
