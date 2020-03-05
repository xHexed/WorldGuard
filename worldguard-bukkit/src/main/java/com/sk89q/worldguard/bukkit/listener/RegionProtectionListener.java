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

import com.google.common.base.Predicate;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
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
import com.sk89q.worldguard.bukkit.internal.WGMetadata;
import com.sk89q.worldguard.bukkit.protection.events.DisallowedPVPEvent;
import com.sk89q.worldguard.bukkit.util.Entities;
import com.sk89q.worldguard.bukkit.util.Events;
import com.sk89q.worldguard.bukkit.util.InteropUtils;
import com.sk89q.worldguard.bukkit.util.Materials;
import com.sk89q.worldguard.commands.CommandUtils;
import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.domains.Association;
import com.sk89q.worldguard.internal.permission.RegionPermissionModel;
import com.sk89q.worldguard.protection.DelayedRegionOverlapAssociation;
import com.sk89q.worldguard.protection.association.Associables;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.vehicle.VehicleExitEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handle events that need to be processed by region protection.
 */
public class RegionProtectionListener extends AbstractListener {

    private static final String DENY_MESSAGE_KEY = "worldguard.region.lastMessage";
    private static final String DISEMBARK_MESSAGE_KEY = "worldguard.region.disembarkMessage";
    private static final int LAST_MESSAGE_DELAY = 500;

    /**
     * Construct the listener.
     *
     * @param plugin an instance of WorldGuardPlugin
     */
    public RegionProtectionListener(final WorldGuardPlugin plugin) {
        super(plugin);
    }

    static void formatAndSendDenyMessage(final String what, final LocalPlayer localPlayer, String message) {
        if (message == null || message.isEmpty()) return;
        message = WorldGuard.getInstance().getPlatform().getMatcher().replaceMacros(localPlayer, message);
        message = CommandUtils.replaceColorMacros(message);
        localPlayer.printRaw(message.replace("%what%", what));
    }

    /**
     * Combine the flags from a delegate event with an array of flags.
     *
     * <p>The delegate event's flags appear at the end.</p>
     *
     * @param event The event
     * @param flag  An array of flags
     *
     * @return An array of flags
     */
    private static StateFlag[] combine(final DelegateEvent event, final StateFlag... flag) {
        final List<StateFlag> extra = event.getRelevantFlags();
        final StateFlag[] flags = Arrays.copyOf(flag, flag.length + extra.size());
        for (int i = 0; i < extra.size(); i++) {
            flags[flag.length + i] = extra.get(i);
        }
        return flags;
    }

    /**
     * Tell a sender that s/he cannot do something 'here'.
     *
     * @param event    the event
     * @param cause    the cause
     * @param location the location
     * @param what     what was done
     */
    private void tellErrorMessage(final DelegateEvent event, final Cause cause, final Location location, final String what) {
        if (event.isSilent() || cause.isIndirect()) {
            return;
        }

        final Object rootCause = cause.getRootCause();

        if (rootCause instanceof Player) {
            final Player player = (Player) rootCause;

            final long now = System.currentTimeMillis();
            final Long lastTime = WGMetadata.getIfPresent(player, DENY_MESSAGE_KEY, Long.class);
            if (lastTime == null || now - lastTime >= LAST_MESSAGE_DELAY) {
                final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
                final LocalPlayer localPlayer = getPlugin().wrapPlayer(player);
                final String message = query.queryValue(BukkitAdapter.adapt(location), localPlayer, Flags.DENY_MESSAGE);
                formatAndSendDenyMessage(what, localPlayer, message);
                WGMetadata.put(player, DENY_MESSAGE_KEY, now);
            }
        }
    }

    /**
     * Return whether the given cause is whitelist (should be ignored).
     *
     * @param cause the cause
     * @param world the world
     * @param pvp   whether the event in question is PvP combat
     *
     * @return true if whitelisted
     */
    private boolean isWhitelisted(final Cause cause, final World world, final boolean pvp) {
        final Object rootCause = cause.getRootCause();

        if (rootCause instanceof Player) {
            final Player player = (Player) rootCause;
            final LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            final WorldConfiguration config = getWorldConfig(BukkitAdapter.adapt(world));

            if (config.fakePlayerBuildOverride && InteropUtils.isFakePlayer(player)) {
                return true;
            }

            return !pvp && new RegionPermissionModel(localPlayer).mayIgnoreRegionProtection(BukkitAdapter.adapt(world));
        }
        else {
            return false;
        }
    }

    private RegionAssociable createRegionAssociable(final Cause cause) {
        final Object rootCause = cause.getRootCause();

        if (!cause.isKnown()) {
            return Associables.constant(Association.NON_MEMBER);
        }
        else if (rootCause instanceof Player) {
            return getPlugin().wrapPlayer((Player) rootCause);
        }
        else if (rootCause instanceof OfflinePlayer) {
            return getPlugin().wrapOfflinePlayer((OfflinePlayer) rootCause);
        }
        else if (rootCause instanceof Entity) {
            final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            return new DelayedRegionOverlapAssociation(query, BukkitAdapter.adapt(((Entity) rootCause).getLocation()));
        } else if (rootCause instanceof Block) {
            final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            return new DelayedRegionOverlapAssociation(query, BukkitAdapter.adapt(((Block) rootCause).getLocation()));
        } else {
            return Associables.constant(Association.NON_MEMBER);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceBlock(final PlaceBlockEvent event) {
        if (event.getResult() == Result.ALLOW) return; // Don't care about events that have been pre-allowed
        if (!isRegionSupportEnabled(BukkitAdapter.adapt(event.getWorld()))) return; // Region support disabled
        if (isWhitelisted(event.getCause(), event.getWorld(), false)) return; // Whitelisted cause

        final Material type = event.getEffectiveMaterial();
        final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        final RegionAssociable associable = createRegionAssociable(event.getCause());

        // Don't check liquid flow unless it's enabled
        if (event.getCause().getRootCause() instanceof Block
                && Materials.isLiquid(type)
                && !getWorldConfig(BukkitAdapter.adapt(event.getWorld())).checkLiquidFlow) {
            return;
        }

        event.filter((Predicate<Location>) target -> {
            final boolean canPlace;
            final String what;

            /* Flint and steel, fire charge, etc. */
            if (type == Material.FIRE) {
                final Block block = event.getCause().getFirstBlock();
                final boolean fire = block != null && block.getType() == Material.FIRE;
                final boolean lava = block != null && Materials.isLava(block.getType());
                final List<StateFlag> flags = new ArrayList<>();
                flags.add(Flags.BLOCK_PLACE);
                flags.add(Flags.LIGHTER);
                if (fire) flags.add(Flags.FIRE_SPREAD);
                if (lava) flags.add(Flags.LAVA_FIRE);
                assert target != null;
                canPlace = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, flags.toArray(new StateFlag[0])));
                what     = "place fire";

            } else if (type == Material.FROSTED_ICE) {
                event.setSilent(true); // gets spammy
                assert target != null;
                canPlace = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.BLOCK_PLACE, Flags.FROSTED_ICE_FORM));
                what     = "use frostwalker"; // hidden anyway
                /* Everything else */
            }
            else {
                assert target != null;
                canPlace = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.BLOCK_PLACE));
                what     = "place that block";
            }

            if (!canPlace) {
                tellErrorMessage(event, event.getCause(), target, what);
                return false;
            }

            return true;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreakBlock(final BreakBlockEvent event) {
        if (event.getResult() == Result.ALLOW) return; // Don't care about events that have been pre-allowed
        if (!isRegionSupportEnabled(BukkitAdapter.adapt(event.getWorld()))) return; // Region support disabled
        if (isWhitelisted(event.getCause(), event.getWorld(), false)) return; // Whitelisted cause

        final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();

        if (!event.isCancelled()) {
            final RegionAssociable associable = createRegionAssociable(event.getCause());

            event.filter((Predicate<Location>) target -> {
                final boolean canBreak;
                final String what;

                /* TNT */
                if (event.getCause().find(EntityType.PRIMED_TNT, EntityType.MINECART_TNT) != null) {
                    assert target != null;
                    canBreak = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.BLOCK_BREAK, Flags.TNT));
                    what     = "use dynamite";

                    /* Everything else */
                }
                else {
                    assert target != null;
                    canBreak = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.BLOCK_BREAK));
                    what     = "break that block";
                }

                if (!canBreak) {
                    tellErrorMessage(event, event.getCause(), target, what);
                    return false;
                }

                return true;
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUseBlock(final UseBlockEvent event) {
        if (event.getResult() == Result.ALLOW) return; // Don't care about events that have been pre-allowed
        if (!isRegionSupportEnabled(BukkitAdapter.adapt(event.getWorld()))) return; // Region support disabled
        if (isWhitelisted(event.getCause(), event.getWorld(), false)) return; // Whitelisted cause

        final Material type = event.getEffectiveMaterial();
        final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        final RegionAssociable associable = createRegionAssociable(event.getCause());

        event.filter((Predicate<Location>) target -> {
            final boolean canUse;
            final String what;

            /* Saplings, etc. */
            if (Materials.isConsideredBuildingIfUsed(type)) {
                assert target != null;
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event));
                what   = "use that";

                /* Inventory */
            }
            else if (Materials.isInventoryBlock(type)) {
                assert target != null;
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.INTERACT, Flags.CHEST_ACCESS));
                what   = "open that";

                /* Beds */
            } else if (Materials.isBed(type)) {
                assert target != null;
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.INTERACT, Flags.SLEEP));
                what   = "sleep";

                /* TNT */
            } else if (type == Material.TNT) {
                assert target != null;
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.INTERACT, Flags.TNT));
                what   = "use explosives";

                /* Legacy USE flag */
            } else if (Materials.isUseFlagApplicable(type)) {
                assert target != null;
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.INTERACT, Flags.USE));
                what   = "use that";

                /* Everything else */
            }
            else {
                assert target != null;
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.INTERACT));
                what   = "use that";
            }

            if (!canUse) {
                tellErrorMessage(event, event.getCause(), target, what);
                return false;
            }

            return true;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnEntity(final SpawnEntityEvent event) {
        if (event.getResult() == Result.ALLOW) return; // Don't care about events that have been pre-allowed
        if (!isRegionSupportEnabled(BukkitAdapter.adapt(event.getWorld()))) return; // Region support disabled
        if (isWhitelisted(event.getCause(), event.getWorld(), false)) return; // Whitelisted cause

        final Location target = event.getTarget();
        final EntityType type = event.getEffectiveType();

        final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        final RegionAssociable associable = createRegionAssociable(event.getCause());

        final boolean canSpawn;
        final String what;

        /* Vehicles */
        if (Entities.isVehicle(type)) {
            canSpawn = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.PLACE_VEHICLE));
            what     = "place vehicles";

            /* Item pickup */
        }
        else if (event.getEntity() instanceof Item) {
            canSpawn = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.ITEM_DROP));
            what = "drop items";

        /* XP drops */
        } else if (type == EntityType.EXPERIENCE_ORB) {
            canSpawn = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.EXP_DROPS));
            what = "drop XP";

        } else if (Entities.isAoECloud(type)) {
            canSpawn = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.POTION_SPLASH));
            what = "use lingering potions";

        /* Everything else */
        } else {
            canSpawn = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event));

            if (event.getEntity() instanceof Item) {
                what = "drop items";
            } else {
                what = "place things";
            }
        }

        if (!canSpawn) {
            tellErrorMessage(event, event.getCause(), target, what);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDestroyEntity(final DestroyEntityEvent event) {
        if (event.getResult() == Result.ALLOW) return; // Don't care about events that have been pre-allowed
        if (!isRegionSupportEnabled(BukkitAdapter.adapt(event.getWorld()))) return; // Region support disabled
        if (isWhitelisted(event.getCause(), event.getWorld(), false)) return; // Whitelisted cause

        final Location target = event.getTarget();
        final EntityType type = event.getEntity().getType();
        final RegionAssociable associable = createRegionAssociable(event.getCause());

        final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        final boolean canDestroy;
        final String what;

        /* Vehicles */
        if (Entities.isVehicle(type)) {
            canDestroy = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.DESTROY_VEHICLE));
            what       = "break vehicles";

            /* Item pickup */
        }
        else if (event.getEntity() instanceof Item || event.getEntity() instanceof ExperienceOrb) {
            canDestroy = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.ITEM_PICKUP));
            what = "pick up items";

        /* Everything else */
        } else {
            canDestroy = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event));
            what = "break things";
        }

        if (!canDestroy) {
            tellErrorMessage(event, event.getCause(), target, what);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUseEntity(final UseEntityEvent event) {
        if (event.getResult() == Result.ALLOW) return; // Don't care about events that have been pre-allowed
        if (!isRegionSupportEnabled(BukkitAdapter.adapt(event.getWorld()))) return; // Region support disabled
        if (isWhitelisted(event.getCause(), event.getWorld(), false)) return; // Whitelisted cause

        final Location target = event.getTarget();
        final RegionAssociable associable = createRegionAssociable(event.getCause());

        final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        final boolean canUse;
        final String what;

        /* Hostile / ambient mob override */
        final EntityType type = event.getEntity().getType();
        if (Entities.isHostile(event.getEntity()) || Entities.isAmbient(event.getEntity())
                || Entities.isNPC(event.getEntity())) {
            canUse = event.getRelevantFlags().isEmpty() || query.queryState(BukkitAdapter.adapt(target), associable, combine(event)) != State.DENY;
            what   = "use that";
            /* Paintings, item frames, etc. */
        }
        else if (Entities.isConsideredBuildingIfUsed(event.getEntity())) {
            if (type == EntityType.ITEM_FRAME && event.getCause().getFirstPlayer() != null
                    && ((ItemFrame) event.getEntity()).getItem().getType() != Material.AIR) {
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.ITEM_FRAME_ROTATE));
                what = "change that";
            } else if (Entities.isMinecart(type)) {
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.CHEST_ACCESS));
                what = "open that";
            } else {
                canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event));
                what = "change that";
            }
        /* Ridden on use */
        } else if (Entities.isRiddenOnUse(event.getEntity())) {
            canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.RIDE, Flags.INTERACT));
            what = "ride that";

        /* Everything else */
        } else {
            canUse = query.testBuild(BukkitAdapter.adapt(target), associable, combine(event, Flags.INTERACT));
            what = "use that";
        }

        if (!canUse) {
            tellErrorMessage(event, event.getCause(), target, what);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageEntity(final DamageEntityEvent event) {
        if (event.getResult() == Result.ALLOW) return; // Don't care about events that have been pre-allowed
        if (!isRegionSupportEnabled(BukkitAdapter.adapt(event.getWorld()))) return; // Region support disabled
        // Whitelist check is below

        final com.sk89q.worldedit.util.Location target = BukkitAdapter.adapt(event.getTarget());
        final RegionAssociable associable = createRegionAssociable(event.getCause());

        final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        final Player playerAttacker = event.getCause().getFirstPlayer();
        boolean canDamage;
        final String what;

        // Block PvP like normal even if the player has an override permission
        // because (1) this is a frequent source of confusion and
        // (2) some users want to block PvP even with the bypass permission
        final boolean pvp = event.getEntity() instanceof Player && playerAttacker != null && !playerAttacker.equals(event.getEntity());
        if (isWhitelisted(event.getCause(), event.getWorld(), pvp)) {
            return;
        }

        /* Hostile / ambient mob override */
        if (Entities.isHostile(event.getEntity()) || Entities.isAmbient(event.getEntity())
                || Entities.isVehicle(event.getEntity().getType())) {
            canDamage = event.getRelevantFlags().isEmpty() || query.queryState(target, associable, combine(event)) != State.DENY;
            what = "hit that";

        /* Paintings, item frames, etc. */
        } else if (Entities.isConsideredBuildingIfUsed(event.getEntity())) {
            canDamage = query.testBuild(target, associable, combine(event));
            what = "change that";

        /* PVP */
        }
        else if (pvp) {
            final LocalPlayer localAttacker = WorldGuardPlugin.inst().wrapPlayer(playerAttacker);
            final Player defender = (Player) event.getEntity();

            // if defender is an NPC
            if (Entities.isNPC(defender)) {
                return;
            }

            canDamage = query.testBuild(target, associable, combine(event, Flags.PVP))
                    && query.queryState(localAttacker.getLocation(), localAttacker, combine(event, Flags.PVP)) != State.DENY
                    && query.queryState(target, localAttacker, combine(event, Flags.PVP)) != State.DENY;

            // Fire the disallow PVP event
            if (!canDamage && Events.fireAndTestCancel(new DisallowedPVPEvent(playerAttacker, defender, event.getOriginalEvent()))) {
                canDamage = true;
            }

            what = "PvP";

        /* Player damage not caused  by another player */
        } else if (event.getEntity() instanceof Player) {
            canDamage = event.getRelevantFlags().isEmpty() || query.queryState(target, associable, combine(event)) != State.DENY;
            what = "damage that";

        /* damage to non-hostile mobs (e.g. animals) */
        } else if (Entities.isNonHostile(event.getEntity())) {
            canDamage = query.testBuild(target, associable, combine(event, Flags.DAMAGE_ANIMALS));
            what = "harm that";

        /* Everything else */
        } else {
            canDamage = query.testBuild(target, associable, combine(event, Flags.INTERACT));
            what = "hit that";
        }

        if (!canDamage) {
            tellErrorMessage(event, event.getCause(), event.getTarget(), what);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleExit(final VehicleExitEvent event) {
        final Entity vehicle = event.getVehicle();
        if (!isRegionSupportEnabled(BukkitAdapter.adapt(vehicle.getWorld()))) return; // Region support disabled
        final Entity exited = event.getExited();

        if (vehicle instanceof Tameable && exited instanceof Player) {
            final Player player = (Player) exited;
            final LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            if (!isWhitelisted(Cause.create(player), vehicle.getWorld(), false)) {
                final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
                final Location location = vehicle.getLocation();
                if (!query.testBuild(BukkitAdapter.adapt(location), localPlayer, Flags.RIDE, Flags.INTERACT)) {
                    final long now = System.currentTimeMillis();
                    final Long lastTime = WGMetadata.getIfPresent(player, DISEMBARK_MESSAGE_KEY, Long.class);
                    if (lastTime == null || now - lastTime >= LAST_MESSAGE_DELAY) {
                        player.sendMessage(ChatColor.GOLD + "Don't disembark here!" + ChatColor.GRAY + " You can't get back on.");
                        WGMetadata.put(player, DISEMBARK_MESSAGE_KEY, now);
                    }

                    event.setCancelled(true);
                }
            }
        }
    }

    private boolean isWhitelistedEntity(final Entity entity) {
        return Entities.isNonPlayerCreature(entity);
    }

}
