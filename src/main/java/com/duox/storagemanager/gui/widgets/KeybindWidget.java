package com.duox.storagemanager.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor; // Required for renderContents
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent; // New Input Event
import net.minecraft.client.input.MouseButtonEvent; // New Input Event
import org.lwjgl.glfw.GLFW;

public class KeybindWidget extends Button {
    private final KeyMapping keyMapping;
    private boolean listening = false;

    public KeybindWidget(int x, int y, int width, int height, KeyMapping keyMapping) {
        super(x, y, width, height, Component.empty(), b -> {}, DEFAULT_NARRATION);
        this.keyMapping = keyMapping;
        this.updateMessage();
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.listening = !this.listening;
        this.updateMessage();
    }

    // --- FIX 1: Implement renderContents ---
    // Since 'renderWidget' is final, we must provide the rendering logic here.
    // We use the standard helper methods provided by AbstractButton to draw the background and label.
    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractDefaultSprite(graphics); // Draws the button texture
        this.extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE)); // Draws the text
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

    // --- FIX 2: Update keyPressed Signature & Logic ---
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening) {
            int keyCode = event.key(); // Access key from record

            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                listening = false;
            } else {
                // FIX 3: Use Type.KEYSYM.getOrCreate instead of InputConstants.getKey
                InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(keyCode);

                keyMapping.setKey(key);
                Minecraft.getInstance().options.save();
                KeyMapping.resetMapping();
                listening = false;
            }
            updateMessage();
            return true;
        }
        // Pass event object to super
        return super.keyPressed(event);
    }

    // --- FIX 2: Update mouseClicked Signature & Logic ---
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        if (listening) {
            int button = event.button(); // Access button from record

            InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
            keyMapping.setKey(key);
            Minecraft.getInstance().options.save();
            KeyMapping.resetMapping();
            listening = false;
            updateMessage();
            return true;
        }
        // Pass event object to super
        return super.mouseClicked(event, isFocused);
    }
}