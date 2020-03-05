/*
 * WorldGuard, a suite of tools for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldGuard team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldguard.bukkit.listener;

import com.google.common.collect.Lists;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.BukkitWorldConfiguration;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.cause.Cause;
import com.sk89q.worldguard.bukkit.event.DelegateEvent;
import com.sk89q.worldguard.bukkit.event.block.BreakBlockEvent;
import com.sk89q.worldguard.bukkit.event.block.PlaceBlockEvent;
import com.sk89q.worldguard.bukkit.event.block.UseBlockEvent;
import com.sk89q.worldguard.bukkit.event.entity.DamageEntityEvent;
import com.sk89q.worldguard.bukkit.event.entity.DestroyEntityEvent;
import com.sk89q.worldguard.bukkit.event.entity.SpawnEntityEvent;
import com.sk89q.worldguard.bukkit.event.entity.UseEntityEvent;
import com.sk89q.worldguard.bukkit.event.inventory.UseItemEvent;
import com.sk89q.worldguard.bukkit.listener.debounce.BlockPistonExtendKey;
import com.sk89q.worldguard.bukkit.listener.debounce.BlockPistonRetractKey;
import com.sk89q.worldguard.bukkit.listener.debounce.EventDebounce;
import com.sk89q.worldguard.bukkit.listener.debounce.legacy.AbstractEventDebounce.Entry;
import com.sk89q.worldguard.bukkit.listener.debounce.legacy.BlockEntityEventDebounce;
import com.sk89q.worldguard.bukkit.listener.debounce.legacy.EntityEntityEventDebounce;
import com.sk89q.worldguard.bukkit.listener.debounce.legacy.InventoryMoveItemEventDebounce;
import com.sk89q.worldguard.bukkit.util.Blocks;
import com.sk89q.worldguard.bukkit.util.Entities;
import com.sk89q.worldguard.bukkit.util.Events;
import com.sk89q.worldguard.bukkit.util.Materials;
import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.protection.flags.Flags;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.entity.*;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.sk89q.worldguard.bukkit.cause.Cause.create;

public class EventAbstractionListener extends AbstractListener {

    private final BlockEntityEventDebounce interactDebounce = new BlockEntityEventDebounce(10000);
    private final EntityEntityEventDebounce pickupDebounce = new EntityEntityEventDebounce(10000);
    private final BlockEntityEventDebounce entityBreakBlockDebounce = new BlockEntityEventDebounce(10000);
    private final InventoryMoveItemEventDebounce moveItemDebounce = new InventoryMoveItemEventDebounce(30000);
    private final EventDebounce<BlockPistonRetractKey> pistonRetractDebounce = EventDebounce.create(5000);
    private final EventDebounce<BlockPistonExtendKey> pistonExtendDebounce = EventDebounce.create(5000);

    /**
     * Construct the listener.
     *
     * @param plugin an instance of WorldGuardPlugin
     */
    public EventAbstractionListener(final WorldGuardPlugin plugin) {
        super(plugin);
    }

    //-------------------------------------------------------------------------
    // Block break / place
    //-------------------------------------------------------------------------

    /**
     * Handle the right click of a block while an item is held.
     *
     * @param event  the original event
     * @param cause  the list of cause
     * @param item   the item
     * @param placed the placed block
     * @param <T>    the event type
     */
    private static <T extends Event & Cancellable> void handleBlockRightClick(final T event, final Cause cause, @Nullable final ItemStack item, final Block clicked, final Block placed) {
        if (item != null && item.getType() == Material.TNT) {
            // Workaround for a bug that allowed TNT to trigger instantly if placed
            // next to redstone, without plugins getting the clicked place event
            // (not sure if this actually still happens) -- note Jun 2019 - happens with dispensers still, tho not players
            Events.fireToCancel(event, new UseBlockEvent(event, cause, clicked.getLocation(), Material.TNT));

            // Workaround for http://leaky.bukkit.org/issues/1034
            Events.fireToCancel(event, new PlaceBlockEvent(event, cause, placed.getLocation(), Material.TNT));
            return;
        }

        // Handle created Minecarts
        if (item != null && Materials.isMinecart(item.getType())) {
            EntityType entityType = Materials.getRelatedEntity(item.getType());
            if (entityType == null) {
                entityType = EntityType.MINECART;
            }
            Events.fireToCancel(event, new SpawnEntityEvent(event, cause, placed.getLocation().add(0.5, 0, 0.5), entityType));
            return;
        }

        // Handle created boats
        if (item != null && Materials.isBoat(item.getType())) {
            Events.fireToCancel(event, new SpawnEntityEvent(event, cause, placed.getLocation().add(0.5, 0, 0.5), EntityType.BOAT));
            return;
        }

        // Handle created armor stands
        if (item != null && item.getType() == Material.ARMOR_STAND) {
            Events.fireToCancel(event, new SpawnEntityEvent(event, cause, placed.getLocation().add(0.5, 0, 0.5), EntityType.ARMOR_STAND));
            return;
        }

        if (item != null && item.getType() == Material.END_CRYSTAL) { /*&& placed.getType() == Material.BEDROCK) {*/ // in vanilla you can only place them on bedrock but who knows what plugins will add
            // may be overprotective as a result, but better than being underprotective
            Events.fireToCancel(event, new SpawnEntityEvent(event, cause, placed.getLocation().add(0.5, 0, 0.5), EntityType.ENDER_CRYSTAL));
            return;
        }

        // Handle created spawn eggs
        if (item != null && Materials.isSpawnEgg(item.getType())) {
            Events.fireToCancel(event, new SpawnEntityEvent(event, cause, placed.getLocation().add(0.5, 0, 0.5), Materials.getEntitySpawnEgg(item.getType())));
        }
    }

    private static <T extends Event & Cancellable> void handleInventoryHolderUse(final T originalEvent, final Cause cause, final InventoryHolder holder) {
        if (originalEvent.isCancelled()) {
            return;
        }

        if (holder instanceof Entity) {
            Events.fireToCancel(originalEvent, new UseEntityEvent(originalEvent, cause, (Entity) holder));
        }
        else if (holder instanceof BlockState) {
            Events.fireToCancel(originalEvent, new UseBlockEvent(originalEvent, cause, ((BlockState) holder).getBlock()));
        }
        else if (holder instanceof DoubleChest) {
            final InventoryHolder left = ((DoubleChest) holder).getLeftSide();
            final InventoryHolder right = ((DoubleChest) holder).getRightSide();
            if (left instanceof Chest) {
                Events.fireToCancel(originalEvent, new UseBlockEvent(originalEvent, cause, ((Chest) left).getBlock()));
            }
            if (right instanceof Chest) {
                Events.fireToCancel(originalEvent, new UseBlockEvent(originalEvent, cause, ((Chest) right).getBlock()));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        Events.fireToCancel(event, new BreakBlockEvent(event, create(event.getPlayer()), event.getBlock()));

        if (event.isCancelled()) {
            playDenyEffect(event.getPlayer(), event.getBlock().getLocation().add(0.5, 1, 0.5));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockMultiPlace(final BlockMultiPlaceEvent event) {
        final List<Block> blocks = new ArrayList<>();
        for (final BlockState bs : event.getReplacedBlockStates()) {
            blocks.add(bs.getBlock());
        }
        Events.fireToCancel(event, new PlaceBlockEvent(event, create(event.getPlayer()),
                                                       event.getBlock().getWorld(), blocks, event.getBlock().getType()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (event instanceof BlockMultiPlaceEvent) return;
        final BlockState previousState = event.getBlockReplacedState();

        // Some blocks, like tall grass and fire, get replaced
        if (previousState.getType() != Material.AIR) {
            Events.fireToCancel(event, new BreakBlockEvent(event, create(event.getPlayer()), previousState.getLocation(), previousState.getType()));
        }

        if (!event.isCancelled()) {
            final ItemStack itemStack = new ItemStack(event.getBlockPlaced().getType(), 1);
            Events.fireToCancel(event, new UseItemEvent(event, create(event.getPlayer()), event.getPlayer().getWorld(), itemStack));
        }

        if (!event.isCancelled()) {
            Events.fireToCancel(event, new PlaceBlockEvent(event, create(event.getPlayer()), event.getBlock()));
        }

        if (event.isCancelled()) {
            playDenyEffect(event.getPlayer(), event.getBlockPlaced().getLocation().add(0.5, 0.5, 0.5));
        }
    }

    // TODO: Handle EntityCreatePortalEvent?

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(final BlockBurnEvent event) {
        final Block target = event.getBlock();

        final Block[] adjacent = {
                target.getRelative(BlockFace.NORTH),
                target.getRelative(BlockFace.SOUTH),
                target.getRelative(BlockFace.WEST),
                target.getRelative(BlockFace.EAST),
                target.getRelative(BlockFace.UP),
                target.getRelative(BlockFace.DOWN)};

        int found = 0;
        boolean allowed = false;

        for (final Block source : adjacent) {
            if (source.getType() == Material.FIRE) {
                found++;
                if (Events.fireAndTestCancel(new BreakBlockEvent(event, create(source), target))) {
                    source.setType(Material.AIR);
                }
                else {
                    allowed = true;
                }
            }
        }

        if (found > 0 && !allowed) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStructureGrowEvent(final StructureGrowEvent event) {
        final int originalCount = event.getBlocks().size();
        final List<Block> blockList = Lists.transform(event.getBlocks(), new BlockStateAsBlockFunction());

        final Player player = event.getPlayer();
        if (player != null) {
            Events.fireBulkEventToCancel(event, new PlaceBlockEvent(event, create(player), event.getLocation().getWorld(), blockList, Material.AIR));
        }
        else {
            Events.fireBulkEventToCancel(event, new PlaceBlockEvent(event, create(event.getLocation().getBlock()), event.getLocation().getWorld(), blockList, Material.AIR));
        }

        if (!event.isCancelled() && event.getBlocks().size() != originalCount) {
            event.getLocation().getBlock().setType(Material.AIR);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(final EntityChangeBlockEvent event) {
        final Block block = event.getBlock();
        final Entity entity = event.getEntity();
        final Material to = event.getTo();

        // Forget about Redstone ore, especially since we handle it in INTERACT
        if (block.getType() == Material.REDSTONE_ORE && to == Material.REDSTONE_ORE) {
            return;
        }

        // Fire two events: one as BREAK and one as PLACE
        if (event.getTo() != Material.AIR && event.getBlock().getType() != Material.AIR) {
            Events.fireToCancel(event, new BreakBlockEvent(event, create(entity), block));
            Events.fireToCancel(event, new PlaceBlockEvent(event, create(entity), block.getLocation(), to));
        } else {
            if (event.getTo() == Material.AIR) {
                // Track the source so later we can create a proper chain of causes
                if (entity instanceof FallingBlock) {
                    Cause.trackParentCause(entity, block);

                    // Switch around the event
                    Events.fireToCancel(event, new SpawnEntityEvent(event, create(block), entity));
                } else {
                    entityBreakBlockDebounce.debounce(
                            event.getBlock(), event.getEntity(), event, new BreakBlockEvent(event, create(entity), event.getBlock()));
                }
            }
            else {
                final boolean wasCancelled = event.isCancelled();
                final Cause cause = create(entity);

                Events.fireToCancel(event, new PlaceBlockEvent(event, cause, event.getBlock().getLocation(), to));

                if (event.isCancelled() && !wasCancelled && entity instanceof FallingBlock) {
                    final FallingBlock fallingBlock = (FallingBlock) entity;
                    final ItemStack itemStack = new ItemStack(fallingBlock.getBlockData().getMaterial(), 1);
                    final Item item = block.getWorld().dropItem(fallingBlock.getLocation(), itemStack);
                    item.setVelocity(new Vector());
                    if (Events.fireAndTestCancel(new SpawnEntityEvent(event, create(block, entity), item))) {
                        item.remove();
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(final EntityExplodeEvent event) {
        final Entity entity = event.getEntity();
        if (event.getYield() == 0 && event.blockList().isEmpty()) {
            // avoids ender dragon spam
            return;
        }

        Events.fireBulkEventToCancel(event, new BreakBlockEvent(event, create(entity), event.getLocation().getWorld(), event.blockList(), Material.AIR));
    }

    //-------------------------------------------------------------------------
    // Block external interaction
    //-------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onBlockPistonRetract(final BlockPistonRetractEvent event) {
        if (event.isSticky()) {
            final EventDebounce.Entry entry = pistonRetractDebounce.getIfNotPresent(new BlockPistonRetractKey(event), event);
            if (entry != null) {
                final Block piston = event.getBlock();
                final Cause cause = create(piston);

                final BlockFace direction = event.getDirection();

                final ArrayList<Block> blocks = new ArrayList<>(event.getBlocks());
                final int originalSize = blocks.size();
                Events.fireBulkEventToCancel(event, new BreakBlockEvent(event, cause, event.getBlock().getWorld(), blocks, Material.AIR));
                if (originalSize != blocks.size()) {
                    event.setCancelled(true);
                }
                for (final Block b : blocks) {
                    final Location loc = b.getRelative(direction).getLocation();
                    Events.fireToCancel(event, new PlaceBlockEvent(event, cause, loc, b.getType()));
                }

                entry.setCancelled(event.isCancelled());

                if (event.isCancelled()) {
                    playDenyEffect(piston.getLocation().add(0.5, 1, 0.5));
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPistonExtend(final BlockPistonExtendEvent event) {
        final EventDebounce.Entry entry = pistonExtendDebounce.getIfNotPresent(new BlockPistonExtendKey(event), event);
        if (entry != null) {
            final List<Block> blocks = new ArrayList<>(event.getBlocks());
            final int originalLength = blocks.size();
            final BlockFace dir = event.getDirection();
            for (int i = 0; i < blocks.size(); i++) {
                final Block existing = blocks.get(i);
                if (existing.getPistonMoveReaction() == PistonMoveReaction.MOVE
                        || existing.getPistonMoveReaction() == PistonMoveReaction.PUSH_ONLY) {
                    blocks.set(i, existing.getRelative(dir));
                }
            }
            Events.fireBulkEventToCancel(event, new PlaceBlockEvent(event, create(event.getBlock()), event.getBlock().getWorld(), blocks, Material.STONE));
            if (blocks.size() != originalLength) {
                event.setCancelled(true);
            }
            entry.setCancelled(event.isCancelled());

            if (event.isCancelled()) {
                playDenyEffect(event.getBlock().getLocation().add(0.5, 1, 0.5));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(final BlockDamageEvent event) {
        final Block target = event.getBlock();

        // Previously, and perhaps still, the only way to catch cake eating
        // events was through here
        if (target.getType() == Material.CAKE) {
            Events.fireToCancel(event, new UseBlockEvent(event, create(event.getPlayer()), target));
        }
    }

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        @Nullable final ItemStack item = event.getItem();
        final Block clicked = event.getClickedBlock();
        Block placed;
        boolean silent = false;
        final boolean modifiesWorld;
        final Cause cause = create(player);

        switch (event.getAction()) {
            case PHYSICAL:
                if (event.useInteractedBlock() != Result.DENY) {
                    assert clicked != null;
                    final DelegateEvent firedEvent = new UseBlockEvent(event, cause, clicked).setAllowed(hasInteractBypass(clicked));
                    if (clicked.getType() == Material.REDSTONE_ORE) {
                        silent = true;
                    }
                    if (clicked.getType() == Material.FARMLAND || clicked.getType() == Material.TURTLE_EGG) {
                        silent = true;
                        firedEvent.getRelevantFlags().add(Flags.TRAMPLE_BLOCKS);
                    }
                    firedEvent.setSilent(silent);
                    interactDebounce.debounce(clicked, event.getPlayer(), event, firedEvent);
                }
                break;

            case RIGHT_CLICK_BLOCK:
                if (event.useInteractedBlock() != Result.DENY) {
                    assert clicked != null;
                    placed = clicked.getRelative(event.getBlockFace());

                    // Re-used for dispensers
                    handleBlockRightClick(event, create(event.getPlayer()), item, clicked, placed);
                }

            case LEFT_CLICK_BLOCK:
                if (event.useInteractedBlock() != Result.DENY) {
                    assert clicked != null;
                    placed = clicked.getRelative(event.getBlockFace());

                    // Only fire events for blocks that are modified when right clicked
                    final boolean hasItemInteraction = item != null && isItemAppliedToBlock(item, clicked)
                            && event.getAction() == Action.RIGHT_CLICK_BLOCK;
                    modifiesWorld = isBlockModifiedOnClick(clicked, event.getAction() == Action.RIGHT_CLICK_BLOCK)
                            || hasItemInteraction;

                    if (Events.fireAndTestCancel(new UseBlockEvent(event, cause, clicked).setAllowed(!modifiesWorld))) {
                        event.setUseInteractedBlock(Result.DENY);
                    }

                    // Handle connected blocks (i.e. beds, chests)
                    for (final Block connected : Blocks.getConnected(clicked)) {
                        if (Events.fireAndTestCancel(new UseBlockEvent(event, create(event.getPlayer()), connected).setAllowed(!modifiesWorld))) {
                            event.setUseInteractedBlock(Result.DENY);
                            break;
                        }
                    }

                    if (hasItemInteraction) {
                        if (Events.fireAndTestCancel(new PlaceBlockEvent(event, cause, clicked.getLocation(), clicked.getType()))) {
                            event.setUseItemInHand(Result.DENY);
                        }
                    }

                    // Special handling of putting out fires
                    if (event.getAction() == Action.LEFT_CLICK_BLOCK && placed.getType() == Material.FIRE) {
                        if (Events.fireAndTestCancel(new BreakBlockEvent(event, create(event.getPlayer()), placed))) {
                            event.setUseInteractedBlock(Result.DENY);
                            break;
                        }
                    }

                    if (event.isCancelled()) {
                        playDenyEffect(event.getPlayer(), clicked.getLocation().add(0.5, 1, 0.5));
                    }
                }

            case LEFT_CLICK_AIR:
            case RIGHT_CLICK_AIR:
                if (event.useItemInHand() != Result.DENY) {
                    if (item != null && !item.getType().isBlock() && Events.fireAndTestCancel(new UseItemEvent(event, cause, player.getWorld(), item))) {
                        event.setUseItemInHand(Result.DENY);
                    }
                }

                // Check for items that the administrator has configured to
                // emit a "use block here" event where the player is
                // standing, which is a hack to protect items that don't
                // throw events
                if (item != null && ((BukkitWorldConfiguration) getWorldConfig(BukkitAdapter.adapt(player.getWorld()))).blockUseAtFeet.test(item)) {
                    if (Events.fireAndTestCancel(new UseBlockEvent(event, cause, player.getLocation().getBlock()))) {
                        event.setUseInteractedBlock(Result.DENY);
                    }
                }

                break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityBlockForm(final EntityBlockFormEvent event) {
        entityBreakBlockDebounce.debounce(event.getBlock(), event.getEntity(), event,
                                          new PlaceBlockEvent(event, create(event.getEntity()),
                                                              event.getBlock().getLocation(), event.getNewState().getType()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(final EntityInteractEvent event) {
        interactDebounce.debounce(event.getBlock(), event.getEntity(), event,
                                  new UseBlockEvent(event, create(event.getEntity()),
                                                    event.getBlock()).setAllowed(hasInteractBypass(event.getBlock())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockIgnite(final BlockIgniteEvent event) {
        final Block block = event.getBlock();
        final Cause cause;

        // Find the cause
        if (event.getPlayer() != null) {
            cause = create(event.getPlayer());
        }
        else if (event.getIgnitingEntity() != null) {
            cause = create(event.getIgnitingEntity());
        }
        else if (event.getIgnitingBlock() != null) {
            cause = create(event.getIgnitingBlock());
        }
        else {
            cause = Cause.unknown();
        }

        Events.fireToCancel(event, new PlaceBlockEvent(event, cause, block.getLocation(), Material.FIRE));
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(final SignChangeEvent event) {
        Events.fireToCancel(event, new UseBlockEvent(event, create(event.getPlayer()), event.getBlock()));

        if (event.isCancelled()) {
            playDenyEffect(event.getPlayer(), event.getBlock().getLocation().add(0.5, 0.5, 0.5));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBedEnter(final PlayerBedEnterEvent event) {
        Events.fireToCancel(event, new UseBlockEvent(event, create(event.getPlayer()), event.getBed()));
    }

    // TODO: Handle EntityPortalEnterEvent

    //-------------------------------------------------------------------------
    // Block self-interaction
    //-------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onPlayerBucketEmpty(final PlayerBucketEmptyEvent event) {
        final Player player = event.getPlayer();
        final Block blockClicked = event.getBlockClicked();
        final Block blockAffected;

        if (blockClicked.getBlockData() instanceof Waterlogged) {
            blockAffected = blockClicked;
        }
        else {
            blockAffected = blockClicked.getRelative(event.getBlockFace());
        }

        boolean allowed = false;

        // Milk buckets can't be emptied as of writing
        if (event.getBucket() == Material.MILK_BUCKET) {
            allowed = true;
        }

        final ItemStack item = new ItemStack(event.getBucket(), 1);
        final Material blockMaterial = Materials.getBucketBlockMaterial(event.getBucket());
        Events.fireToCancel(event, new PlaceBlockEvent(event, create(player), blockAffected.getLocation(), blockMaterial).setAllowed(allowed));
        Events.fireToCancel(event, new UseItemEvent(event, create(player), player.getWorld(), item).setAllowed(allowed));

        if (event.isCancelled()) {
            playDenyEffect(event.getPlayer(), blockAffected.getLocation().add(0.5, 0.5, 0.5));
        }
    }

    //-------------------------------------------------------------------------
    // Entity break / place
    //-------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onPlayerBucketFill(final PlayerBucketFillEvent event) {
        final Player player = event.getPlayer();
        final Block blockAffected = event.getBlockClicked().getRelative(event.getBlockFace());
        boolean allowed = false;

        // Milk buckets can't be emptied as of writing
        if (event.getItemStack().getType() == Material.MILK_BUCKET) {
            allowed = true;
        }

        final ItemStack item = new ItemStack(event.getBucket(), 1);
        Events.fireToCancel(event, new BreakBlockEvent(event, create(player), blockAffected).setAllowed(allowed));
        Events.fireToCancel(event, new UseItemEvent(event, create(player), player.getWorld(), item).setAllowed(allowed));

        if (event.isCancelled()) {
            playDenyEffect(event.getPlayer(), blockAffected.getLocation().add(0.5, 0.5, 0.5));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(final BlockFromToEvent event) {
        final WorldConfiguration config = getWorldConfig(BukkitAdapter.adapt(event.getBlock().getWorld()));

        // This only applies to regions but nothing else cares about high
        // frequency events at the moment
        if (!config.useRegions || (!config.highFreqFlags && !config.checkLiquidFlow)) {
            return;
        }

        final Block from = event.getBlock();
        final Block to = event.getToBlock();
        final Material fromType = from.getType();
        final Material toType = to.getType();

        // Liquids pass this event when flowing to solid blocks
        if (toType.isSolid() && Materials.isLiquid(fromType)) {
            return;
        }

        // This significantly reduces the number of events without having
        // too much effect. Unfortunately it appears that even if this
        // check didn't exist, you can raise the level of some liquid
        // flow and the from/to data may not be correct.
        if ((Materials.isWater(fromType) && Materials.isWater(toType)) || (Materials.isLava(fromType) && Materials.isLava(toType))) {
            return;
        }

        final Cause cause = create(from);

        // Disable since it's probably not needed
        /*if (from.getType() != Material.AIR) {
            Events.fireToCancel(event, new BreakBlockEvent(event, cause, to));
        }*/

        Events.fireToCancel(event, new PlaceBlockEvent(event, cause, to.getLocation(), from.getType()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(final CreatureSpawnEvent event) {
        switch (event.getSpawnReason()) {
            case DISPENSE_EGG:
            case EGG:
            case SPAWNER_EGG:
                if (getWorldConfig(BukkitAdapter.adapt(event.getEntity().getWorld())).strictEntitySpawn) {
                    Events.fireToCancel(event, new SpawnEntityEvent(event, Cause.unknown(), event.getEntity()));
                }
                break;
            case NATURAL:
            case JOCKEY:
            case CHUNK_GEN:
            case SPAWNER:
            case LIGHTNING:
            case BUILD_SNOWMAN:
            case BUILD_IRONGOLEM:
            case BUILD_WITHER:
            case VILLAGE_DEFENSE:
            case VILLAGE_INVASION:
            case BREEDING:
            case SLIME_SPLIT:
            case REINFORCEMENTS:
            case NETHER_PORTAL:
            case INFECTION:
            case CURED:
            case OCELOT_BABY:
            case SILVERFISH_BLOCK:
            case MOUNT:
            case CUSTOM:
            case DEFAULT:
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(final HangingPlaceEvent event) {
        Events.fireToCancel(event, new SpawnEntityEvent(event, create(event.getPlayer()), event.getEntity()));

        if (event.isCancelled()) {
            final Block effectBlock = event.getBlock().getRelative(event.getBlockFace());
            playDenyEffect(event.getPlayer(), effectBlock.getLocation().add(0.5, 0.5, 0.5));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(final HangingBreakEvent event) {
        if (event instanceof HangingBreakByEntityEvent) {
            final Entity remover = ((HangingBreakByEntityEvent) event).getRemover();
            Events.fireToCancel(event, new DestroyEntityEvent(event, create(remover), event.getEntity()));

            if (event.isCancelled() && remover instanceof Player) {
                playDenyEffect((Player) remover, event.getEntity().getLocation());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleDestroy(final VehicleDestroyEvent event) {
        Events.fireToCancel(event, new DestroyEntityEvent(event, create(event.getAttacker()), event.getVehicle()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExp(final BlockExpEvent event) {
        if (event.getExpToDrop() > 0) { // Event is raised even where no XP is being dropped
            if (Events.fireAndTestCancel(new SpawnEntityEvent(event, create(event.getBlock()), event.getBlock().getLocation(), EntityType.EXPERIENCE_ORB))) {
                event.setExpToDrop(0);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFish(final PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            if (Events.fireAndTestCancel(new UseItemEvent(event, create(event.getPlayer(), event.getHook()),
                                                          event.getPlayer().getWorld(), event.getPlayer().getInventory().getItemInMainHand()))) {
                event.setCancelled(true);
            }
        }
        else if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            if (Events.fireAndTestCancel(new SpawnEntityEvent(event, create(event.getPlayer(), event.getHook()), event.getHook().getLocation(), EntityType.EXPERIENCE_ORB))) {
                event.setExpToDrop(0);
            }
        }
        else if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            final Entity caught = event.getCaught();
            if (caught == null) return;
            if (caught instanceof Item) {
                Events.fireToCancel(event, new DestroyEntityEvent(event, create(event.getPlayer(), event.getHook()), caught));
            }
            else if (Entities.isConsideredBuildingIfUsed(caught)) {
                Events.fireToCancel(event, new UseEntityEvent(event, create(event.getPlayer(), event.getHook()), caught));
            }
            else if (Entities.isNonHostile(caught) || caught instanceof Player) {
                Events.fireToCancel(event, new DamageEntityEvent(event, create(event.getPlayer(), event.getHook()), caught));
            }
        }
    }

    //-------------------------------------------------------------------------
    // Entity external interaction
    //-------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onExpBottle(final ExpBottleEvent event) {
        if (Events.fireAndTestCancel(new SpawnEntityEvent(event, create(event.getEntity()), event.getEntity().getLocation(), EntityType.EXPERIENCE_ORB))) {
            event.setExperience(0);

            // Give the player back his or her XP bottle
            final ProjectileSource shooter = event.getEntity().getShooter();
            if (shooter instanceof Player) {
                final Player player = (Player) shooter;
                if (player.getGameMode() != GameMode.CREATIVE) {
                    player.getInventory().addItem(new ItemStack(Material.EXPERIENCE_BOTTLE, 1));
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(final EntityDeathEvent event) {
        if (event.getDroppedExp() > 0) {
            if (Events.fireAndTestCancel(new SpawnEntityEvent(event, create(event.getEntity()), event.getEntity().getLocation(), EntityType.EXPERIENCE_ORB))) {
                event.setDroppedExp(0);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(final PlayerInteractEntityEvent event) {
        final Player player = event.getPlayer();
        final World world = player.getWorld();
        final ItemStack item = event.getHand() == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        final Entity entity = event.getRightClicked();

        Events.fireToCancel(event, new UseItemEvent(event, create(player), world, item));
        Events.fireToCancel(event, new UseEntityEvent(event, create(player), entity));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageEvent event) {
        if (event instanceof EntityDamageByBlockEvent) {
            @Nullable final Block attacker = ((EntityDamageByBlockEvent) event).getDamager();

            // The attacker should NOT be null, but sometimes it is
            // See WORLDGUARD-3350
            if (attacker != null) {
                Events.fireToCancel(event, new DamageEntityEvent(event, create(attacker), event.getEntity()));
            }

        }
        else if (event instanceof EntityDamageByEntityEvent) {
            final EntityDamageByEntityEvent entityEvent = (EntityDamageByEntityEvent) event;
            final Entity damager = entityEvent.getDamager();
            Events.fireToCancel(event, new DamageEntityEvent(event, create(damager), event.getEntity()));

            // Item use event with the item in hand
            // Older blacklist handler code used this, although it suffers from
            // race problems
            if (damager instanceof Player) {
                // this event doesn't tell us which hand the weapon was in
                final ItemStack item = ((Player) damager).getInventory().getItemInMainHand();

                if (item.getType() != Material.AIR) {
                    Events.fireToCancel(event, new UseItemEvent(event, create(damager), event.getEntity().getWorld(), item));
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityCombust(final EntityCombustEvent event) {
        if (event instanceof EntityCombustByBlockEvent) {
            // at the time of writing, spigot is throwing null for the event's combuster. this causes lots of issues downstream.
            // whenever (i mean if ever) it is fixed, use getCombuster again instead of the current block
            Events.fireToCancel(event, new DamageEntityEvent(event, create(event.getEntity().getLocation().getBlock()), event.getEntity()));
        }
        else if (event instanceof EntityCombustByEntityEvent) {
            Events.fireToCancel(event, new DamageEntityEvent(event, create(((EntityCombustByEntityEvent) event).getCombuster()), event.getEntity()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityUnleash(final EntityUnleashEvent event) {
        if (event instanceof PlayerUnleashEntityEvent) {
            final PlayerUnleashEntityEvent playerEvent = (PlayerUnleashEntityEvent) event;
            Events.fireToCancel(playerEvent, new UseEntityEvent(playerEvent, create(playerEvent.getPlayer()), event.getEntity()));
        }  // TODO: Raise anyway?
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTame(final EntityTameEvent event) {
        Events.fireToCancel(event, new UseEntityEvent(event, create(event.getOwner()), event.getEntity()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerShearEntity(final PlayerShearEntityEvent event) {
        Events.fireToCancel(event, new UseEntityEvent(event, create(event.getPlayer()), event.getEntity()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPickupItem(final PlayerPickupItemEvent event) {
        final Item item = event.getItem();
        pickupDebounce.debounce(event.getPlayer(), item, event, new DestroyEntityEvent(event, create(event.getPlayer()), event.getItem()));
    }

    //-------------------------------------------------------------------------
    // Composite events
    //-------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(final PlayerDropItemEvent event) {
        Events.fireToCancel(event, new SpawnEntityEvent(event, create(event.getPlayer()), event.getItemDrop()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleDamage(final VehicleDamageEvent event) {
        final Entity attacker = event.getAttacker();
        Events.fireToCancel(event, new DamageEntityEvent(event, create(attacker), event.getVehicle()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerItemConsume(final PlayerItemConsumeEvent event) {
        Events.fireToCancel(event, new UseItemEvent(event, create(event.getPlayer()), event.getPlayer().getWorld(), event.getItem()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(final InventoryOpenEvent event) {
        final InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BlockState) {
            Events.fireToCancel(event, new UseBlockEvent(event, create(event.getPlayer()), ((BlockState) holder).getBlock()));
        }
        else if (holder instanceof Entity) {
            if (!(holder instanceof Player)) {
                Events.fireToCancel(event, new UseEntityEvent(event, create(event.getPlayer()), (Entity) holder));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(final InventoryMoveItemEvent event) {
        final InventoryHolder causeHolder = event.getInitiator().getHolder();
        final InventoryHolder sourceHolder = event.getSource().getHolder();
        final InventoryHolder targetHolder = event.getDestination().getHolder();

        if ((causeHolder instanceof Hopper || causeHolder instanceof Dropper)
                && ((BukkitWorldConfiguration) WorldGuard.getInstance().getPlatform().getGlobalStateManager().get(
                BukkitAdapter.adapt(((Container) causeHolder).getWorld()))).ignoreHopperMoveEvents) {
            return;
        }

        final Entry entry;

        if ((entry = moveItemDebounce.tryDebounce(event)) != null) {
            final Cause cause;

            if (causeHolder instanceof Entity) {
                cause = create(causeHolder);
            } else if (causeHolder instanceof BlockState) {
                cause = create(((BlockState) causeHolder).getBlock());
            } else {
                cause = Cause.unknown();
            }

            if (causeHolder != null && !causeHolder.equals(sourceHolder)) {
                handleInventoryHolderUse(event, cause, sourceHolder);
            }

            handleInventoryHolderUse(event, cause, targetHolder);

            if (event.isCancelled() && causeHolder instanceof Hopper) {
                Bukkit.getScheduler().scheduleSyncDelayedTask(getPlugin(),
                        () -> ((Hopper) causeHolder).getBlock().breakNaturally());
            } else {
                entry.setCancelled(event.isCancelled());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionSplash(final PotionSplashEvent event) {
        final Entity entity = event.getEntity();
        final ThrownPotion potion = event.getPotion();
        final World world = entity.getWorld();
        final Cause cause = create(potion);

        // Fire item interaction event
        Events.fireToCancel(event, new UseItemEvent(event, cause, world, potion.getItem()));

        // Fire entity interaction event
        if (!event.isCancelled()) {
            int blocked = 0;
            final int affectedSize = event.getAffectedEntities().size();
            final boolean hasDamageEffect = Materials.hasDamageEffect(potion.getEffects());

            for (final LivingEntity affected : event.getAffectedEntities()) {
                final DelegateEvent delegate = hasDamageEffect
                        ? new DamageEntityEvent(event, cause, affected) :
                        new UseEntityEvent(event, cause, affected);

                // Consider the potion splash flag
                delegate.getRelevantFlags().add(Flags.POTION_SPLASH);

                if (Events.fireAndTestCancel(delegate)) {
                    event.setIntensity(affected, 0);
                    blocked++;
                }
            }

            if (blocked == affectedSize) { // server does weird things with this if the event is modified, so use cached number
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDispense(final BlockDispenseEvent event) {
        final Cause cause = create(event.getBlock());
        final Block dispenserBlock = event.getBlock();
        final ItemStack item = event.getItem();

        Events.fireToCancel(event, new UseItemEvent(event, cause, dispenserBlock.getWorld(), item));

        // Simulate right click event as players have it
        if (dispenserBlock.getBlockData() instanceof Dispenser) {
            final Dispenser dispenser = (Dispenser) dispenserBlock.getBlockData();
            final Block placed = dispenserBlock.getRelative(dispenser.getFacing());
            final Block clicked = placed.getRelative(dispenser.getFacing());
            handleBlockRightClick(event, cause, item, clicked, placed);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLingeringSplash(final LingeringPotionSplashEvent event) {
        final AreaEffectCloud aec = event.getAreaEffectCloud();
        final ThrownPotion potion = event.getEntity();
        final World world = potion.getWorld();
        final Cause cause = create(event.getEntity());

        // Fire item interaction event
        Events.fireToCancel(event, new UseItemEvent(event, cause, world, potion.getItem()));

        // Fire entity spawn event
        if (!event.isCancelled()) {
            // radius unfortunately doesn't go through with this, so only a single location is tested
            Events.fireToCancel(event, new SpawnEntityEvent(event, cause, aec.getLocation().add(0.5, 0, 0.5), EntityType.AREA_EFFECT_CLOUD));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLingeringApply(final AreaEffectCloudApplyEvent event) {
        if (!Materials.hasDamageEffect(event.getEntity().getCustomEffects())) {
            return;
        }
        final Cause cause = create(event.getEntity());
        event.getAffectedEntities()
                .removeIf(victim -> Events.fireAndTestCancel(new DamageEntityEvent(event, cause, victim)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractAtEntity(final PlayerInteractAtEntityEvent event) {
        onPlayerInteractEntity(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(final BlockExplodeEvent event) {
        final BreakBlockEvent eventToFire = new BreakBlockEvent(event, create(event.getBlock()),
                                                                event.getBlock().getLocation().getWorld(), event.blockList(), Material.AIR);
        eventToFire.getRelevantFlags().add(Flags.OTHER_EXPLOSION);
        Events.fireBulkEventToCancel(event, eventToFire);
    }

    private boolean hasInteractBypass(final Block block) {
        return ((BukkitWorldConfiguration) getWorldConfig(BukkitAdapter.adapt(block.getWorld()))).allowAllInteract.test(block);
    }

    private boolean hasInteractBypass(final World world, final ItemStack item) {
        return ((BukkitWorldConfiguration) getWorldConfig(BukkitAdapter.adapt(world))).allowAllInteract.test(item);
    }

    private boolean isBlockModifiedOnClick(final Block block, final boolean rightClick) {
        return Materials.isBlockModifiedOnClick(block.getType(), rightClick) && !hasInteractBypass(block);
    }

    private boolean isItemAppliedToBlock(final ItemStack item, final Block clicked) {
        return Materials.isItemAppliedToBlock(item.getType(), clicked.getType())
                && !hasInteractBypass(clicked)
                && !hasInteractBypass(clicked.getWorld(), item);
    }

    private void playDenyEffect(final Player player, final Location location) {
        //player.playSound(location, Sound.SUCCESSFUL_HIT, 0.2f, 0.4f);
        if (WorldGuard.getInstance().getPlatform().getGlobalStateManager().particleEffects) {
            player.playEffect(location, Effect.SMOKE, BlockFace.UP);
        }
    }

    private void playDenyEffect(final Location location) {
        if (WorldGuard.getInstance().getPlatform().getGlobalStateManager().particleEffects) {
            location.getWorld().playEffect(location, Effect.SMOKE, BlockFace.UP);
        }
    }

}
