package com.duox.storagemanager.modules;

import com.duox.storagemanager.gui.StorageScreen;
import com.duox.storagemanager.system.Category;
import com.duox.storagemanager.system.Module;
import com.duox.storagemanager.system.settings.BooleanSetting;
import com.duox.storagemanager.system.settings.NumberSetting;
import com.duox.storagemanager.utils.CacheUtils;
import com.duox.storagemanager.utils.InventoryUtils;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;

/**
 * StorageManager Module
 * 
 * Manages automated item retrieval from cached chests.
 * Uses a state machine to plan pathways, open chests silently,
 * withdraw required items, and return to the original container.
 */
public class StorageManager extends Module {
    // Request Queue: Item ID -> Quantity needed
    private final Map<String, Integer> requestQueue = new HashMap<>();

    // Execution Queue: Chest Pos -> List of Item IDs to take from it
    private final Map<BlockPos, Map<String, Integer>> retrievalPlan = new HashMap<>();
    private Iterator<Map.Entry<BlockPos, Map<String, Integer>>> retrievalIterator;
    private Map.Entry<BlockPos, Map<String, Integer>> currentTargetEntry;

    // State Machine
    private enum State {
        IDLE,
        PLANNING,
        OPENING_CHEST,
        WAITING_FOR_OPEN,
        WITHDRAWING,
        CLOSING_CHEST
    }

    private static final int CHEST_OPEN_TIMEOUT = 11;
    private static final int PARTIAL_MOVE_DELAY = 0;
    private static final int FULL_MOVE_DELAY = 0;
    private static final int PLAYER_INVENTORY_SIZE = 36;

    private State currentState = State.IDLE;
    private BlockPos currentTarget = null;
    private BlockPos returnToContainerPos = null;
    private int waitTimer = 0;
    private int silentContainerId = -1;
    private boolean containerReady = false;

    public final NumberSetting panelX = new NumberSetting("Panel X", 300, 0, 2560, 1);
    public final NumberSetting panelY = new NumberSetting("Panel Y", 100, 0, 1440, 1);
    public final BooleanSetting autoRequestRecipe = new BooleanSetting("Auto-Request Recipes", true);

    // Panel settings
    public final NumberSetting panelScale = new NumberSetting("Panel Scale", 1.0, 0.5, 2.0, 0.1);
    public final NumberSetting panelGridCols = new NumberSetting("Panel Columns", 8, 4, 16, 1);
    public final NumberSetting panelGridRows = new NumberSetting("Panel Rows", 7, 4, 16, 1);

    // Screen settings
    public final NumberSetting screenScale = new NumberSetting("Screen Scale", 1.0, 0.5, 2.0, 0.1);
    public final NumberSetting screenGridCols = new NumberSetting("Screen Columns", 9, 4, 16, 1);
    public final NumberSetting screenGridRows = new NumberSetting("Screen Rows", 9, 4, 16, 1);

    // Scan Range setting for filtering active cache
    public final NumberSetting scanRange = new NumberSetting("Scan Range", 16.0, 4.0, 64.0, 1.0);

    public StorageManager() {
        super("StorageManager", "Manage items from cached chests.", Category.UTILITY);
        this.getKeyMapping().setKey(
                com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_V));
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
    }

    @Override
    public void onEnable() {
        if (mc.player == null)
            return;

        if (mc.screen == null) {
            mc.setScreen(new StorageScreen(this));
        }

        // If we have pending requests, start processing them
        if (!requestQueue.isEmpty()) {
            startRetrieval();
        } else {
            // Otherwise just open the GUI
            mc.setScreen(new StorageScreen(this));
        }
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            this.setEnabled(false);
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
            case IDLE:
                break;
        }
    }

    // --- Public API for GUI ---

    public void addToRequestQueue(String itemId, int quantity) {
        requestQueue.put(itemId, requestQueue.getOrDefault(itemId, 0) + quantity);
    }

    public void clearRequestQueue() {
        requestQueue.clear();
    }

    public Map<String, Integer> getRequestQueue() {
        return requestQueue;
    }

    public boolean hasItemInCache(String itemId) {
        Map<String, Map<String, Integer>> cache = AutoStash.getChestCache();
        if (cache == null)
            return false;

        for (Map<String, Integer> content : cache.values()) {
            if (content != null && content.containsKey(itemId) && content.get(itemId) > 0) {
                return true;
            }
        }
        return false;
    }

    public void startRetrieval() {
        if (requestQueue.isEmpty()) {
            sendMessage("Queue is empty.");
            return;
        }

        // Check for open crafting table and save its position
        captureCraftingTableContext();

        currentState = State.PLANNING;
    }

    private void captureCraftingTableContext() {
        if (mc.screen instanceof CraftingScreen) {
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

    // --- Internal Logic ---

    /**
     * Load cache from file if not already loaded
     * PUBLIC so it can be called from StorageScreen constructor
     */
    public void ensureCacheLoaded() {
        Map<String, Map<String, Integer>> globalBuffer = AutoStash.getGlobalBuffer();

        // If globalBuffer is empty, try to load from file
        if (globalBuffer.isEmpty()) {
            Path cacheFile = CacheUtils.getCacheFilePath(mc, "autostash");
            Type type = new TypeToken<Map<String, Map<String, Integer>>>() {
            }.getType();
            Map<String, Map<String, Integer>> loaded = CacheUtils.loadFromJson(cacheFile, type);

            if (loaded != null && !loaded.isEmpty()) {
                // Load into globalBuffer
                globalBuffer.putAll(loaded);
                // Refresh activeCache based on current position and scan range
                if (mc.player != null) {
                    AutoStash autoStash = com.duox.storagemanager.system.ModuleManager.INSTANCE
                            .getModule(AutoStash.class);
                    if (autoStash != null) {
                        autoStash.forceRefreshActiveCache();
                    }
                }

                AutoStash.cacheDirty = true;
                sendMessage("§aLoaded cache from file (" + loaded.size() + " chests).");
            } else {
                sendMessage("§eCache is empty. You may need to run AutoStash to rebuild the cache first.");
            }
        }
    }

    private void resetState() {
        currentState = State.IDLE;
        currentTarget = null;
        returnToContainerPos = null;
        silentContainerId = -1;
        containerReady = false;
        retrievalPlan.clear();
        retrievalIterator = null;
        currentTargetEntry = null;
    }

    private void calculateRetrievalPlan() {
        retrievalPlan.clear();
        Map<String, Map<String, Integer>> cache = AutoStash.getChestCache();

        if (requestQueue.isEmpty()) {
            finishWhenQueueEmpty();
            return;
        }

        Map<String, Integer> remainingNeeds = drainRequestQueue();
        List<String> sortedChests = sortChestsByRelevance(cache, remainingNeeds, mc.player.blockPosition());

        for (String chestPosStr : sortedChests) {
            Map<String, Integer> contents = cache.get(chestPosStr);
            if (contents == null || contents.isEmpty()) {
                continue;
            }

            BlockPos chestPos = CacheUtils.stringToPos(chestPosStr);
            processChestContents(contents, chestPos, remainingNeeds);
        }

        finalizePlanning(remainingNeeds);
    }

    private Map<String, Integer> drainRequestQueue() {
        Map<String, Integer> remainingNeeds = new HashMap<>(requestQueue);
        requestQueue.clear();
        return remainingNeeds;
    }

    private List<String> sortChestsByRelevance(Map<String, Map<String, Integer>> cache,
            Map<String, Integer> remainingNeeds, BlockPos playerPos) {
        List<String> sortedChests = new ArrayList<>(cache.keySet());
        sortedChests.sort(new ChestComparator(cache, remainingNeeds, playerPos));
        return sortedChests;
    }

    private void finalizePlanning(Map<String, Integer> remainingNeeds) {
        if (!remainingNeeds.isEmpty()) {
            sendMessage("Warning: Cannot find all items. Missing: " + remainingNeeds);
        }

        if (retrievalPlan.isEmpty()) {
            handleEmptyPlan();
            return;
        }

        retrievalIterator = retrievalPlan.entrySet().iterator();
        moveToNextTarget();
    }

    private void finishWhenQueueEmpty() {
        currentState = State.IDLE;
        this.setEnabled(false);
    }

    private void processChestContents(Map<String, Integer> contents, BlockPos chestPos,
            Map<String, Integer> remainingNeeds) {
        Iterator<Map.Entry<String, Integer>> it = remainingNeeds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Integer> req = it.next();
            String itemId = req.getKey();
            int needed = req.getValue();

            if (contents.containsKey(itemId)) {
                int available = contents.get(itemId);
                int toTake = Math.min(needed, available);

                if (toTake > 0) {
                    retrievalPlan.computeIfAbsent(chestPos, k -> new HashMap<>()).put(itemId, toTake);
                    int newNeeded = needed - toTake;
                    if (newNeeded <= 0) {
                        it.remove();
                    } else {
                        req.setValue(newNeeded);
                    }
                }
            }
        }
    }

    private void handleEmptyPlan() {
        sendMessage("Could not find any items to retrieve.");
        if (requestQueue.isEmpty()) {
            currentState = State.IDLE;
            this.setEnabled(false);
        } else {
            calculateRetrievalPlan();
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
            currentTarget = currentTargetEntry.getKey();
            currentState = State.OPENING_CHEST;
        } else {
            sendMessage("Retrieval complete.");
            if (!requestQueue.isEmpty()) {
                // Restart planning if new requests exist
                currentState = State.PLANNING;
            } else {
                // Finish if queue is empty
                if (returnToContainerPos != null) {
                    openReturnContainer();
                    returnToContainerPos = null;
                }
                currentState = State.IDLE;
            }
        }
    }

    private void openTargetSilent() {
        if (currentTarget == null)
            return;

        Vec3 center = Vec3.atCenterOf(currentTarget);
        BlockHitResult hitResult = new BlockHitResult(center, Direction.UP, currentTarget, false);

        containerReady = false;
        silentContainerId = -1;
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        mc.player.swing(InteractionHand.MAIN_HAND);

        currentState = State.WAITING_FOR_OPEN;
        waitTimer = CHEST_OPEN_TIMEOUT;
    }

    private void waitForContainer() {
        if (containerReady && silentContainerId != -1) {
            currentState = State.WITHDRAWING;
            return;
        }
        waitTimer--;
        if (waitTimer <= 0) {
            sendMessage("Timeout opening chest at " + currentTarget);
            currentState = State.CLOSING_CHEST;
        }
    }

    public void onSilentContainerOpen(int containerId, MenuType<?> menuType) {
        this.silentContainerId = containerId;
        this.containerReady = true;
    }

    public boolean isSilentMode() {
        return this.isEnabled() && (currentState != State.IDLE && currentState != State.PLANNING);
    }

    private void performWithdrawal() {
        // Add artificial delay to prevent packet spam and desync
        if (waitTimer > 0) {
            waitTimer--;
            return;
        }

        AbstractContainerMenu menu = mc.player.containerMenu;
        int containerSlots = menu.slots.size() - PLAYER_INVENTORY_SIZE; // inventory always last 36 slots

        if (containerSlots <= 0) {
            currentState = State.CLOSING_CHEST;
            return;
        }

        Map<String, Integer> itemsToTake = currentTargetEntry.getValue();

        if (!processContainerSlots(menu, itemsToTake, containerSlots)) {
            currentState = State.CLOSING_CHEST;
        }
    }

    private boolean processContainerSlots(AbstractContainerMenu menu, Map<String, Integer> itemsToTake,
            int containerSlots) {
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!itemsToTake.containsKey(itemId)) {
                continue;
            }

            int needed = itemsToTake.get(itemId);
            if (needed <= 0) {
                continue;
            }

            if (tryWithdrawItem(menu, i, stack, itemId, needed, containerSlots)) {
                return true;
            }
        }

        return false;
    }

    private boolean tryWithdrawItem(AbstractContainerMenu menu, int slotIndex, ItemStack stack, String itemId,
            int needed, int containerSlots) {
        int inSlot = stack.getCount();
        int actualTaken = 0;
        boolean isPartial = false;

        if (inSlot <= needed) {
            actualTaken = transferFullStack(menu, slotIndex, stack);
        } else {
            actualTaken = transferPartialStack(menu, slotIndex, needed, containerSlots);
            if (actualTaken > 0)
                isPartial = true;
        }

        if (actualTaken > 0) {
            updateCache(itemId, actualTaken);
            currentTargetEntry.getValue().put(itemId, needed - actualTaken);
            waitTimer = isPartial ? PARTIAL_MOVE_DELAY : FULL_MOVE_DELAY;
            return true;
        }
        return false;
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

    private int transferPartialStack(AbstractContainerMenu menu, int slotIndex, int needed, int containerSlots) {
        int targetSlot = InventoryUtils.findEmptyPlayerSlot(menu, containerSlots);
        if (targetSlot != -1) {
            InventoryUtils.pickup(menu, slotIndex);
            for (int k = 0; k < needed; k++) {
                InventoryUtils.dropOne(menu, targetSlot);
            }
            InventoryUtils.pickup(menu, slotIndex);
            return needed;
        }
        return 0;
    }

    private void updateCache(String itemId, int amountTaken) {
        if (currentTarget == null)
            return;

        String chestJsonKey = CacheUtils.posToString(currentTarget);
        Map<String, Map<String, Integer>> globalCache = AutoStash.getGlobalBuffer();
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

                // Use the new updateGlobalAndSync to sync both layers and file
                AutoStash.updateGlobalAndSync(currentTarget, chestContents);
            }
        }
    }

    private void closeSilent() {
        InventoryUtils.closeContainerSilent();
        silentContainerId = -1;
        containerReady = false;

        moveToNextTarget();
    }

    private void openReturnContainer() {
        if (returnToContainerPos == null)
            return;

        Vec3 center = Vec3.atCenterOf(returnToContainerPos);
        BlockHitResult hitResult = new BlockHitResult(center, Direction.UP, returnToContainerPos, false);

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void sendMessage(String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§6[StorageManager] §r" + message), false);
        }
    }

    private class ChestComparator implements Comparator<String> {
        private final Map<String, Map<String, Integer>> cache;
        private final Map<String, Integer> remainingNeeds;
        private final BlockPos playerPos;

        public ChestComparator(Map<String, Map<String, Integer>> cache, Map<String, Integer> remainingNeeds,
                BlockPos playerPos) {
            this.cache = cache;
            this.remainingNeeds = remainingNeeds;
            this.playerPos = playerPos;
        }

        @Override
        public int compare(String s1, String s2) {
            Map<String, Integer> c1Contents = cache.get(s1);
            Map<String, Integer> c2Contents = cache.get(s2);

            // Calculate "Relevance Score" -> The quantity of the needed item in the chest.
            // We want the chest where the needed item count is SMALLEST (but > 0).
            int score1 = getMinRelevantQuantity(c1Contents, remainingNeeds);
            int score2 = getMinRelevantQuantity(c2Contents, remainingNeeds);

            // If one chest doesn't have what we need, push it to end (MAX_VALUE)
            if (score1 != score2) {
                return Integer.compare(score1, score2);
            }

            // Tie-break with distance
            BlockPos p1 = CacheUtils.stringToPos(s1);
            BlockPos p2 = CacheUtils.stringToPos(s2);
            return Double.compare(p1.distSqr(playerPos), p2.distSqr(playerPos));
        }
    }
}
