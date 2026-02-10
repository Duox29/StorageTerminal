package com.duox.storagemanager.modules;

import com.duox.storagemanager.logic.ChestScoringEngine;
import com.duox.storagemanager.logic.StashPlanner;
import com.duox.storagemanager.system.Category;
import com.duox.storagemanager.system.Module;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.system.settings.BooleanSetting;
import com.duox.storagemanager.system.settings.NumberSetting;
import com.duox.storagemanager.utils.ChestCache;
import com.duox.storagemanager.utils.ManualContainerTracker;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class AutoStash extends Module {

    // --- Constants ---
    private static final int HOTBAR_SIZE = 9;
    private static final int PLAYER_INVENTORY_SIZE = 36;
    private static final int MAIN_INVENTORY_SIZE = 27;
    private static final int DEFAULT_WAIT_TICKS = 20;
    private static final int SERVER_SYNC_WAIT_TICKS = 10;
    // Public API compatibility flags
    public static boolean cacheDirty = false; // Referenced by StorageScreen
    // Settings
    private final NumberSetting range = new NumberSetting("Range", 5.0, 1.0, 10.0, 0.5);
    private final BooleanSetting includeHotbar = new BooleanSetting("Include Hotbar", false);
    private final NumberSetting itemsPerTick = new NumberSetting("Items/Tick", 4.0, 1.0, 27.0, 1.0);
    private final BooleanSetting rebuildCache = new BooleanSetting("Rebuild Cache Next Run", false);
    private final NumberSetting distanceWeight = new NumberSetting("Distance Weight", 0.2, 0.0, 1.0, 0.1);
    private final NumberSetting itemConcentrationWeight = new NumberSetting("Item Concentration Weight", 0.4, 0.0, 1.0, 0.1);
    private final NumberSetting spaceWeight = new NumberSetting("Space Weight", 0.4, 0.0, 1.0, 0.1);
    private final List<BlockPos> scanQueue = new ArrayList<>();
    private final Set<BlockPos> processedChests = new HashSet<>();
    private State currentState = State.IDLE;
    // --- Runtime Data ---
    private BlockPos currentTarget = null;
    private int waitTimer = 0;
    private int silentContainerId = -1;
    private boolean containerReady = false;
    private Map<BlockPos, List<Integer>> stashQueue = new LinkedHashMap<>();
    private Iterator<Map.Entry<BlockPos, List<Integer>>> stashIterator;
    private Map.Entry<BlockPos, List<Integer>> currentStashEntry;
    private StashPlanner stashPlanner;

    public AutoStash() {
        super("AutoStash", "Scans chests to build a cache, then intelligently stashes items.", Category.UTILITY);
        this.getKeyMapping().setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_B));

        List.of(range, includeHotbar, itemsPerTick, rebuildCache, distanceWeight, itemConcentrationWeight, spaceWeight)
                .forEach(this::addSetting);
    }

    public static Map<String, Map<String, Integer>> getChestCache() {
        return ChestCache.getActiveCache();
    }

    // --- Public API (Used by StorageManager and StorageScreen) ---

    public static Map<String, Map<String, Integer>> getGlobalBuffer() {
        return ChestCache.getGlobalBuffer();
    }

    public static void updateGlobalAndSync(BlockPos pos, Map<String, Integer> newData) {
        ChestCache.updateAndSync(pos, newData, Minecraft.getInstance());
        cacheDirty = ChestCache.DIRTY_FLAG.get();
    }

    public static void clearCachesAndStorage(Minecraft mc) {
        ChestCache.clearAll(mc);
        cacheDirty = ChestCache.DIRTY_FLAG.get();
    }

    public static BlockPos getLastInteractedBlock() {
        return ManualContainerTracker.getLastInteractedBlock();
    }

    // Mixin hooks
    public static void setLastInteractedBlock(BlockPos pos) {
        ManualContainerTracker.setLastInteractedBlock(pos);
    }

    public static void tickManualCacheUpdate() {
        ManualContainerTracker.tickManualCacheUpdate();
        cacheDirty = ChestCache.DIRTY_FLAG.get();
    }

    public double getRange() {
        return range.getValue();
    }

    @Override
    public void onEnable() {
        if (!validateEnvironment()) {
            this.setEnabled(false);
            return;
        }
        resetState();
        initializePlanner();

        if (rebuildCache.getValue()) {
            startRebuildCache();
        } else {
            startSmartStash();
        }
    }

    // --- Module Lifecycle ---

    @Override
    public void onDisable() {
        if (silentContainerId != -1 && mc.player != null) {
            closeContainerSafe();
        }
        resetState();
    }

    private void resetState() {
        currentState = State.IDLE;
        currentTarget = null;
        silentContainerId = -1;
        containerReady = false;
        waitTimer = 0;
        scanQueue.clear();
        stashQueue.clear();
        stashIterator = null;
        currentStashEntry = null;
        processedChests.clear();
    }

    private void initializePlanner() {
        ChestScoringEngine engine = new ChestScoringEngine(
                mc.level,
                distanceWeight.getValue(),
                itemConcentrationWeight.getValue(),
                spaceWeight.getValue()
        );
        stashPlanner = new StashPlanner(engine, this::logDebug);
    }

    @Override
    public void onTick() {
        if (!validateEnvironment()) return;

        // Background maintenance
        processManualInteractions();
        syncDirtyFlag();

        // State Machine Execution
        this.currentState = handleState(this.currentState);
    }

    private boolean validateEnvironment() {
        if (mc.player == null || mc.level == null) {
            this.setEnabled(false);
            return false;
        }
        return true;
    }

    private void processManualInteractions() {
        ManualContainerTracker.tick(mc, (pos) -> {
            ChestCache.updateFromContainerMenu(pos, mc.player.containerMenu, mc);
            cacheDirty = ChestCache.DIRTY_FLAG.get();
        });
    }

    private void syncDirtyFlag() {
        if (ChestCache.DIRTY_FLAG.get()) {
            cacheDirty = true;
        }
    }

    /**
     * Core State Machine Transition Logic.
     * Each handler performs an action and returns the NEXT state.
     */
    private State handleState(State state) {
        return switch (state) {
            case IDLE -> State.IDLE;

            // --- Scanning Branch ---
            case SCANNING_WORLD -> handleScanningWorld();
            case OPENING_FOR_SCAN -> handleOpeningContainer(State.WAITING_FOR_SCAN_OPEN);
            case WAITING_FOR_SCAN_OPEN -> handleWaitingForContainer(State.SCANNING_CONTENTS, State.SCANNING_WORLD);
            case SCANNING_CONTENTS -> handleScanningContents();
            case CLOSING_AFTER_SCAN -> handleClosingContainer(State.SCANNING_WORLD);

            // --- Stashing Branch ---
            case OPENING_FOR_STASH -> handleOpeningContainer(State.WAITING_FOR_STASH_OPEN);
            case WAITING_FOR_STASH_OPEN -> handleWaitingForContainer(State.STASHING_ITEMS, State.CLOSING_AFTER_STASH);
            case STASHING_ITEMS -> handleStashingItems();
            case WAITING_FOR_UPDATE -> handleWaitingForUpdate();
            case CLOSING_AFTER_STASH -> handleClosingContainer(State.OPENING_FOR_STASH);
        };
    }

    private void startRebuildCache() {
        ChestCache.DIRTY_FLAG.set(true);
        scanQueue.clear();

        BlockPos playerPos = mc.player.blockPosition();
        int r = range.getInt();


        BlockPos.betweenClosedStream(playerPos.offset(-r, -r, -r), playerPos.offset(r, r, r))
                .filter(pos -> isValidContainer(mc.level.getBlockEntity(pos)))
                .filter(pos -> !isDuplicateDoubleChest(mc.level.getBlockEntity(pos)))
                .map(BlockPos::immutable)
                .forEach(scanQueue::add);

        scanQueue.sort(Comparator.comparingDouble(pos -> pos.distSqr(playerPos)));
        currentState = State.SCANNING_WORLD;
    }

    private State handleScanningWorld() {
        if (scanQueue.isEmpty()) {
            rebuildCache.setValue(false);
            forceRefreshActiveCache();
            sendMessage("Cache rebuild complete. Saved to disk.");
            this.setEnabled(false);
            return State.IDLE;
        }
        currentTarget = scanQueue.remove(0);
        return State.OPENING_FOR_SCAN;
    }

    private State handleScanningContents() {
        if (mc.player.containerMenu != null) {
            ChestCache.updateFromContainerMenu(currentTarget, mc.player.containerMenu, mc);
        }
        return State.CLOSING_AFTER_SCAN;
    }

    private void startSmartStash() {
        if (ChestCache.isCacheEmpty()) {
            ChestCache.loadFromDatabase(mc);
            if (ChestCache.isCacheEmpty()) {
                sendMessage("§cCache is empty. Please run Rebuild Cache first.");
                this.setEnabled(false);
                return;
            }
        }

        forceRefreshActiveCache();
        calculateStashPlan();

        if (stashQueue.isEmpty()) {
            sendMessage("Nothing to stash.");
            this.setEnabled(false);
            return;
        }

        stashIterator = stashQueue.entrySet().iterator();
        moveToNextStashTarget();
    }
    // --- State Handlers: Stashing ---

    private void calculateStashPlan() {
        stashQueue = stashPlanner.calculatePlan(
                mc.player,
                includeHotbar.getValue(),
                range.getValue(),
                ChestCache.getActiveCache(),
                processedChests
        );
    }

    private void moveToNextStashTarget() {
        if (stashIterator != null && stashIterator.hasNext()) {
            currentStashEntry = stashIterator.next();
            currentTarget = currentStashEntry.getKey();
            currentState = State.OPENING_FOR_STASH;
        } else {
            this.setEnabled(false);
            currentState = State.IDLE;
        }
    }

    private State handleStashingItems() {
        AbstractContainerMenu menu = mc.player.containerMenu;
        int containerSlots = menu.slots.size() - PLAYER_INVENTORY_SIZE;

        if (containerSlots <= 0) return State.CLOSING_AFTER_STASH;

        List<Integer> slotsToMove = currentStashEntry.getValue();
        int limit = itemsPerTick.getInt();
        int moves = 0;

        Iterator<Integer> it = slotsToMove.iterator();
        while (it.hasNext() && moves < limit) {
            int invSlotIndex = it.next();
            int menuSlotId = mapInventorySlotToMenuSlot(invSlotIndex, containerSlots);

            if (menu.getSlot(menuSlotId).hasItem()) {
                sendQuickMovePacket(menu, menuSlotId);
                moves++;
            }
            it.remove();
        }

        if (slotsToMove.isEmpty()) {
            waitTimer = SERVER_SYNC_WAIT_TICKS;
            return State.WAITING_FOR_UPDATE;
        }

        return State.STASHING_ITEMS;
    }

    /**
     * Maps player inventory slot index to ContainerMenu slot ID.
     * Standard MC ContainerMenu Layout:
     * 0..N-1: Container Slots
     * N..N+26: Player Inventory (Main)
     * N+27..N+35: Player Hotbar
     */
    private int mapInventorySlotToMenuSlot(int invSlotIndex, int containerSlots) {
        if (invSlotIndex < HOTBAR_SIZE) {
            // Hotbar (0-8) -> End of player slots
            return containerSlots + MAIN_INVENTORY_SIZE + invSlotIndex;
        } else {
            // Main Inventory (9-35) -> Start of player slots
            return containerSlots + (invSlotIndex - HOTBAR_SIZE);
        }
    }

    private State handleWaitingForUpdate() {
        if (--waitTimer <= 0) {
            // Update cache after modification
            ChestCache.updateFromContainerMenu(currentTarget, mc.player.containerMenu, mc);
            processedChests.add(currentTarget);

            // Dynamic Re-planning: If user still has items, check if they can go elsewhere
            if (hasItemsToStash()) {
                forceRefreshActiveCache();
                calculateStashPlan();

                if (!stashQueue.isEmpty()) {
                    stashIterator = stashQueue.entrySet().iterator();
                    return State.CLOSING_AFTER_STASH;
                }
            }
            return State.CLOSING_AFTER_STASH;
        }
        return State.WAITING_FOR_UPDATE;
    }

    private State handleOpeningContainer(State nextState) {
        if (currentTarget == null) return State.IDLE;

        openContainerSilent(currentTarget);
        this.waitTimer = DEFAULT_WAIT_TICKS;
        return nextState;
    }

    private State handleWaitingForContainer(State successState, State timeoutState) {
        if (containerReady && silentContainerId != -1) {
            return successState;
        }
        if (--waitTimer <= 0) {
            return timeoutState;
        }
        return currentState;
    }

    private State handleClosingContainer(State nextState) {
        closeContainerSafe();

        if (nextState == State.OPENING_FOR_STASH) {
            moveToNextStashTarget();
            return currentState; // State updated inside moveToNextStashTarget
        }
        return nextState;
    }

    private void openContainerSilent(BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        BlockHitResult hitResult = new BlockHitResult(center, Direction.UP, pos, false);

        containerReady = false;
        silentContainerId = -1;

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }
    // --- Network & Interaction Helpers ---

    private void closeContainerSafe() {
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.connection.send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            mc.player.containerMenu = mc.player.inventoryMenu;
        }
        silentContainerId = -1;
        containerReady = false;
    }

    private void sendQuickMovePacket(AbstractContainerMenu menu, int slotId) {
        if (mc.gameMode != null) {
            mc.gameMode.handleInventoryMouseClick(menu.containerId, slotId, 0, ClickType.QUICK_MOVE, mc.player);
        }
    }

    public void onSilentContainerOpen(int containerId, net.minecraft.world.inventory.MenuType<?> menuType) {
        this.silentContainerId = containerId;
        this.containerReady = true;
    }

    public void onManualContainerOpen(int containerId, BlockPos containerPos) {
        ManualContainerTracker.onManualContainerOpen(containerId, containerPos, mc, (pos) -> {
            ChestCache.updateFromContainerMenu(pos, mc.player.containerMenu, mc);
            cacheDirty = true;
        });
    }

    // --- Utilities ---
    public void forceRefreshActiveCache() {
        if (mc.player == null) return;

        StorageManager sm = ModuleManager.INSTANCE.getModule(StorageManager.class);
        double scanRange = (sm != null) ? sm.scanRange.getValue() : 16.0;

        ChestCache.refreshActiveCache(mc.player.blockPosition(), scanRange);
        cacheDirty = ChestCache.DIRTY_FLAG.get();
    }

    public boolean isSilentMode() {
        return this.isEnabled();
    }

    private boolean hasItemsToStash() {
        int start = includeHotbar.getValue() ? 0 : HOTBAR_SIZE;
        for (int i = start; i < PLAYER_INVENTORY_SIZE; i++) {
            if (!mc.player.getInventory().getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidContainer(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
    }

    private boolean isDuplicateDoubleChest(BlockEntity be) {
        if (be instanceof ChestBlockEntity chest) {
            var state = chest.getBlockState();
            // Filter out the 'LEFT' part of a double chest to avoid double scanning
            if (state.hasProperty(ChestBlock.TYPE)) {
                return state.getValue(ChestBlock.TYPE) == ChestType.LEFT;
            }
        }
        return false;
    }

    private void sendMessage(String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§b[AutoStash] §r" + message), false);
        }
    }

    // FIX: Signature updated to match StashPlanner's BiConsumer<String, String> requirement
    private void logDebug(String message, String extra) {
        // Optional: Implement logging if needed
        // System.out.println("[AutoStash Debug] " + message + " " + extra);
    }

    // State Machine
    private enum State {
        IDLE,
        // Scan Cycle
        SCANNING_WORLD, OPENING_FOR_SCAN, WAITING_FOR_SCAN_OPEN, SCANNING_CONTENTS, CLOSING_AFTER_SCAN,
        // Stash Cycle
        OPENING_FOR_STASH, WAITING_FOR_STASH_OPEN, STASHING_ITEMS, WAITING_FOR_UPDATE, CLOSING_AFTER_STASH
    }
}