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

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.BukkitWorldConfiguration;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.util.Materials;
import com.sk89q.worldguard.config.ConfigurationManager;
import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.util.SpongeUtil;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The listener for block events.
 *
 * @author sk89q
 */
public class WorldGuardBlockListener implements Listener {

    private final WorldGuardPlugin plugin;

    /**
     * Construct the object.
     *
     * @param plugin The plugin instance
     */
    public WorldGuardBlockListener(final WorldGuardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Register events.
     */
    public void registerEvents() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Get the world configuration given a world.
     *
     * @param world The world to get the configuration for.
     *
     * @return The configuration for {@code world}
     */
    private WorldConfiguration getWorldConfig(final World world) {
        return WorldGuard.getInstance().getPlatform().getGlobalStateManager().get(BukkitAdapter.adapt(world));
    }

    /**
     * Get the world configuration given a player.
     *
     * @param player The player to get the wold from
     *
     * @return The {@link BukkitWorldConfiguration} for the player's world
     */
    private WorldConfiguration getWorldConfig(final Player player) {
        return getWorldConfig(player.getWorld());
    }

    /*
     * Called when a block is broken.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        final WorldConfiguration wcfg = getWorldConfig(player);

        if (!wcfg.itemDurability) {
            final ItemStack held = player.getInventory().getItemInMainHand();
            final ItemMeta meta = held.getItemMeta();
            if (meta != null) {
                ((Damageable) meta).setDamage(0);
                held.setItemMeta(meta);
                player.getInventory().setItemInMainHand(held);
            }
        }
    }

    /*
     * Called when fluids flow.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(final BlockFromToEvent event) {
        final World world = event.getBlock().getWorld();
        final Block blockFrom = event.getBlock();
        final Block blockTo = event.getToBlock();

        final boolean isWater = blockFrom.getType() == Material.WATER;
        final boolean isLava = blockFrom.getType() == Material.LAVA;
        final boolean isAir = blockFrom.getType() == Material.AIR;

        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final WorldConfiguration wcfg = getWorldConfig(event.getBlock().getWorld());

        if (cfg.activityHaltToggle) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.simulateSponge && isWater) {
            final int ox = blockTo.getX();
            final int oy = blockTo.getY();
            final int oz = blockTo.getZ();

            for (int cx = -wcfg.spongeRadius; cx <= wcfg.spongeRadius; cx++) {
                for (int cy = -wcfg.spongeRadius; cy <= wcfg.spongeRadius; cy++) {
                    for (int cz = -wcfg.spongeRadius; cz <= wcfg.spongeRadius; cz++) {
                        final Block sponge = world.getBlockAt(ox + cx, oy + cy, oz + cz);
                        if (sponge.getType() == Material.SPONGE
                                && (!wcfg.redstoneSponges || !sponge.isBlockIndirectlyPowered())) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }

        /*if (plugin.classicWater && isWater) {
        int blockBelow = blockFrom.getRelative(0, -1, 0).getTypeId();
        if (blockBelow != 0 && blockBelow != 8 && blockBelow != 9) {
        blockFrom.setTypeId(9);
        if (blockTo.getTypeId() == 0) {
        blockTo.setTypeId(9);
        }
        return;
        }
        }*/

        // Check the fluid block (from) whether it is air.
        // If so and the target block is protected, cancel the event
        if (!wcfg.preventWaterDamage.isEmpty()) {
            final Material targetId = blockTo.getType();

            if ((isAir || isWater) &&
                    wcfg.preventWaterDamage.contains(BukkitAdapter.asBlockType(targetId).getId())) {
                event.setCancelled(true);
                return;
            }
        }

        if (!wcfg.allowedLavaSpreadOver.isEmpty() && isLava) {
            final Material targetId = blockTo.getRelative(0, -1, 0).getType();

            if (!wcfg.allowedLavaSpreadOver.contains(BukkitAdapter.asBlockType(targetId).getId())) {
                event.setCancelled(true);
                return;
            }
        }

        if (wcfg.highFreqFlags && isWater
                && WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().queryState(BukkitAdapter.adapt(blockFrom.getLocation()), (RegionAssociable) null, Flags.WATER_FLOW) == StateFlag.State.DENY) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.highFreqFlags && isLava
                && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().queryState(BukkitAdapter.adapt(blockFrom.getLocation()), (RegionAssociable) null, Flags.LAVA_FLOW))) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.disableObsidianGenerators && (isAir || isLava)
                && (blockTo.getType() == Material.REDSTONE_WIRE
                    || blockTo.getType() == Material.TRIPWIRE)) {
            blockTo.setType(Material.AIR);
        }
    }

    /*
     * Called when a block gets ignited.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(final BlockIgniteEvent event) {
        final IgniteCause cause = event.getCause();
        final Block block = event.getBlock();
        final World world = block.getWorld();

        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final WorldConfiguration wcfg = getWorldConfig(world);

        if (cfg.activityHaltToggle) {
            event.setCancelled(true);
            return;
        }
        final boolean isFireSpread = cause == IgniteCause.SPREAD;

        if (wcfg.preventLightningFire && cause == IgniteCause.LIGHTNING) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.preventLavaFire && cause == IgniteCause.LAVA) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.disableFireSpread && isFireSpread) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.blockLighter && (cause == IgniteCause.FLINT_AND_STEEL || cause == IgniteCause.FIREBALL)
                && event.getPlayer() != null
                && !plugin.hasPermission(event.getPlayer(), "worldguard.override.lighter")) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.fireSpreadDisableToggle && isFireSpread) {
            event.setCancelled(true);
            return;
        }

        if (!wcfg.disableFireSpreadBlocks.isEmpty() && isFireSpread) {
            final int x = block.getX();
            final int y = block.getY();
            final int z = block.getZ();

            if (wcfg.disableFireSpreadBlocks.contains(BukkitAdapter.asBlockType(world.getBlockAt(x, y - 1, z).getType()).getId())
                    || wcfg.disableFireSpreadBlocks.contains(BukkitAdapter.asBlockType(world.getBlockAt(x + 1, y, z).getType()).getId())
                    || wcfg.disableFireSpreadBlocks.contains(BukkitAdapter.asBlockType(world.getBlockAt(x - 1, y, z).getType()).getId())
                    || wcfg.disableFireSpreadBlocks.contains(BukkitAdapter.asBlockType(world.getBlockAt(x, y, z - 1).getType()).getId())
                    || wcfg.disableFireSpreadBlocks.contains(BukkitAdapter.asBlockType(world.getBlockAt(x, y, z + 1).getType()).getId())) {
                event.setCancelled(true);
                return;
            }
        }

        if (wcfg.useRegions) {
            final ApplicableRegionSet set =
                    WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().getApplicableRegions(BukkitAdapter.adapt(block.getLocation()));

            if (wcfg.highFreqFlags && isFireSpread
                    && !set.testState(null, Flags.FIRE_SPREAD)) {
                event.setCancelled(true);
                return;
            }

            if (wcfg.highFreqFlags && cause == IgniteCause.LAVA
                    && !set.testState(null, Flags.LAVA_FIRE)) {
                event.setCancelled(true);
                return;
            }

            if (cause == IgniteCause.FIREBALL && event.getPlayer() == null) {
                // wtf bukkit, FIREBALL is supposed to be reserved to players
                if (!set.testState(null, Flags.GHAST_FIREBALL)) {
                    event.setCancelled(true);
                    return;
                }
            }

            if (cause == IgniteCause.LIGHTNING && !set.testState(null, Flags.LIGHTNING)) {
                event.setCancelled(true);
            }
        }
    }

    /*
     * Called when a block is destroyed from burning.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(final BlockBurnEvent event) {
        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final BukkitWorldConfiguration wcfg = (BukkitWorldConfiguration) getWorldConfig(event.getBlock().getWorld());

        if (cfg.activityHaltToggle) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.disableFireSpread) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.fireSpreadDisableToggle) {
            final Block block = event.getBlock();
            event.setCancelled(true);
            checkAndDestroyAround(block.getWorld(), block.getX(), block.getY(), block.getZ(), Material.FIRE);
            return;
        }

        if (!wcfg.disableFireSpreadBlocks.isEmpty()) {
            final Block block = event.getBlock();

            if (wcfg.disableFireSpreadBlocks.contains(BukkitAdapter.asBlockType(block.getType()).getId())) {
                event.setCancelled(true);
                checkAndDestroyAround(block.getWorld(), block.getX(), block.getY(), block.getZ(), Material.FIRE);
                return;
            }
        }

        if (wcfg.isChestProtected(BukkitAdapter.adapt(event.getBlock().getLocation()))) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.useRegions) {
            final Block block = event.getBlock();
            final int x = block.getX();
            final int y = block.getY();
            final int z = block.getZ();
            final ApplicableRegionSet set =
                    WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().getApplicableRegions(BukkitAdapter.adapt(block.getLocation()));

            if (!set.testState(null, Flags.FIRE_SPREAD)) {
                checkAndDestroyAround(block.getWorld(), x, y, z, Material.FIRE);
                event.setCancelled(true);
            }

        }
    }

    private void checkAndDestroyAround(final World world, final int x, final int y, final int z, final Material required) {
        checkAndDestroy(world, x, y, z + 1, required);
        checkAndDestroy(world, x, y, z - 1, required);
        checkAndDestroy(world, x, y + 1, z, required);
        checkAndDestroy(world, x, y - 1, z, required);
        checkAndDestroy(world, x + 1, y, z, required);
        checkAndDestroy(world, x - 1, y, z, required);
    }

    private void checkAndDestroy(final World world, final int x, final int y, final int z, final Material required) {
        if (world.getBlockAt(x, y, z).getType() == required) {
            world.getBlockAt(x, y, z).setType(Material.AIR);
        }
    }

    /*
     * Called when block physics occurs.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockPhysics(final BlockPhysicsEvent event) {
        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final WorldConfiguration wcfg = getWorldConfig(event.getBlock().getWorld());

        if (cfg.activityHaltToggle) {
            event.setCancelled(true);
            return;
        }

        final Material id = event.getBlock().getType();

        if (id == Material.GRAVEL && wcfg.noPhysicsGravel) {
            event.setCancelled(true);
            return;
        }

        if (id == Material.SAND && wcfg.noPhysicsSand) {
            event.setCancelled(true);
            return;
        }

        if (id == Material.NETHER_PORTAL && wcfg.allowPortalAnywhere) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.ropeLadders && event.getBlock().getType() == Material.LADDER) {
            if (event.getBlock().getRelative(0, 1, 0).getType() == Material.LADDER) {
                event.setCancelled(true);
            }
        }
    }

    /*
     * Called when a player places a block.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        final Block target = event.getBlock();
        final World world = target.getWorld();

        final WorldConfiguration wcfg = getWorldConfig(world);

        if (wcfg.simulateSponge && target.getType() == Material.SPONGE) {
            if (wcfg.redstoneSponges && target.isBlockIndirectlyPowered()) {
                return;
            }

            final int ox = target.getX();
            final int oy = target.getY();
            final int oz = target.getZ();

            SpongeUtil.clearSpongeWater(BukkitAdapter.adapt(world), ox, oy, oz);
        }
    }

    /*
     * Called when redstone changes.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockRedstoneChange(final BlockRedstoneEvent event) {
        final Block blockTo = event.getBlock();
        final World world = blockTo.getWorld();

        final WorldConfiguration wcfg = getWorldConfig(world);

        if (wcfg.simulateSponge && wcfg.redstoneSponges) {
            final int ox = blockTo.getX();
            final int oy = blockTo.getY();
            final int oz = blockTo.getZ();

            for (int cx = -1; cx <= 1; cx++) {
                for (int cy = -1; cy <= 1; cy++) {
                    for (int cz = -1; cz <= 1; cz++) {
                        final Block sponge = world.getBlockAt(ox + cx, oy + cy, oz + cz);
                        if (sponge.getType() == Material.SPONGE
                                && sponge.isBlockIndirectlyPowered()) {
                            SpongeUtil.clearSpongeWater(BukkitAdapter.adapt(world), ox + cx, oy + cy, oz + cz);
                        }
                        else if (sponge.getType() == Material.SPONGE
                                && !sponge.isBlockIndirectlyPowered()) {
                            SpongeUtil.addSpongeWater(BukkitAdapter.adapt(world), ox + cx, oy + cy, oz + cz);
                        }
                    }
                }
            }

        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeavesDecay(final LeavesDecayEvent event) {
        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final WorldConfiguration wcfg = getWorldConfig(event.getBlock().getWorld());

        if (cfg.activityHaltToggle) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.disableLeafDecay) {
            event.setCancelled(true);
            return;
        }

        if (wcfg.useRegions) {
            if (!StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.LEAF_DECAY))) {
                event.setCancelled(true);
            }
        }
    }

    /*
     * Called when a block is formed based on world conditions.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(final BlockFormEvent event) {
        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final WorldConfiguration wcfg = getWorldConfig(event.getBlock().getWorld());

        if (cfg.activityHaltToggle) {
            event.setCancelled(true);
            return;
        }

        final Material type = event.getNewState().getType();

        if (event instanceof EntityBlockFormEvent) {
            if (((EntityBlockFormEvent) event).getEntity() instanceof Snowman) {
                if (wcfg.disableSnowmanTrails) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }

        if (type == Material.ICE) {
            if (wcfg.disableIceFormation) {
                event.setCancelled(true);
                return;
            }
            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.ICE_FORM))) {
                event.setCancelled(true);
                return;
            }
        }

        if (type == Material.SNOW) {
            if (wcfg.disableSnowFormation) {
                event.setCancelled(true);
                return;
            }
            if (!wcfg.allowedSnowFallOver.isEmpty()) {
                final Material targetId = event.getBlock().getRelative(0, -1, 0).getType();

                if (!wcfg.allowedSnowFallOver.contains(BukkitAdapter.asBlockType(targetId).getId())) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.SNOW_FALL))) {
                event.setCancelled(true);
            }
        }
    }

    /*
     * Called when a block spreads based on world conditions.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(final BlockSpreadEvent event) {
        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final WorldConfiguration wcfg = getWorldConfig(event.getBlock().getWorld());

        if (cfg.activityHaltToggle) {
            event.setCancelled(true);
            return;
        }

        final Material newType = event.getNewState().getType(); // craftbukkit randomly gives AIR as event.getSource even if that block is not air

        if (Materials.isMushroom(newType)) {
            if (wcfg.disableMushroomSpread) {
                event.setCancelled(true);
                return;
            }
            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.MUSHROOMS))) {
                event.setCancelled(true);
                return;
            }
        }

        if (newType == Material.GRASS_BLOCK) {
            if (wcfg.disableGrassGrowth) {
                event.setCancelled(true);
                return;
            }
            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.GRASS_SPREAD))) {
                event.setCancelled(true);
                return;
            }
        }

        if (newType == Material.MYCELIUM) {
            if (wcfg.disableMyceliumSpread) {
                event.setCancelled(true);
                return;
            }

            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.MYCELIUM_SPREAD))) {
                event.setCancelled(true);
                return;
            }
        }

        if (newType == Material.VINE || newType == Material.KELP) {
            if (wcfg.disableVineGrowth) {
                event.setCancelled(true);
                return;
            }

            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.VINE_GROWTH))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockGrow(final BlockGrowEvent event) {
        final WorldConfiguration wcfg = getWorldConfig(event.getBlock().getWorld());
        final Material type = event.getBlock().getType();

        if (Materials.isCrop(type)) {
            if (wcfg.disableCropGrowth) {
                event.setCancelled(false);
                return;
            }

            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                                                           .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.CROP_GROWTH))) {
                event.setCancelled(true);
            }
        }
    }

    /*
     * Called when a block fades.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFade(final BlockFadeEvent event) {

        final WorldConfiguration wcfg = getWorldConfig(event.getBlock().getWorld());

        if (event.getBlock().getType() == Material.ICE) {
            if (wcfg.disableIceMelting) {
                event.setCancelled(true);
                return;
            }

            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.ICE_MELT))) {
                event.setCancelled(true);
            }
        } else if (event.getBlock().getType() == Material.FROSTED_ICE) {
            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.FROSTED_ICE_MELT))) {
                event.setCancelled(true);
            }
        } else if (event.getBlock().getType() == Material.SNOW) {
            if (wcfg.disableSnowMelting) {
                event.setCancelled(true);
                return;
            }

            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.SNOW_MELT))) {
                event.setCancelled(true);
            }
        } else if (event.getBlock().getType() == Material.FARMLAND) {
            if (wcfg.disableSoilDehydration) {
                event.setCancelled(true);
                return;
            }
            if (wcfg.useRegions && !StateFlag.test(WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), (RegionAssociable) null, Flags.SOIL_DRY))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(final BlockExplodeEvent event) {
        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();

        if (cfg.activityHaltToggle) {
            event.setCancelled(true);
            return;
        }

        final WorldConfiguration wcfg = getWorldConfig(event.getBlock().getWorld());
        if (wcfg.blockOtherExplosions) {
            event.setCancelled(true);
        }
    }

}
