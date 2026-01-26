package com.duox.storagemanager.modules;

import com.duox.storagemanager.gui.StorageScreen;
import com.duox.storagemanager.system.Category;
import com.duox.storagemanager.system.Module;
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

    private State currentState = State.IDLE;
    private BlockPos currentTarget = null;
    private BlockPos returnToContainerPos = null;
    private int waitTimer = 0;
    private int silentContainerId = -1;
    private boolean containerReady = false;

    public final NumberSetting panelX = new NumberSetting("Panel X", 300, 0, 2560, 1);
    public final NumberSetting panelY = new NumberSetting("Panel Y", 100, 0, 1440, 1);

    public StorageManager() {
        super("StorageManager", "Manage items from cached chests.", Category.UTILITY);
        this.getKeyMapping().setKey(
                com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_V));
        this.addSetting(panelX);
        this.addSetting(panelY);
    }

    @Override
    public void onEnable() {
        if (mc.player == null)
            return;

        // Load cache from file if not already loaded
        ensureCacheLoaded();

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
     */
    private void ensureCacheLoaded() {
        Map<String, Map<String, Integer>> cache = AutoStash.getChestCache();

        // If cache is empty, try to load from file
        if (cache.isEmpty()) {
            Path cacheFile = CacheUtils.getCacheFilePath(mc, "autostash");
            Type type = new TypeToken<Map<String, Map<String, Integer>>>() {
            }.getType();
            Map<String, Map<String, Integer>> loaded = CacheUtils.loadFromJson(cacheFile, type);

            if (loaded != null && !loaded.isEmpty()) {
                cache.putAll(loaded);
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
            currentState = State.IDLE;
            this.setEnabled(false);
            return;
        }
        // Clone request queue to track remaining needs
        Map<String, Integer> remainingNeeds = new HashMap<>(requestQueue);
        requestQueue.clear();
        BlockPos playerPos = mc.player.blockPosition();

        // Get all chests
        List<String> sortedChests = new ArrayList<>(cache.keySet());

        // CUSTOM SORT: Prioritize chests with LESS items (cleaning up junk) then
        // Distance
        sortedChests.sort((s1, s2) -> {
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
        });

        for (String chestPosStr : sortedChests) {
            Map<String, Integer> contents = cache.get(chestPosStr);
            BlockPos chestPos = CacheUtils.stringToPos(chestPosStr);

            for (Iterator<Map.Entry<String, Integer>> it = remainingNeeds.entrySet().iterator(); it.hasNext();) {
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
                            it.remove(); // Fulfilled
                        } else {
                            req.setValue(newNeeded);
                        }
                    }
                }
            }
        }

        if (!remainingNeeds.isEmpty()) {
            sendMessage("Warning: Cannot find all items. Missing: " + remainingNeeds);
        }

        if (retrievalPlan.isEmpty()) {
            sendMessage("Could not find any items to retrieve.");
            // Kiểm tra xem user có add thêm gì mới vào queue trong lúc tính toán không
            if (requestQueue.isEmpty()) {
                currentState = State.IDLE;
                this.setEnabled(false);
            } else {
                // Nếu có queue mới, tính toán lại ngay
                calculateRetrievalPlan();
            }
        }

        retrievalIterator = retrievalPlan.entrySet().iterator();
        moveToNextTarget();
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
                // Nếu có item mới trong hàng đợi, quay lại bước Lập Kế Hoạch ngay lập tức
                currentState = State.PLANNING;
            } else {
                // Nếu không còn gì để làm, mới tắt module
                if (returnToContainerPos != null) {
                    openReturnContainer();
                    returnToContainerPos = null;
                }
                currentState = State.IDLE;
                // this.setEnabled(false);
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
        waitTimer = 11;
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
        // This ensures the server acknowledges each transaction before we send the next
        if (waitTimer > 0) {
            waitTimer--;
            return;
        }

        AbstractContainerMenu menu = mc.player.containerMenu;
        int containerSlots = menu.slots.size() - 36; // inventory always last 36 slots

        if (containerSlots <= 0) {
            currentState = State.CLOSING_CHEST;
            return;
        }

        Map<String, Integer> itemsToTake = currentTargetEntry.getValue();
        boolean actionTaken = false;

        // Iterate through chest slots
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty())
                continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

            if (itemsToTake.containsKey(itemId)) {
                int needed = itemsToTake.get(itemId);
                if (needed <= 0)
                    continue;

                int inSlot = stack.getCount();
                int actualTaken = 0;
                boolean isPartial = false;

                if (inSlot <= needed) {
                    // Take whole stack using Quick Move (Shift + Click)
                    InventoryUtils.quickMove(menu, i);
                    actualTaken = inSlot;
                } else {
                    // Take PARTIAL stack
                    int targetSlot = InventoryUtils.findEmptyPlayerSlot(menu, containerSlots);
                    if (targetSlot != -1) {
                        isPartial = true;
                        // 1. Pickup source (Left Click) -> Hold stack
                        InventoryUtils.pickup(menu, i);

                        // 2. Drop 'needed' items into player slot (Right Click = Place 1)
                        for (int k = 0; k < needed; k++) {
                            InventoryUtils.dropOne(menu, targetSlot);
                        }

                        // 3. Return remainder to source (Left Click) -> Place back
                        InventoryUtils.pickup(menu, i);

                        actualTaken = needed;
                    } else {
                        // Inventory full, unable to take partial stack, try next item or abort
                        continue;
                    }
                }

                updateCache(itemId, actualTaken);
                itemsToTake.put(itemId, needed - actualTaken);

                // CRITICAL FIX: Only process ONE stack/action per tick cycle.
                // Breaking the loop allows the server time to process the inventory changes.
                // Partial takes are complex and need more time (4 ticks), Quick Moves are
                // faster (2 ticks).
                actionTaken = true;
                waitTimer = isPartial ? 0 : 0;
                break;
            }
        }

        // Only close if we scanned the whole chest and found nothing more to take
        if (!actionTaken) {
            currentState = State.CLOSING_CHEST;
        }
    }

    private void updateCache(String itemId, int amountTaken) {
        if (currentTarget == null)
            return;

        String chestJsonKey = CacheUtils.posToString(currentTarget);
        Map<String, Map<String, Integer>> globalCache = AutoStash.getChestCache();
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
                AutoStash.cacheDirty = true;
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
}
