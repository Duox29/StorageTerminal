package com.duox.storagemanager.modules;

import com.duox.storagemanager.logic.ChestScoringEngine;
import com.duox.storagemanager.logic.StashPlanner;
import com.duox.storagemanager.system.Module;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.system.settings.BooleanSetting;
import com.duox.storagemanager.system.settings.NumberSetting;
import com.duox.storagemanager.utils.ChestCache;
import com.duox.storagemanager.utils.InventoryUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/* Scans chests and stashes matching items. */
public class AutoStash extends Module {

    private static final int HOTBAR_SIZE = 9;
    private static final int PLAYER_INVENTORY_SIZE = 36;
    private static final int MAIN_INVENTORY_SIZE = 27;
    private static final int DEFAULT_WAIT_TICKS = 20;
    private static final int SERVER_SYNC_WAIT_TICKS = 10;

    private final NumberSetting range = new NumberSetting("Range", 5.0, 1.0, 10.0, 0.5);
    private final BooleanSetting includeHotbar = new BooleanSetting("Include Hotbar", false);
    private final NumberSetting itemsPerTick = new NumberSetting("Items/Tick", 4.0, 1.0, 27.0, 1.0);
    private boolean rebuildCacheNextRun = false;
    private final NumberSetting distanceWeight = new NumberSetting("Distance Weight", 0.2, 0.0, 1.0, 0.1);
    private final NumberSetting itemConcentrationWeight = new NumberSetting("Item Concentration Weight", 0.4, 0.0, 1.0, 0.1);
    private final NumberSetting spaceWeight = new NumberSetting("Space Weight", 0.4, 0.0, 1.0, 0.1);
    private final List<BlockPos> scanQueue = new ArrayList<>();
    private final Set<BlockPos> processedChests = new HashSet<>();
    private State currentState = State.IDLE;
    private BlockPos currentTarget = null;
    private int waitTimer = 0;
    private int silentContainerId = -1;
    private Map<BlockPos, List<Integer>> stashQueue = new LinkedHashMap<>();
    private Iterator<Map.Entry<BlockPos, List<Integer>>> stashIterator;
    private Map.Entry<BlockPos, List<Integer>> currentStashEntry;
    private StashPlanner stashPlanner;
    private boolean isDumpMode = false;

    public AutoStash() {
        super("AutoStash", "Scans chests to build a cache, then intelligently stashes items.");
        this.getKeyMapping().setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_B));

        List.of(range, includeHotbar, itemsPerTick, distanceWeight, itemConcentrationWeight, spaceWeight)
                .forEach(this::addSetting);
    }

    public double getRange() {
        return range.getValue();
    }

    public boolean isIncludeHotbar() {
        return includeHotbar.getValue();
    }

    public void toggleIncludeHotbar() {
        includeHotbar.setValue(!includeHotbar.getValue());
    }

    public void requestCacheRebuild() {
        rebuildCacheNextRun = true;
    }

    @Override
    public void onEnable() {
        if (!validateEnvironment()) {
            this.setEnabled(false);
            return;
        }
        resetState();
        initializePlanner();

        if (rebuildCacheNextRun) {
            startRebuildCache();
        } else {
            startSmartStash();
        }
    }

    @Override
    public void onDisable() {
        closeContainerSafe();
        resetState();
        this.isDumpMode = false;

    }

    @Override
    public void onWorldChanged() {
        resetState();
        this.isDumpMode = false;
    }

    private void resetState() {
        currentState = State.IDLE;
        currentTarget = null;
        silentContainerId = -1;
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
        stashPlanner = new StashPlanner(engine);
    }
    public void startDumpAll() {
        this.isDumpMode = true;
        this.setEnabled(true);
    }
    @Override
    public void onTick() {
        if (!validateEnvironment()) return;
        this.currentState = handleState(this.currentState);
    }

    private boolean validateEnvironment() {
        if (mc.player == null || mc.level == null) {
            this.setEnabled(false);
            return false;
        }
        return true;
    }

    /* Advances the state machine one step. */
    private State handleState(State state) {
        return switch (state) {
            case IDLE -> State.IDLE;

            case SCANNING_WORLD -> handleScanningWorld();
            case OPENING_FOR_SCAN -> handleOpeningContainer(State.WAITING_FOR_SCAN_OPEN);
            case WAITING_FOR_SCAN_OPEN -> handleWaitingForContainer(State.SCANNING_CONTENTS, State.SCANNING_WORLD);
            case SCANNING_CONTENTS -> handleScanningContents();
            case CLOSING_AFTER_SCAN -> handleClosingContainer(State.SCANNING_WORLD);

            case OPENING_FOR_STASH -> handleOpeningContainer(State.WAITING_FOR_STASH_OPEN);
            case WAITING_FOR_STASH_OPEN -> handleWaitingForContainer(State.STASHING_ITEMS, State.CLOSING_AFTER_STASH);
            case STASHING_ITEMS -> handleStashingItems();
            case WAITING_FOR_UPDATE -> handleWaitingForUpdate();
            case CLOSING_AFTER_STASH -> handleClosingContainer(State.OPENING_FOR_STASH);
        };
    }

    private void startRebuildCache() {
        BlockPos playerPos = mc.player.blockPosition();
        int r = range.getInt();


        BlockPos.betweenClosedStream(playerPos.offset(-r, -r, -r), playerPos.offset(r, r, r))
                .filter(pos -> {
                    BlockEntity be = mc.level.getBlockEntity(pos);
                    return InventoryUtils.isStorable(be) && !InventoryUtils.isDoubleChestLeftHalf(be.getBlockState());
                })
                .map(BlockPos::immutable)
                .forEach(scanQueue::add);

        scanQueue.sort(Comparator.comparingDouble(pos -> pos.distSqr(playerPos)));
        currentState = State.SCANNING_WORLD;
    }

    private State handleScanningWorld() {
        if (scanQueue.isEmpty()) {
            rebuildCacheNextRun = false;
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
        ChestCache.ensureLoaded(mc);

        forceRefreshActiveCache();

        stashQueue = stashPlanner.calculatePlan(
                mc.player,
                includeHotbar.getValue(),
                range.getValue(),
                ChestCache.getActiveCache(),
                processedChests,
                isDumpMode
        );

        if (stashQueue.isEmpty()) {
            sendMessage(isDumpMode ? "No suitable chests found for dumping." : "Nothing to stash.");
            this.setEnabled(false);
            isDumpMode = false;
            return;
        }

        stashIterator = stashQueue.entrySet().iterator();
        moveToNextStashTarget();
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
                InventoryUtils.sendClickPacket(menu, menuSlotId, 0, ContainerInput.QUICK_MOVE);
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

    /* Maps an inventory slot to its menu slot id. */
    private int mapInventorySlotToMenuSlot(int invSlotIndex, int containerSlots) {
        if (invSlotIndex < HOTBAR_SIZE) {
            return containerSlots + MAIN_INVENTORY_SIZE + invSlotIndex;
        } else {
            return containerSlots + (invSlotIndex - HOTBAR_SIZE);
        }
    }

    private State handleWaitingForUpdate() {
        if (--waitTimer <= 0) {
            // Refresh the cache after moving items.
            ChestCache.updateFromContainerMenu(currentTarget, mc.player.containerMenu, mc);
            processedChests.add(currentTarget);

            // Re-plan if items remain.
            if (hasItemsToStash()) {
                forceRefreshActiveCache();
                stashQueue = stashPlanner.calculatePlan(
                        mc.player,
                        includeHotbar.getValue(),
                        range.getValue(),
                        ChestCache.getActiveCache(),
                        processedChests,
                        false
                );

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
        if (silentContainerId != -1) {
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
            return currentState;
        }
        return nextState;
    }

    private void openContainerSilent(BlockPos pos) {
        silentContainerId = -1;
        InventoryUtils.useItemOnSilently(pos);
    }

    private void closeContainerSafe() {
        InventoryUtils.closeContainerSilent();
        silentContainerId = -1;
    }

    public void onSilentContainerOpen(int containerId, net.minecraft.world.inventory.MenuType<?> menuType) {
        this.silentContainerId = containerId;
    }

    public void forceRefreshActiveCache() {
        ChestCache.refreshActiveCache(mc);
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

    private void sendMessage(String message) {
        com.duox.storagemanager.utils.ToastUtils.sendToast("§bAutoStash", message);
    }

    private enum State {
        IDLE,
        SCANNING_WORLD, OPENING_FOR_SCAN, WAITING_FOR_SCAN_OPEN, SCANNING_CONTENTS, CLOSING_AFTER_SCAN,
        OPENING_FOR_STASH, WAITING_FOR_STASH_OPEN, STASHING_ITEMS, WAITING_FOR_UPDATE, CLOSING_AFTER_STASH
    }
}