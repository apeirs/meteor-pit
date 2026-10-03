package dev.goldenhead.modules;

import dev.goldenhead.MeteorPitAddon;
import dev.goldenhead.mixin.TextDisplayAccessor;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.*;

/**
 * Care Package event: finds the package chest (a chest that appears mid-game, or one under a "care package"
 * hologram), gets there and spam-clicks it. Every right click counts toward the 200 clicks and the first one
 * after that opens it. The moment the contents arrive, every item is shift-clicked into your inventory
 * (most valuable first) with direct slot clicks, no cursor movement, then the chest is closed.
 */
public class CarePackage extends ClickTarget {
    private static final Set<Block> CHESTS = Set.of(Blocks.CHEST, Blocks.TRAPPED_CHEST);

    private final SettingGroup sgLoot = settings.createGroup("Loot");

    private final Setting<String> hologram = sgLoot.add(new StringSetting.Builder()
        .name("hologram-keyword")
        .description("A chest under a hologram containing this counts as the package, even if it was there before. Empty = off.")
        .defaultValue("care package")
        .build()
    );

    private final Setting<String> titleKeyword = sgLoot.add(new StringSetting.Builder()
        .name("title-keyword")
        .description("Also loot any chest screen whose title contains this. Empty = only the chest you were just clicking.")
        .defaultValue("care package")
        .build()
    );

    private final Setting<List<String>> priority = sgLoot.add(new StringListSetting.Builder()
        .name("priority")
        .description("Take items whose name or id contains these first, in this order. Everything else after.")
        .defaultValue(List.of("mystic", "fresh", "diamond"))
        .build()
    );

    private final Setting<Integer> lootDelay = sgLoot.add(new IntSetting.Builder()
        .name("loot-delay-ms")
        .description("Time between shift-clicks. 0 = take everything in the same instant.")
        .defaultValue(0)
        .range(0, 500)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Boolean> autoClose = sgLoot.add(new BoolSetting.Builder()
        .name("auto-close")
        .description("Close the chest once nothing more can be taken.")
        .defaultValue(true)
        .build()
    );

    private final Set<BlockPos> fresh = new HashSet<>();
    private final Set<BlockPos> done = new HashSet<>();
    private final Set<Integer> stuck = new HashSet<>();

    private BlockPos lastClicked;
    private long lastClickTime;
    private int lootSyncId = -1;
    private BlockPos lootChest;
    private long openedAt;
    private long nextLoot;
    private boolean tookSomething;

    public CarePackage() {
        super(MeteorPitAddon.PIT, "care-package", "Gets to the care package, spam-clicks it open and instantly loots it, best items first.");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        lootSyncId = -1;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        fresh.clear();
        done.clear();
        lootSyncId = -1;
    }

    // A chest placed while we watch (the package landing) rather than one loaded with the map.
    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        boolean isChest = event.newState.getBlock() instanceof ChestBlock;
        boolean wasChest = event.oldState != null && event.oldState.getBlock() instanceof ChestBlock;
        if (isChest && !wasChest) fresh.add(event.pos.toImmutable());
        else if (!isChest) {
            fresh.remove(event.pos);
            done.remove(event.pos);
        }
    }

    @Override
    protected Set<Block> candidateBlocks() {
        return CHESTS;
    }

    @Override
    protected boolean isTargetBlock(BlockPos pos) {
        return !done.contains(pos) && (fresh.contains(pos) || underHologram(pos));
    }

    private boolean underHologram(BlockPos pos) {
        String key = hologram.get().toLowerCase(Locale.ROOT).trim();
        if (key.isEmpty()) return false;

        Box above = new Box(pos).expand(1.5, 0, 1.5).stretch(0, 4, 0);
        for (Entity entity : mc.world.getOtherEntities(null, above)) {
            if (entity instanceof PlayerEntity) continue;
            Text label = entity instanceof DisplayEntity.TextDisplayEntity display ? ((TextDisplayAccessor) display).goldenhead$getText() : entity.getCustomName();
            if (label != null && Beast.clean(label).contains(key)) return true;
        }
        return false;
    }

    @Override
    protected void onClicked() {
        lastClicked = targetBlock;
        lastClickTime = System.currentTimeMillis();
    }

    // Contents land with this packet; loot in the same instant, before anyone else's clicks arrive.
    @EventHandler
    private void onInventory(InventoryEvent event) {
        loot();
    }

    @EventHandler
    private void onTickLoot(TickEvent.Post event) {
        loot();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (lootDelay.get() > 0) loot();
    }

    private void loot() {
        if (mc.player == null || !(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) return;

        if (handler.syncId != lootSyncId) {
            if (!isPackageScreen()) return;
            lootSyncId = handler.syncId;
            lootChest = lastClicked;
            openedAt = System.currentTimeMillis();
            tookSomething = false;
            stuck.clear();
        }

        List<Integer> slots = new ArrayList<>();
        int containerSize = handler.getRows() * 9;
        for (int i = 0; i < containerSize; i++) {
            if (!stuck.contains(i) && !handler.getSlot(i).getStack().isEmpty()) slots.add(i);
        }
        slots.sort(Comparator.comparingInt(i -> rank(handler.getSlot(i).getStack())));

        if (slots.isEmpty()) {
            // Nothing (more) we can take. If we never saw an item, give the contents a moment to arrive first.
            if (tookSomething || System.currentTimeMillis() - openedAt > 500) finish();
            return;
        }

        long now = System.currentTimeMillis();
        for (int slot : slots) {
            if (lootDelay.get() > 0 && now < nextLoot) return;

            mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
            tookSomething = true;
            // Still there after the (client-predicted) shift-click = no room for it; don't retry it.
            if (!handler.getSlot(slot).getStack().isEmpty()) stuck.add(slot);

            if (lootDelay.get() > 0) {
                nextLoot = now + lootDelay.get();
                return;
            }
        }
    }

    private void finish() {
        if (lootChest != null) done.add(lootChest);
        if (autoClose.get() && mc.player.currentScreenHandler.syncId == lootSyncId) mc.player.closeHandledScreen();
        tookSomething = false;
        lootSyncId = -1;
    }

    // The chest we were spam-clicking just opened, or the title says it is the package.
    private boolean isPackageScreen() {
        if (lastClicked != null && System.currentTimeMillis() - lastClickTime < 1500) return true;
        String key = titleKeyword.get().toLowerCase(Locale.ROOT).trim();
        return !key.isEmpty() && mc.currentScreen instanceof HandledScreen<?> screen && Beast.clean(screen.getTitle()).contains(key);
    }

    private int rank(ItemStack stack) {
        String name = Beast.clean(stack.getName());
        String id = Registries.ITEM.getId(stack.getItem()).getPath();
        List<String> keys = priority.get();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i).toLowerCase(Locale.ROOT).trim();
            if (!key.isEmpty() && (name.contains(key) || id.contains(key))) return i;
        }
        return keys.size();
    }

    @Override
    public String getInfoString() {
        return lootSyncId != -1 ? "looting" : super.getInfoString();
    }
}
