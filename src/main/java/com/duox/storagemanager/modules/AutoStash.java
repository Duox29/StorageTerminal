package com.duox.storagemanager.modules;

import com.duox.storagemanager.logic.ChestScoringEngine;
import com.duox.storagemanager.logic.StashPlanner;
import com.duox.storagemanager.system.Module;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.system.settings.BooleanSetting;
import com.duox.storagemanager.system.settings.NumberSetting;
import com.duox.storagemanager.utils.ChestCache;
import com.duox.storagemanager.utils.InventoryUtils;
import com.duox.storagemanager.utils.ItemSerializer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

/* Scans chests and stashes matching items. */
public class AutoStash extends Module {

    private static final int HOTBAR_SIZE = 9;
    private static final int PLAYER_INVENTORY_SIZE = 36;
    private static final int DEFAULT_WAIT_TICKS = 20;
    private static final int SERVER_SYNC_WAIT_TICKS = 10;

    private final NumberSetting range = new NumberSetting("Range", 5.0, 1.0, 10.0, 0.5);
    private final BooleanSetting includeHotbar = new BooleanSetting("Include Hotbar", false);
    private final NumberSetting itemsPerTick = new NumberSetting("Items/Tick", 4.0, 1.0, 27.0, 1.0);
    private boolean rebuildCacheNextRun = false;
    private final NumberSetting distanceWeight = new NumberSetting("Distance Weight", 0.2, 0.0, 1.0, 0.1);
    private final NumberSetting itemConcentrationWeight = new NumberSetting("Item Concentration Weight", 0.4, 0.0, 1.0, 0.1);
    private final NumberSetting spaceWeight = new NumberSetting("Space Weight", 0.4, 0.0, 1.0, 0.1);
    private final BooleanSetting sendToasts = new BooleanSetting("Send Toasts", true);
    private final NumberSetting chestOpenWait = new NumberSetting("Chest Open Wait", DEFAULT_WAIT_TICKS,
            1.0, 100.0, 1.0);
    private final NumberSetting syncWait = new NumberSetting("Sync Wait", SERVER_SYNC_WAIT_TICKS,
            1.0, 100.0, 1.0);
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
    private Integer targetedInventorySlot = null;
    private int targetedExpectedRemainingCount = 0;
    private ItemStack targetedExpectedStack = ItemStack.EMPTY;
    private boolean cancelTargeted;

    public AutoStash() {
        super("AutoStash", "Scans chests to build a cache, then intelligently stashes items.");
        this.getKeyMapping().setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_B));

        List.of(range, includeHotbar, itemsPerTick, distanceWeight, itemConcentrationWeight, spaceWeight,
                        sendToasts, chestOpenWait, syncWait)
                .forEach(this::addSetting);
    }

    public double getRange() {
        return range.getValue();
    }

    public boolean isIncludeHotbar() {
        return includeHotbar.getValue();
    }

    public boolean isSendToasts() {
        return sendToasts.getValue();
    }

    public void toggleIncludeHotbar() {
        includeHotbar.setValue(!includeHotbar.getValue());
    }

    public void requestCacheRebuild() {
        rebuildCacheNextRun = true;
    }

    public boolean startTargetedStash(int inventorySlot, ItemStack expectedStack) {
        if (isEnabled() || mc.player == null || mc.level == null
                || !mc.player.containerMenu.getCarried().isEmpty()
                || inventorySlot < 0 || inventorySlot >= PLAYER_INVENTORY_SIZE
                || expectedStack == null || expectedStack.isEmpty()
                || !ItemStack.isSameItemSameComponents(mc.player.getInventory().getItem(inventorySlot), expectedStack)
                || mc.player.getInventory().getItem(inventorySlot).getCount() != expectedStack.getCount()) {
            return false;
        }
        targetedInventorySlot = inventorySlot;
        targetedExpectedStack = expectedStack.copy();
        targetedExpectedRemainingCount = expectedStack.getCount();
        isDumpMode = false;
        setEnabled(true);
        // onEnable() can immediately disable the module if no suitable chest exists.
        return isEnabled();
    }

    public boolean isSilentMode() {
        return (isEnabled() || cancelTargeted) && currentState != State.IDLE && currentState != State.WAITING_FOR_TRANSFER;
    }

    public boolean isWaitingForSilentOpen() {
        return isSilentMode() && (currentState == State.WAITING_FOR_SCAN_OPEN
                || currentState == State.WAITING_FOR_STASH_OPEN);
    }

    public boolean isCancelling() {
        return cancelTargeted;
    }

    public void cancelTargetedStash() {
        if (targetedInventorySlot != null) cancelTargeted = true;
    }

    @Override
    public void onEnable() {
        if (cancelTargeted) return;
        if (!validateEnvironment()) {
            this.setEnabled(false);
            return;
        }
        resetState();
        initializePlanner();

        StorageManager storage = ModuleManager.INSTANCE.getModule(StorageManager.class);
        if (storage != null && storage.isRetrieving()) {
            currentState = State.WAITING_FOR_TRANSFER;
        } else {
            beginOperation();
        }
    }

    private void beginOperation() {
        if (rebuildCacheNextRun && targetedInventorySlot == null) {
            startRebuildCache();
        } else {
            startSmartStash();
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null && mc.level != null && silentContainerId == -1 && waitTimer > 0
                && (currentState == State.WAITING_FOR_SCAN_OPEN || currentState == State.WAITING_FOR_STASH_OPEN)) {
            cancelTargeted = true;
            return;
        }
        if (silentContainerId != -1 && mc.player != null
                && mc.player.containerMenu.containerId == silentContainerId) closeContainerSafe();
        resetState();
        this.isDumpMode = false;
        targetedInventorySlot = null;
        targetedExpectedRemainingCount = 0;
        targetedExpectedStack = ItemStack.EMPTY;
        cancelTargeted = false;
    }

    @Override
    public void onWorldChanged() {
        if (targetedInventorySlot != null && isEnabled()) setEnabled(false);
        resetState();
        this.isDumpMode = false;
        targetedInventorySlot = null;
        targetedExpectedRemainingCount = 0;
        targetedExpectedStack = ItemStack.EMPTY;
        cancelTargeted = false;
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
        if (isEnabled() || cancelTargeted) return;
        this.isDumpMode = true;
        this.setEnabled(true);
    }
    @Override
    public void onTick() {
        if (!validateEnvironment()) return;
        if (cancelTargeted) {
            if ((currentState == State.WAITING_FOR_STASH_OPEN || currentState == State.WAITING_FOR_SCAN_OPEN)
                    && silentContainerId == -1 && waitTimer > 0) {
                currentState = handleWaitingForContainer(State.STASHING_ITEMS, State.CLOSING_AFTER_STASH);
            } else {
                setEnabled(false);
            }
            return;
        }
        // A real inventory drag can start while the visible menu is still
        // current. Do not open or close a silent menu with that stack in hand.
        if (targetedInventorySlot != null && !mc.player.containerMenu.getCarried().isEmpty()) return;
        // A targeted stash authorizes only the original stack. A player or another
        // mod may change that slot while we wait for a silent chest to open or sync.
        if (targetedInventorySlot != null && !targetedStackMatchesExpected()) {
            sendMessage("Selected stack changed; stash cancelled to protect inventory.");
            setEnabled(false);
            return;
        }
        this.currentState = handleState(this.currentState);
    }

    private boolean targetedStackMatchesExpected() {
        if (targetedInventorySlot == null) return true;
        var stack = mc.player.getInventory().getItem(targetedInventorySlot);
        if (targetedExpectedRemainingCount == 0) return stack.isEmpty();
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, targetedExpectedStack)
                && stack.getCount() == targetedExpectedRemainingCount;
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
            case WAITING_FOR_TRANSFER -> {
                StorageManager storage = ModuleManager.INSTANCE.getModule(StorageManager.class);
                if (storage != null && storage.isRetrieving()) yield State.WAITING_FOR_TRANSFER;
                beginOperation();
                yield currentState;
            }

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
        if (silentContainerId != -1 && mc.player.containerMenu.containerId == silentContainerId) {
            ChestCache.updateFromContainerMenu(currentTarget, mc.player.containerMenu, mc);
        }
        return State.CLOSING_AFTER_SCAN;
    }

    private void startSmartStash() {
        ChestCache.ensureLoaded(mc);

        forceRefreshActiveCache();

        stashQueue = planStash(isDumpMode);

        if (stashQueue.isEmpty()) {
            sendMessage(targetedInventorySlot != null ? "No suitable chest found for selected item."
                    : isDumpMode ? "No suitable chests found for dumping." : "Nothing to stash.");
            this.setEnabled(false);
            return;
        }

        stashIterator = stashQueue.entrySet().iterator();
        moveToNextStashTarget();
    }

    private Map<BlockPos, List<Integer>> planStash(boolean forceDump) {
        if (targetedInventorySlot != null) {
            return stashPlanner.calculatePlanForSlot(mc.player, targetedInventorySlot, range.getValue(),
                    ChestCache.getActiveCache(), processedChests, true);
        }
        return stashPlanner.calculatePlan(mc.player, includeHotbar.getValue(), range.getValue(),
                ChestCache.getActiveCache(), processedChests, forceDump);
    }

    private void moveToNextStashTarget() {
        if (stashIterator != null && stashIterator.hasNext()) {
            currentStashEntry = stashIterator.next();
            currentTarget = currentStashEntry.getKey();
            currentState = State.OPENING_FOR_STASH;
        } else {
            if (targetedInventorySlot != null) reportTargetedStashResult();
            this.setEnabled(false);
        }
    }

    private void reportTargetedStashResult() {
        var remainingStack = mc.player.getInventory().getItem(targetedInventorySlot);
        int remaining = !remainingStack.isEmpty()
                && ItemStack.isSameItemSameComponents(remainingStack, targetedExpectedStack)
                ? remainingStack.getCount() : 0;
        int initial = targetedExpectedStack.getCount();
        int moved = Math.max(0, initial - remaining);
        if (moved == 0) {
            sendMessage("Could not stash selected item; available chests may be full.");
        } else if (remaining > 0) {
            sendMessage("Stashed " + moved + "/" + initial + " selected items (partial).");
        } else {
            sendMessage("Stashed " + moved + " items.");
        }
    }

    private State handleStashingItems() {
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (silentContainerId == -1 || menu.containerId != silentContainerId) {
            return State.CLOSING_AFTER_STASH;
        }
        int containerSlots = menu.slots.size() - PLAYER_INVENTORY_SIZE;

        if (containerSlots <= 0) return State.CLOSING_AFTER_STASH;

        List<Integer> slotsToMove = currentStashEntry.getValue();
        int limit = itemsPerTick.getInt();
        int moves = 0;

        Iterator<Integer> it = slotsToMove.iterator();
        while (it.hasNext() && moves < limit) {
            int invSlotIndex = it.next();
            int menuSlotId = InventoryUtils.mapInventorySlotToMenuSlot(invSlotIndex, containerSlots);

            if (menu.getSlot(menuSlotId).hasItem()) {
                int before = menu.getSlot(menuSlotId).getItem().getCount();
                if (targetedInventorySlot != null && (invSlotIndex != targetedInventorySlot
                        || before != targetedExpectedRemainingCount
                        || !ItemStack.isSameItemSameComponents(menu.getSlot(menuSlotId).getItem(), targetedExpectedStack))) {
                    sendMessage("Selected stack changed; stash cancelled to protect inventory.");
                    setEnabled(false);
                    return State.IDLE;
                }
                InventoryUtils.sendClickPacket(menu, menuSlotId, 0, ContainerInput.QUICK_MOVE);
                if (targetedInventorySlot != null) {
                    targetedExpectedRemainingCount = menu.getSlot(menuSlotId).getItem().getCount();
                }
                moves++;
            }
            it.remove();
        }

        if (slotsToMove.isEmpty()) {
            waitTimer = syncWait.getInt();
            return State.WAITING_FOR_UPDATE;
        }

        return State.STASHING_ITEMS;
    }

    private State handleWaitingForUpdate() {
        if (--waitTimer <= 0) {
            // A manually opened or replaced menu must never overwrite this chest's cache.
            if (silentContainerId == -1 || mc.player.containerMenu.containerId != silentContainerId) {
                return State.CLOSING_AFTER_STASH;
            }
            ChestCache.updateFromContainerMenu(currentTarget, mc.player.containerMenu, mc);
            processedChests.add(currentTarget);

            // Re-plan if items remain.
            if (hasItemsToStash()) {
                forceRefreshActiveCache();
                stashQueue = planStash(false);

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
        this.waitTimer = chestOpenWait.getInt();
        return nextState;
    }

    private State handleWaitingForContainer(State successState, State timeoutState) {
        if (silentContainerId != -1 && mc.player.containerMenu.containerId == silentContainerId) {
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
        InventoryUtils.closeSilentIfOpen(silentContainerId);
        silentContainerId = -1;
    }

    public void onSilentContainerOpen(int containerId, net.minecraft.world.inventory.MenuType<?> menuType) {
        if ((currentState == State.WAITING_FOR_SCAN_OPEN || currentState == State.WAITING_FOR_STASH_OPEN)
                && InventoryUtils.matchesStorageMenu(currentTarget, menuType)) {
            this.silentContainerId = containerId;
        }
    }

    public void forceRefreshActiveCache() {
        ChestCache.refreshActiveCache(mc);
    }

    private boolean hasItemsToStash() {
        if (targetedInventorySlot != null) {
            var stack = mc.player.getInventory().getItem(targetedInventorySlot);
            return targetedExpectedRemainingCount > 0 && !stack.isEmpty()
                    && ItemStack.isSameItemSameComponents(stack, targetedExpectedStack)
                    && stack.getCount() == targetedExpectedRemainingCount;
        }
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
        IDLE, WAITING_FOR_TRANSFER,
        SCANNING_WORLD, OPENING_FOR_SCAN, WAITING_FOR_SCAN_OPEN, SCANNING_CONTENTS, CLOSING_AFTER_SCAN,
        OPENING_FOR_STASH, WAITING_FOR_STASH_OPEN, STASHING_ITEMS, WAITING_FOR_UPDATE, CLOSING_AFTER_STASH
    }
}
