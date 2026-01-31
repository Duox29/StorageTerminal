package com.duox.storagemanager.modules;

import com.duox.storagemanager.logic.ChestScoringEngine;
import com.duox.storagemanager.logic.StashPlanner;
import com.duox.storagemanager.system.Category;
import com.duox.storagemanager.system.Module;
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
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class AutoStash extends Module {
    // Settings
    private final NumberSetting range = new NumberSetting("Range", 5.0, 1.0, 10.0, 0.5);
    private final BooleanSetting includeHotbar = new BooleanSetting("Include Hotbar", false);
    private final NumberSetting itemsPerTick = new NumberSetting("Items/Tick", 4.0, 1.0, 27.0, 1.0);
    private final BooleanSetting rebuildCache = new BooleanSetting("Rebuild Cache Next Run", false);
    private final NumberSetting distanceWeight = new NumberSetting("Distance Weight", 0.2, 0.0, 1.0, 0.1);
    private final NumberSetting itemConcentrationWeight = new NumberSetting("Item Concentration Weight", 0.4, 0.0, 1.0, 0.1);
    private final NumberSetting spaceWeight = new NumberSetting("Space Weight", 0.4, 0.0, 1.0, 0.1);

    // Public API compatibility flags
    public static boolean cacheDirty = false; // Referenced by StorageScreen

    // State Machine
    private enum State {
        IDLE, SCANNING_WORLD, OPENING_FOR_SCAN, WAITING_FOR_SCAN_OPEN,
        SCANNING_CONTENTS, CLOSING_AFTER_SCAN, OPENING_FOR_STASH,
        WAITING_FOR_STASH_OPEN, STASHING_ITEMS, WAITING_FOR_UPDATE,
        CLOSING_AFTER_STASH
    }
    private State currentState = State.IDLE;

    // Runtime variables
    private BlockPos currentTarget = null;
    private int waitTimer = 0;
    private int silentContainerId = -1;
    private boolean containerReady = false;
    private List<BlockPos> scanQueue = new ArrayList<>();
    private Map<BlockPos, List<Integer>> stashQueue = new LinkedHashMap<>();
    private Iterator<Map.Entry<BlockPos, List<Integer>>> stashIterator;
    private Map.Entry<BlockPos, List<Integer>> currentStashEntry;
    private Set<BlockPos> processedChests = new HashSet<>();

    // Helpers
    private StashPlanner stashPlanner;

    public AutoStash() {
        super("AutoStash", "Scans chests to build a cache, then intelligently stashes items.", Category.UTILITY);
        this.getKeyMapping().setKey(InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_B));

        this.addSetting(range);
        this.addSetting(includeHotbar);
        this.addSetting(itemsPerTick);
        this.addSetting(rebuildCache);
        this.addSetting(distanceWeight);
        this.addSetting(itemConcentrationWeight);
        this.addSetting(spaceWeight);
    }

    // --- Public API (Used by StorageManager and StorageScreen) ---

    public static Map<String, Map<String, Integer>> getChestCache() {
        return ChestCache.getActiveCache();
    }

    public static Map<String, Map<String, Integer>> getGlobalBuffer() {
        return ChestCache.getGlobalBuffer();
    }
    public double getRange() {
        return range.getValue();
    }

    public static void updateGlobalAndSync(BlockPos pos, Map<String, Integer> newData) {
        ChestCache.updateAndSync(pos, newData, Minecraft.getInstance());
        cacheDirty = ChestCache.DIRTY_FLAG.get();
    }

    public static void clearCachesAndStorage(Minecraft mc) {
        ChestCache.clearAll(mc);
        cacheDirty = ChestCache.DIRTY_FLAG.get();
    }

    // Mixin hooks
    public static void setLastInteractedBlock(BlockPos pos) {
        ManualContainerTracker.setLastInteractedBlock(pos);
    }

    public static BlockPos getLastInteractedBlock() {
        return ManualContainerTracker.getLastInteractedBlock();
    }

    public static void tickManualCacheUpdate() {
        ManualContainerTracker.tickManualCacheUpdate();
        cacheDirty = ChestCache.DIRTY_FLAG.get();
    }

    // --- Module Lifecycle ---

    @Override
    public void onEnable() {
        if (mc.player == null || mc.level == null) return;

        resetState();
        initializePlanner();

        if (rebuildCache.getValue()) {
            startRebuildCache();
        } else {
            startSmartStash();
        }
    }

    @Override
    public void onDisable() {
        if (silentContainerId != -1 && mc.player != null) {
            sendClosePacket();
        }
        resetState();
    }

    private void resetState() {
        currentState = State.IDLE;
        currentTarget = null;
        silentContainerId = -1;
        containerReady = false;
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
        stashPlanner = new StashPlanner(engine, (msg, extra) -> sendMessage(msg + extra));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            this.setEnabled(false);
            return;
        }

        // Process manual interactions continuously
        ManualContainerTracker.tick(mc, (pos) -> {
            ChestCache.updateFromContainerMenu(pos, mc.player.containerMenu, mc);
            cacheDirty = ChestCache.DIRTY_FLAG.get();
        });

        // Sync dirty flag from cache manager
        if (ChestCache.DIRTY_FLAG.get()) {
            cacheDirty = true;
        }

        switch (currentState) {
            case SCANNING_WORLD -> processScanQueue();
            case OPENING_FOR_SCAN -> openTargetSilent(State.WAITING_FOR_SCAN_OPEN);
            case WAITING_FOR_SCAN_OPEN -> waitForContainer(State.SCANNING_CONTENTS);
            case SCANNING_CONTENTS -> scanContainerContents();
            case CLOSING_AFTER_SCAN -> closeSilent(State.SCANNING_WORLD);

            case OPENING_FOR_STASH -> openTargetSilent(State.WAITING_FOR_STASH_OPEN);
            case WAITING_FOR_STASH_OPEN -> waitForContainer(State.STASHING_ITEMS);
            case STASHING_ITEMS -> performStash();
            case WAITING_FOR_UPDATE -> waitForUpdate();
            case CLOSING_AFTER_STASH -> closeSilent(State.OPENING_FOR_STASH);
        }
    }

    // --- Cache Rebuild Logic ---

    private void startRebuildCache() {
        ChestCache.DIRTY_FLAG.set(true);
        scanQueue.clear();

        BlockPos playerPos = mc.player.blockPosition();
        int r = range.getInt();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    BlockEntity be = mc.level.getBlockEntity(pos);
                    if (isValidContainer(be) && !isDuplicateDoubleChest(be)) {
                        scanQueue.add(pos);
                    }
                }
            }
        }

        scanQueue.sort(Comparator.comparingDouble(pos -> pos.distSqr(playerPos)));
        currentState = State.SCANNING_WORLD;
    }

    private void processScanQueue() {
        if (scanQueue.isEmpty()) {
            rebuildCache.setValue(false);
            forceRefreshActiveCache(); // Refresh active cache after rebuild
            sendMessage("Cache rebuild complete. Saved to disk.");
            this.setEnabled(false);
            return;
        }
        currentTarget = scanQueue.remove(0);
        currentState = State.OPENING_FOR_SCAN;
    }

    private void scanContainerContents() {
        ChestCache.updateFromContainerMenu(currentTarget, mc.player.containerMenu, mc);
        currentState = State.CLOSING_AFTER_SCAN;
    }

    // --- Smart Stash Logic ---

    private void startSmartStash() {
        if (ChestCache.isCacheEmpty()) {
            ChestCache.loadFromDatabase(mc);
        }

        if (ChestCache.isCacheEmpty()) {
            sendMessage("§cCache is empty. Please run Rebuild Cache first.");
            this.setEnabled(false);
            return;
        }

        forceRefreshActiveCache(); // Ensure active cache is current
        calculateStashPlan();

        if (stashQueue.isEmpty()) {
            sendMessage("Nothing to stash.");
            this.setEnabled(false);
            return;
        }

        stashIterator = stashQueue.entrySet().iterator();
        moveToNextStashTarget();
    }

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
        }
    }

    private void performStash() {
        AbstractContainerMenu menu = mc.player.containerMenu;
        int containerSlots = menu.slots.size() - 36;

        if (containerSlots <= 0) {
            currentState = State.CLOSING_AFTER_STASH;
            return;
        }

        List<Integer> slotsToMove = currentStashEntry.getValue();
        int limit = itemsPerTick.getInt();
        int moves = 0;

        Iterator<Integer> it = slotsToMove.iterator();
        while (it.hasNext() && moves < limit) {
            int invSlotIndex = it.next();
            int menuSlotId = (invSlotIndex < 9) ? containerSlots + 27 + invSlotIndex
                    : containerSlots + (invSlotIndex - 9);

            if (menu.getSlot(menuSlotId).hasItem()) {
                sendQuickMovePacket(menu, menuSlotId);
                moves++;
            }
            it.remove();
        }

        if (slotsToMove.isEmpty()) {
            currentState = State.WAITING_FOR_UPDATE;
            waitTimer = 10; // Wait for server sync
        }
    }

    private void waitForUpdate() {
        waitTimer--;
        if (waitTimer <= 0) {
            // Rescan chest and mark processed
            ChestCache.updateFromContainerMenu(currentTarget, mc.player.containerMenu, mc);
            processedChests.add(currentTarget);

            // Dynamic re-planning
            if (hasItemsToStash()) {
                forceRefreshActiveCache();
                calculateStashPlan();

                if (!stashQueue.isEmpty()) {
                    stashIterator = stashQueue.entrySet().iterator();
                    currentState = State.CLOSING_AFTER_STASH;
                    return;
                }
            }

            currentState = State.CLOSING_AFTER_STASH;
        }
    }

    private boolean hasItemsToStash() {
        int start = includeHotbar.getValue() ? 0 : 9;
        for (int i = start; i < 36; i++) {
            if (!mc.player.getInventory().getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // --- Container Interaction Helpers ---

    private void openTargetSilent(State nextState) {
        if (currentTarget == null) return;

        Vec3 center = Vec3.atCenterOf(currentTarget);
        BlockHitResult hitResult = new BlockHitResult(center, Direction.UP, currentTarget, false);

        containerReady = false;
        silentContainerId = -1;
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        mc.player.swing(InteractionHand.MAIN_HAND);

        this.waitTimer = 20;
        this.currentState = nextState;
    }

    private void waitForContainer(State nextState) {
        if (containerReady && silentContainerId != -1) {
            currentState = nextState;
            return;
        }
        waitTimer--;
        if (waitTimer <= 0) {
            // Timeout handling
            currentState = (currentState == State.WAITING_FOR_SCAN_OPEN) ? State.SCANNING_WORLD
                    : State.CLOSING_AFTER_STASH;
        }
    }

    private void closeSilent(State nextState) {
        sendClosePacket();
        silentContainerId = -1;
        containerReady = false;

        if (nextState == State.OPENING_FOR_STASH) {
            moveToNextStashTarget();
        } else {
            currentState = nextState;
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

    public void forceRefreshActiveCache() {
        if (mc.player == null) return;

        StorageManager sm = com.duox.storagemanager.system.ModuleManager.INSTANCE.getModule(StorageManager.class);
        double scanRange = (sm != null) ? sm.scanRange.getValue() : 16.0;

        ChestCache.refreshActiveCache(mc.player.blockPosition(), scanRange);
        cacheDirty = ChestCache.DIRTY_FLAG.get();
    }

    public boolean isSilentMode() {
        return this.isEnabled();
    }

    // --- Utility Methods ---

    private boolean isValidContainer(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
    }

    private boolean isDuplicateDoubleChest(BlockEntity be) {
        if (be instanceof ChestBlockEntity) {
            var state = be.getBlockState();
            if (state.hasProperty(net.minecraft.world.level.block.ChestBlock.TYPE)) {
                return state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) ==
                        net.minecraft.world.level.block.state.properties.ChestType.LEFT;
            }
        }
        return false;
    }

    private void sendQuickMovePacket(AbstractContainerMenu menu, int slotId) {
        if (mc.gameMode != null) {
            mc.gameMode.handleInventoryMouseClick(menu.containerId, slotId, 0, ClickType.QUICK_MOVE, mc.player);
        }
    }

    private void sendClosePacket() {
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.connection.send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            mc.player.containerMenu = mc.player.inventoryMenu;
        }
    }

    private void sendMessage(String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§b[AutoStash] §r" + message), false);
        }
    }
}