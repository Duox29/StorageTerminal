package com.duox.storagemanager.modules;

import com.duox.storagemanager.gui.StorageScreen;
import com.duox.storagemanager.logic.StorageTransferCoordinator;
import com.duox.storagemanager.system.ModuleManager;
import com.duox.storagemanager.system.Module;
import com.duox.storagemanager.system.settings.BooleanSetting;
import com.duox.storagemanager.system.settings.NumberSetting;
import com.duox.storagemanager.utils.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/* Plans and executes item retrieval from cached chests. */
public class StorageManager extends Module {
    // Item id -> requested quantity.
    private final Map<String, Integer> requestQueue = new HashMap<>();
    private final Map<String, Integer> activeRequests = new HashMap<>();

    // Chest position -> items to withdraw.
    private final Map<BlockPos, Map<String, Integer>> retrievalPlan = new HashMap<>();
    private Iterator<Map.Entry<BlockPos, Map<String, Integer>>> retrievalIterator;
    private Map.Entry<BlockPos, Map<String, Integer>> currentTargetEntry;

    private RecipeDisplayId pendingRecipeId = null;
    private boolean pendingRecipePlaceAll = false;

    private enum State {
        IDLE,
        PLANNING,
        OPENING_CHEST,
        WAITING_FOR_OPEN,
        WITHDRAWING,
        CLOSING_CHEST,
        WAITING_FOR_RETURN_OPEN
    }

    private State currentState = State.IDLE;
    private BlockPos returnToContainerPos = null;
    private int waitTimer = 0;
    private int silentContainerId = -1;
    private int targetedInventorySlot = -1;
    private int targetedRequestedCount;
    private int targetedTransferredCount;
    private ItemStack targetedExpectedStack = ItemStack.EMPTY;
    private boolean cancelTargeted;

    public final NumberSetting panelX = new NumberSetting("Panel X", 300, 0, 2560, 1);
    public final NumberSetting panelY = new NumberSetting("Panel Y", 100, 0, 1440, 1);
    public final BooleanSetting autoRequestRecipe = new BooleanSetting("Auto-Request Recipes", true);

    public final NumberSetting panelScale = new NumberSetting("Panel Scale", 1.0, 0.5, 2.0, 0.1);
    public final NumberSetting panelGridCols = new NumberSetting("Panel Columns", 8, 4, 16, 1);
    public final NumberSetting panelGridRows = new NumberSetting("Panel Rows", 7, 4, 16, 1);

    public final NumberSetting screenScale = new NumberSetting("Screen Scale", 1.0, 0.5, 2.0, 0.1);
    public final NumberSetting screenGridCols = new NumberSetting("Screen Columns", 9, 4, 16, 1);
    public final NumberSetting screenGridRows = new NumberSetting("Screen Rows", 9, 4, 16, 1);

    // Radius used to build the active cache.
    public final NumberSetting scanRange = new NumberSetting("Scan Range", 16.0, 4.0, 64.0, 1.0);

    // Retrieval pacing. Waits are inclusive tick counts, so each minimum delays one extra tick.
    public final NumberSetting chestOpenWait = new NumberSetting("Chest Open Wait", 11.0, 1.0, 100.0, 1.0);
    public final NumberSetting withdrawDelay = new NumberSetting("Withdraw Delay", 0.0, 0.0, 100.0, 1.0);
    public final NumberSetting withdrawPerTick = new NumberSetting("Withdraw/Tick", 1.0, 1.0, 27.0, 1.0);

    public StorageManager() {
        super("StorageManager", "Manage items from cached chests.");
        this.getKeyMapping().setKey(
                InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_V));
        this.addSetting(panelX);
        this.addSetting(panelY);
        this.addSetting(autoRequestRecipe);
        this.addSetting(panelScale);
        this.addSetting(panelGridCols);
        this.addSetting(panelGridRows);
        this.addSetting(screenScale);
        this.addSetting(screenGridCols);
        this.addSetting(screenGridRows);
        this.addSetting(scanRange);
        this.addSetting(chestOpenWait);
        this.addSetting(withdrawDelay);
        this.addSetting(withdrawPerTick);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || cancelTargeted)
            return;

        if (!requestQueue.isEmpty()) {
            startRetrieval();
        } else {
            mc.gui.setScreen(new StorageScreen(this));
        }
    }

    @Override
    public void onDisable() {
        StorageTransferCoordinator.cancelForDisable();
        if (mc.player != null && mc.level != null && currentState == State.WAITING_FOR_OPEN
                && silentContainerId == -1 && waitTimer > 0) {
            cancelTargeted = true;
            clearRequestQueue();
            return;
        }
        if (mc.player != null && silentContainerId != -1
                && mc.player.containerMenu.containerId == silentContainerId) {
            InventoryUtils.closeContainerSilent();
        }
        resetState();
        clearRequestQueue();
    }

    @Override
    public void onWorldChanged() {
        if (mc.player != null && silentContainerId != -1
                && mc.player.containerMenu.containerId == silentContainerId) {
            InventoryUtils.closeContainerSilent();
        }
        resetState();
        clearRequestQueue();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            this.setEnabled(false); // Disable when the player leaves the world.
            return;
        }

        if (cancelTargeted) {
            if (currentState == State.WAITING_FOR_OPEN && silentContainerId == -1 && waitTimer > 0) {
                waitForContainer();
            } else {
                if (silentContainerId != -1 && mc.player.containerMenu.containerId == silentContainerId)
                    InventoryUtils.closeContainerSilent();
                resetState();
            }
            return;
        }

        switch (currentState) {
            case PLANNING:
                calculateRetrievalPlan();
                break;
            case OPENING_CHEST:
                openTargetSilent();
                break;
            case WAITING_FOR_OPEN:
                waitForContainer();
                break;
            case WITHDRAWING:
                performWithdrawal();
                break;
            case CLOSING_CHEST:
                closeSilent();
                break;
            case WAITING_FOR_RETURN_OPEN:
                handleWaitingForReturn();
                break;
            case IDLE:
                break;
        }
    }

    public void addToRequestQueue(String itemId, int quantity) {
        requestQueue.put(itemId, requestQueue.getOrDefault(itemId, 0) + quantity);
    }

    public void clearRequestQueue() {
        requestQueue.clear();
        activeRequests.clear();
        pendingRecipeId = null;
        pendingRecipePlaceAll = false;
    }

    public void clearPendingRequests() {
        requestQueue.clear();
        if (currentState == State.IDLE && activeRequests.isEmpty()) {
            pendingRecipeId = null;
            pendingRecipePlaceAll = false;
        }
    }

    public boolean isRetrieving() {
        return currentState != State.IDLE || !activeRequests.isEmpty();
    }

    public void cancelTargetedRetrieval() {
        if (targetedInventorySlot >= 0) cancelTargeted = true;
    }

    public boolean isCancelling() {
        return cancelTargeted;
    }

    public int getQueued(String itemId) {
        return requestQueue.getOrDefault(itemId, 0);
    }

    public void adjustQueued(String itemId, int delta, int max) {
        int target = Math.max(0, Math.min(max, getQueued(itemId) + delta));
        if (target == 0) {
            requestQueue.remove(itemId);
        } else {
            requestQueue.put(itemId, target);
        }
    }

    public void startRetrieval() {
        startRetrieval(null, false);
    }

    public void startRetrieval(RecipeDisplayId recipe, boolean placeAll) {
        if (requestQueue.isEmpty()) {
            if (currentState == State.IDLE) {
                sendMessage("Queue is empty.");
            }
            return;
        }

        StorageTransferCoordinator.retrieveRequests(requestQueue, recipe, placeAll);
        requestQueue.clear();
    }

    public boolean startQueuedRetrieval(Map<String, Integer> requests, RecipeDisplayId recipe, boolean placeAll) {
        if (!isEnabled() || mc.player == null || currentState != State.IDLE || !activeRequests.isEmpty()
                || isStashActive() || !mc.player.containerMenu.getCarried().isEmpty()) return false;
        activeRequests.putAll(requests);
        pendingRecipeId = recipe;
        pendingRecipePlaceAll = placeAll;
        returnToContainerPos = null;
        if (recipe != null) captureCraftingTableContext();
        currentState = State.PLANNING;
        return true;
    }

    /** Starts a direct drag transfer into one player inventory slot. */
    public boolean startTargetedRetrieval(String itemId, int quantity, int inventorySlot,
                                           ItemStack expectedStack) {
        if (!isEnabled() || mc.player == null || !mc.player.containerMenu.getCarried().isEmpty()
                || currentState != State.IDLE || !activeRequests.isEmpty() || isStashActive()
                || inventorySlot < 0 || inventorySlot >= InventoryUtils.PLAYER_INVENTORY_SIZE
                || quantity <= 0 || itemId == null || expectedStack == null || expectedStack.isEmpty()
                || !itemId.equals(ItemSerializer.serialize(expectedStack))) {
            return false;
        }

        // A queued drag may start after another transfer fills its chosen slot.
        // Resolve a new empty slot once, before planning the normal request.
        if (!mc.player.getInventory().getItem(inventorySlot).isEmpty()) {
            inventorySlot = InventoryUtils.findEmptyInventorySlot();
            if (inventorySlot < 0) return false;
        }

        targetedInventorySlot = inventorySlot;
        targetedRequestedCount = quantity;
        targetedTransferredCount = 0;
        targetedExpectedStack = expectedStack.copy();
        activeRequests.put(itemId, quantity);
        returnToContainerPos = null;
        currentState = State.PLANNING;
        return true;
    }

    private boolean isStashActive() {
        AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
        return stash != null && stash.isSilentMode();
    }

    private void captureCraftingTableContext() {
        if (mc.player != null && mc.gui.screen() instanceof CraftingScreen screen
                && screen.getMenu() == mc.player.containerMenu) {
            HitResult hit = mc.hitResult;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) hit;
                BlockPos pos = blockHit.getBlockPos();
                if (mc.level != null) {
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.getBlock() instanceof CraftingTableBlock) {
                        returnToContainerPos = pos;
                    }
                }
            }
        }
    }

    public void ensureCacheLoaded() {
        if (ChestCache.ensureLoaded(mc)) {
            int size = ChestCache.getGlobalBuffer().size();
            sendMessage("§aLoaded cache for " + CacheUtils.getDimensionId(mc) + " (" + size + " chests).");
        }
    }

    private void resetState() {
        currentState = State.IDLE;
        returnToContainerPos = null;
        silentContainerId = -1;
        pendingRecipeId = null;
        pendingRecipePlaceAll = false;
        activeRequests.clear();
        retrievalPlan.clear();
        retrievalIterator = null;
        currentTargetEntry = null;
        targetedInventorySlot = -1;
        targetedRequestedCount = 0;
        targetedTransferredCount = 0;
        targetedExpectedStack = ItemStack.EMPTY;
        cancelTargeted = false;
    }

    private void calculateRetrievalPlan() {
        retrievalPlan.clear();
        Map<String, Map<String, Integer>> cache = ChestCache.getActiveCache();

        if (activeRequests.isEmpty()) {
            currentState = State.IDLE;
            return;
        }

        Map<String, Integer> remainingNeeds = drainActiveRequests();
        BlockPos playerPos = mc.player.blockPosition();
        List<String> sortedChests = new ArrayList<>(cache.keySet());
        sortedChests.sort((s1, s2) -> {
            int score1 = getMinRelevantQuantity(cache.get(s1), remainingNeeds);
            int score2 = getMinRelevantQuantity(cache.get(s2), remainingNeeds);
            if (score1 != score2) {
                return Integer.compare(score1, score2);
            }
            BlockPos p1 = CacheUtils.stringToPos(s1);
            BlockPos p2 = CacheUtils.stringToPos(s2);
            return Double.compare(p1.distSqr(playerPos), p2.distSqr(playerPos));
        });

        for (String chestPosStr : sortedChests) {
            Map<String, Integer> contents = cache.get(chestPosStr);
            if (contents == null || contents.isEmpty()) continue;

            BlockPos chestPos = CacheUtils.stringToPos(chestPosStr);
            processChestContents(contents, chestPos, remainingNeeds);
        }

        finalizePlanning(remainingNeeds);
    }

    private Map<String, Integer> drainActiveRequests() {
        Map<String, Integer> remainingNeeds = new HashMap<>(activeRequests);
        activeRequests.clear();
        return remainingNeeds;
    }

    private void finalizePlanning(Map<String, Integer> remainingNeeds) {
        if (!remainingNeeds.isEmpty()) {
            sendMessage("Warning: Cannot find all items. Missing: " + remainingNeeds);
            pendingRecipeId = null;
        }

        if (retrievalPlan.isEmpty()) {
            handleEmptyPlan();
            return;
        }

        retrievalIterator = retrievalPlan.entrySet().iterator();
        moveToNextTarget();
    }

    private void processChestContents(Map<String, Integer> contents, BlockPos chestPos, Map<String, Integer> remainingNeeds) {
        List<String> fulfilledNeeds = new ArrayList<>();

        for (Map.Entry<String, Integer> req : remainingNeeds.entrySet()) {
            String reqId = req.getKey();
            int needed = req.getValue();

            // Prefer an exact item key match.
            if (contents.containsKey(reqId)) {
                int available = contents.get(reqId);
                int toTake = Math.min(needed, available);
                if (toTake > 0) {
                    retrievalPlan.computeIfAbsent(chestPos, k -> new HashMap<>()).put(reqId, toTake);
                    needed -= toTake;
                }
            }

            // Otherwise match by base id for auto-crafting requests.
            if (targetedInventorySlot < 0 && needed > 0 && !reqId.startsWith("{")) {
                for (Map.Entry<String, Integer> chestItem : contents.entrySet()) {
                    String cacheKey = chestItem.getKey();

                    if (ItemSerializer.getBaseId(cacheKey).equals(reqId)) {
                        int available = chestItem.getValue();
                        // Avoid planning more than the chest contains.
                        int planned = retrievalPlan.getOrDefault(chestPos, new HashMap<>()).getOrDefault(cacheKey, 0);
                        int actualAvailable = available - planned;

                        if (actualAvailable > 0) {
                            int toTake = Math.min(needed, actualAvailable);
                            retrievalPlan.computeIfAbsent(chestPos, k -> new HashMap<>()).put(cacheKey, toTake);
                            needed -= toTake;
                            if (needed <= 0) break;
                        }
                    }
                }
            }

            // Update the remaining need.
            if (needed <= 0) {
                fulfilledNeeds.add(reqId);
            } else if (needed != req.getValue()) {
                req.setValue(needed);
            }
        }

        // Remove fully fulfilled requests.
        for (String f : fulfilledNeeds) {
            remainingNeeds.remove(f);
        }
    }

    private void handleEmptyPlan() {
        sendMessage("Could not find any items to retrieve.");
        pendingRecipeId = null;
        finishOrPlanNext();
    }

    private void finishOrPlanNext() {
        currentState = activeRequests.isEmpty() ? State.IDLE : State.PLANNING;
        if (currentState == State.IDLE) {
            targetedInventorySlot = -1;
            targetedExpectedStack = ItemStack.EMPTY;
        }
    }

    private int getMinRelevantQuantity(Map<String, Integer> chestContents, Map<String, Integer> needs) {
        int minQty = Integer.MAX_VALUE;
        boolean foundAny = false;

        for (String neededItem : needs.keySet()) {
            if (chestContents.containsKey(neededItem)) {
                int qty = chestContents.get(neededItem);
                if (qty < minQty) {
                    minQty = qty;
                }
                foundAny = true;
            }
        }
        return foundAny ? minQty : Integer.MAX_VALUE;
    }

    private void moveToNextTarget() {
        if (retrievalIterator != null && retrievalIterator.hasNext()) {
            currentTargetEntry = retrievalIterator.next();
            currentState = State.OPENING_CHEST;
        } else {
            if (targetedInventorySlot >= 0) {
                sendMessage(targetedTransferredCount == 0
                        ? "No dragged items could be placed in the target slot."
                        : "Drag retrieved " + targetedTransferredCount + "/" + targetedRequestedCount + " items.");
            } else {
                sendMessage("Retrieval complete.");
            }
            if (!activeRequests.isEmpty()) {
                currentState = State.PLANNING;
            } else {
                if (returnToContainerPos != null) {
                    openReturnContainer();
                    returnToContainerPos = null;
                    waitTimer = 20;
                    currentState = State.WAITING_FOR_RETURN_OPEN;
                } else {
                    finishOrPlanNext();
                }
            }
        }
    }

    private void handleWaitingForReturn() {
        if (mc.player != null && mc.gui.screen() instanceof CraftingScreen screen
                && screen.getMenu() == mc.player.containerMenu) {
            if (pendingRecipeId != null && mc.player != null) {
                mc.gameMode.handlePlaceRecipe(mc.player.containerMenu.containerId, pendingRecipeId, pendingRecipePlaceAll);
                pendingRecipeId = null;
            }
            finishOrPlanNext();
        } else {
            waitTimer--;
            if (waitTimer <= 0) {
                pendingRecipeId = null;
                finishOrPlanNext();
            }
        }
    }

    private void openTargetSilent() {
        if (currentTargetEntry == null) return;

        silentContainerId = -1;
        InventoryUtils.useItemOnSilently(currentTargetEntry.getKey());

        currentState = State.WAITING_FOR_OPEN;
        waitTimer = chestOpenWait.getInt();
    }

    private void waitForContainer() {
        if (silentContainerId != -1 && mc.player.containerMenu.containerId == silentContainerId) {
            currentState = State.WITHDRAWING;
            return;
        }
        waitTimer--;
        if (waitTimer <= 0) {
            sendMessage("Timeout opening chest at " + currentTargetEntry.getKey());
            currentState = State.CLOSING_CHEST;
        }
    }

    public void onSilentContainerOpen(int containerId, MenuType<?> menuType) {
        if (currentState == State.WAITING_FOR_OPEN && currentTargetEntry != null
                && InventoryUtils.matchesStorageMenu(currentTargetEntry.getKey(), menuType)) {
            this.silentContainerId = containerId;
        }
    }

    public boolean isSilentMode() {
        return (this.isEnabled() || cancelTargeted) && (currentState != State.IDLE
                && currentState != State.PLANNING && currentState != State.WAITING_FOR_RETURN_OPEN);
    }

    public boolean isWaitingForSilentOpen() {
        return currentState == State.WAITING_FOR_OPEN && isSilentMode();
    }

    public boolean isRestoringCraftingScreen() {
        return currentState == State.WAITING_FOR_RETURN_OPEN && mc.player != null
                && mc.gui.screen() instanceof CraftingScreen screen
                && screen.getMenu() != mc.player.containerMenu
                && mc.player.containerMenu instanceof net.minecraft.world.inventory.CraftingMenu;
    }

    private void performWithdrawal() {
        if (waitTimer > 0) {
            waitTimer--;
            return;
        }
        waitTimer = withdrawDelay.getInt();

        AbstractContainerMenu menu = mc.player.containerMenu;
        if (silentContainerId == -1 || menu.containerId != silentContainerId) {
            currentState = State.CLOSING_CHEST;
            return;
        }
        int containerSlots = menu.slots.size() - InventoryUtils.PLAYER_INVENTORY_SIZE;

        if (containerSlots <= 0) {
            currentState = State.CLOSING_CHEST;
            return;
        }

        Map<String, Integer> itemsToTake = currentTargetEntry.getValue();

        if (!processContainerSlots(menu, itemsToTake, containerSlots)) {
            currentState = State.CLOSING_CHEST;
        }
    }

    private boolean processContainerSlots(AbstractContainerMenu menu, Map<String, Integer> itemsToTake, int containerSlots) {
        int limit = withdrawPerTick.getInt();
        int moves = 0;

        for (int i = 0; i < containerSlots && moves < limit; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty()) continue;

            String itemId = ItemSerializer.serialize(stack);
            if (!itemsToTake.containsKey(itemId)) continue;
            if (targetedInventorySlot >= 0
                    && !ItemStack.isSameItemSameComponents(stack, targetedExpectedStack)) continue;

            int needed = itemsToTake.get(itemId);
            if (needed <= 0) continue;

            if (tryWithdrawItem(menu, i, stack, itemId, needed, containerSlots)) {
                moves++;
            }
        }
        return moves > 0;
    }

    private boolean tryWithdrawItem(AbstractContainerMenu menu, int slotIndex, ItemStack stack, String itemId,
                                    int needed, int containerSlots) {
        int inSlot = stack.getCount();
        int actualTaken;

        if (targetedInventorySlot >= 0) {
            actualTaken = transferToTargetSlot(menu, slotIndex, stack, needed, containerSlots);
        } else if (inSlot <= needed) {
            actualTaken = transferFullStack(menu, slotIndex, stack);
        } else {
            actualTaken = transferPartialStack(menu, slotIndex, needed);
        }

        if (actualTaken > 0) {
            if (targetedInventorySlot >= 0) targetedTransferredCount += actualTaken;
            updateCache(itemId, actualTaken);
            currentTargetEntry.getValue().put(itemId, needed - actualTaken);
            return true;
        }
        return false;
    }

    private int transferToTargetSlot(AbstractContainerMenu menu, int chestSlot, ItemStack stack,
                                     int needed, int containerSlots) {
        int playerSlot = InventoryUtils.mapInventorySlotToMenuSlot(targetedInventorySlot, containerSlots);
        if (playerSlot < containerSlots || playerSlot >= menu.slots.size()
                || !menu.getCarried().isEmpty()
                || !ItemStack.isSameItemSameComponents(stack, targetedExpectedStack)) return 0;

        int move = Math.min(Math.min(needed, stack.getCount()),
                InventoryUtils.getSlotSpace(menu.getSlot(playerSlot), stack));
        return move > 0 ? moveItemsToSlot(menu, chestSlot, playerSlot, move) : 0;
    }

    private int moveItemsToSlot(AbstractContainerMenu menu, int sourceSlot, int targetSlot, int move) {
        ItemStack source = menu.getSlot(sourceSlot).getItem().copy();
        int before = menu.getSlot(targetSlot).getItem().getCount();
        InventoryUtils.pickup(menu, sourceSlot);
        if (move == source.getCount() && menu.getSlot(targetSlot).getItem().isEmpty()) {
            InventoryUtils.pickup(menu, targetSlot);
        } else {
            for (int i = 0; i < move; i++) InventoryUtils.dropOne(menu, targetSlot);
        }
        if (!menu.getCarried().isEmpty()) InventoryUtils.pickup(menu, sourceSlot);

        ItemStack after = menu.getSlot(targetSlot).getItem();
        return !after.isEmpty() && ItemStack.isSameItemSameComponents(after, source)
                ? Math.max(0, after.getCount() - before) : 0;
    }

    private int transferFullStack(AbstractContainerMenu menu, int slotIndex, ItemStack stack) {
        int space = InventoryUtils.calculatePlayerSpace(menu, stack);
        int toMove = Math.min(stack.getCount(), space);

        if (toMove > 0) {
            InventoryUtils.quickMove(menu, slotIndex);
            return toMove;
        }
        return 0;
    }

    private int transferPartialStack(AbstractContainerMenu menu, int slotIndex, int needed) {
        int targetSlot = InventoryUtils.findEmptyPlayerSlot(menu, menu.getSlot(slotIndex).getItem());
        return targetSlot != -1 ? moveItemsToSlot(menu, slotIndex, targetSlot, needed) : 0;
    }

    private void updateCache(String itemId, int amountTaken) {
        if (currentTargetEntry == null) return;

        String chestJsonKey = CacheUtils.posToString(currentTargetEntry.getKey());
        Map<String, Map<String, Integer>> globalCache = ChestCache.getGlobalBuffer();
        if (globalCache.containsKey(chestJsonKey)) {
            Map<String, Integer> chestContents = globalCache.get(chestJsonKey);

            if (chestContents != null && chestContents.containsKey(itemId)) {
                int currentAmount = chestContents.get(itemId);
                int newAmount = currentAmount - amountTaken;

                if (newAmount <= 0) {
                    chestContents.remove(itemId);
                } else {
                    chestContents.put(itemId, newAmount);
                }

                ChestCache.updateAndSync(currentTargetEntry.getKey(), chestContents);
            }
        }
    }

    private void closeSilent() {
        if (mc.player != null && silentContainerId != -1
                && mc.player.containerMenu.containerId == silentContainerId) {
            InventoryUtils.closeContainerSilent();
        }
        silentContainerId = -1;

        moveToNextTarget();
    }

    private void openReturnContainer() {
        if (returnToContainerPos == null) return;
        InventoryUtils.useItemOnSilently(returnToContainerPos);
    }

    private void sendMessage(String message) {
        com.duox.storagemanager.utils.ToastUtils.sendToast("§6StorageManager", message);
    }

}
