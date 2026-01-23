package com.duox.advancedutilities.system;

/*
 * Centralized constants for the Advanced Utilities mod.
 * This class contains all magic numbers and configuration values used throughout the mod.
 */
public final class Constants {
    private Constants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // Time constants (in ticks, where 20 ticks = 1 second)
    public static final int TICKS_PER_SECOND = 20;
    public static final int TICKS_PER_MINUTE = 1200;

    // Finder module constants
    public static final int FINDER_SCAN_INTERVAL_TICKS = 100; // 5 seconds
    public static final int FINDER_DEFAULT_RANGE = 64;
    public static final int FINDER_MIN_RANGE = 16;
    public static final int FINDER_MAX_RANGE = 256;
    public static final int FINDER_DEFAULT_LIMIT = 1000;
    public static final int FINDER_MIN_LIMIT = 10;
    public static final int FINDER_MAX_LIMIT = 5000;

    // AutoFish module constants
    public static final int AUTOFISH_WATCHDOG_TIMEOUT_TICKS = 400; // 20 seconds
    public static final int AUTOFISH_MAX_WAIT_TICKS = 600; // 30 seconds
    public static final int AUTOFISH_MIN_BOBBER_AGE = 60;
    public static final double AUTOFISH_BITE_MOTION_THRESHOLD = -0.05;
    public static final int AUTOFISH_XP_DELAY_TICKS = 20;

    // AutoRightClick module constants
    public static final int AUTORIGHTCLICK_MAP_CLEAR_INTERVAL_TICKS = 60; // 3 seconds
    public static final int AUTORIGHTCLICK_SCAN_INTERVAL_TICKS = 20; // 1 second

    // FullBright module constants
    public static final int FULLBRIGHT_NIGHT_VISION_DURATION = 400;

    // GUI constants
    public static final int GUI_TOP_BAR_HEIGHT = 30;
    public static final int GUI_SIDEBAR_WIDTH = 120;
    public static final int GUI_MODULE_BTN_HEIGHT = 22;
    public static final int GUI_MODULE_BTN_WIDTH = 100;
    public static final int GUI_PADDING = 5;
    public static final int GUI_SETTINGS_START_X_OFFSET = 20;
    public static final int GUI_SETTINGS_START_Y_OFFSET = 40;
    public static final int GUI_SETTINGS_WIDGET_WIDTH = 200;
    public static final int GUI_LIST_WIDGET_HEIGHT = 55;

    // Rendering constants
    public static final float RENDER_LINE_WIDTH = 2.0f;
    public static final float RENDER_DEFAULT_LINE_WIDTH = 1.0f;
    public static final float RENDER_BLOCK_ALPHA = 0.4f;
    public static final float RENDER_ENTITY_ALPHA = 1.0f;
}

