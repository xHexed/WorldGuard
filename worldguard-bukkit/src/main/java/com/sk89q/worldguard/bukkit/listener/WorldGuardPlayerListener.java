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
import com.sk89q.worldedit.world.gamemode.GameMode;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.event.player.ProcessPlayerEvent;
import com.sk89q.worldguard.bukkit.util.Events;
import com.sk89q.worldguard.bukkit.util.Materials;
import com.sk89q.worldguard.config.ConfigurationManager;
import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.handler.GameModeFlag;
import com.sk89q.worldguard.util.Entities;
import com.sk89q.worldguard.util.command.CommandFilter;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.TravelAgent;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Handles all events thrown in relation to a player.
 */
public class WorldGuardPlayerListener implements Listener {

    private static final Logger log = Logger.getLogger(WorldGuardPlayerListener.class.getCanonicalName());
    private static final Pattern opPattern = Pattern.compile("^/(?:minecraft:)?(?:bukkit:)?(?:de)?op(?:\\s.*)?$", Pattern.CASE_INSENSITIVE);
    private final WorldGuardPlugin plugin;

    /**
     * Construct the object;
     *
     * @param plugin
     */
    public WorldGuardPlayerListener(final WorldGuardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Register events.
     */
    public void registerEvents() {
        final PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerGameModeChange(final PlayerGameModeChangeEvent event) {
        final Player player = event.getPlayer();
        final LocalPlayer localPlayer = plugin.wrapPlayer(player);
        final WorldConfiguration wcfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager().get(localPlayer.getWorld());
        final Session session = WorldGuard.getInstance().getPlatform().getSessionManager().getIfPresent(localPlayer);
        if (session != null) {
            final GameModeFlag handler = session.getHandler(GameModeFlag.class);
            if (handler != null && wcfg.useRegions && !WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(localPlayer,
                                                                                                                            localPlayer.getWorld())) {
                final GameMode expected = handler.getSetGameMode();
                if (handler.getOriginalGameMode() != null && expected != null && expected != BukkitAdapter.adapt(event.getNewGameMode())) {
                    log.info("Game mode change on " + player.getName() + " has been blocked due to the region GAMEMODE flag");
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final LocalPlayer localPlayer = plugin.wrapPlayer(player);
        final World world = player.getWorld();

        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final WorldConfiguration wcfg = cfg.get(localPlayer.getWorld());

        if (cfg.activityHaltToggle) {
            player.sendMessage(ChatColor.YELLOW
                                       + "Intensive server activity has been HALTED.");

            int removed = 0;

            for (final Entity entity : world.getEntities()) {
                if (Entities.isIntensiveEntity(BukkitAdapter.adapt(entity))) {
                    entity.remove();
                    removed++;
                }
            }

            if (removed > 10) {
                log.info("Halt-Act: " + removed + " entities (>10) auto-removed from "
                        + player.getWorld().toString());
            }
        }

        if (wcfg.fireSpreadDisableToggle) {
            player.sendMessage(ChatColor.YELLOW
                    + "Fire spread is currently globally disabled for this world.");
        }

        Events.fire(new ProcessPlayerEvent(player));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerChat(final AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final LocalPlayer localPlayer = plugin.wrapPlayer(player);
        final WorldConfiguration wcfg =
                WorldGuard.getInstance().getPlatform().getGlobalStateManager().get(localPlayer.getWorld());
        if (wcfg.useRegions) {
            final RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            final ApplicableRegionSet chatFrom = query.getApplicableRegions(localPlayer.getLocation());

            if (!chatFrom.testState(localPlayer, Flags.SEND_CHAT)) {
                final String message = chatFrom.queryValue(localPlayer, Flags.DENY_MESSAGE);
                RegionProtectionListener.formatAndSendDenyMessage("chat", localPlayer, message);
                event.setCancelled(true);
                return;
            }

            boolean anyRemoved = false;
            for (final Iterator<Player> i = event.getRecipients().iterator(); i.hasNext(); ) {
                final Player rPlayer = i.next();
                final LocalPlayer rLocal = plugin.wrapPlayer(rPlayer);
                if (!query.testState(rLocal.getLocation(), rLocal, Flags.RECEIVE_CHAT)) {
                    i.remove();
                    anyRemoved = true;
                }
            }
            if (anyRemoved && event.getRecipients().size() == 0 && wcfg.regionCancelEmptyChatEvents) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerLogin(final PlayerLoginEvent event) {
        final Player player = event.getPlayer();
        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();

        String hostKey = cfg.hostKeys.get(player.getUniqueId().toString());
        if (hostKey == null) {
            hostKey = cfg.hostKeys.get(player.getName().toLowerCase());
        }

        if (hostKey != null) {
            String hostname = event.getHostname();
            final int colonIndex = hostname.indexOf(':');
            if (colonIndex != -1) {
                hostname = hostname.substring(0, colonIndex);
            }

            if (!hostname.equals(hostKey)
                    && !(cfg.hostKeysAllowFMLClients && hostname.equals(hostKey + "\u0000FML\u0000"))) {
                event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                        "You did not join with the valid host key!");
                log.warning("WorldGuard host key check: " +
                        player.getName() + " joined with '" + hostname +
                        "' but '" + hostKey + "' was expected. Kicked!");
                return;
            }
        }

        if (cfg.deopOnJoin) {
            player.setOp(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        final World world = player.getWorld();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            handleBlockRightClick(event);
        }
        else if (event.getAction() == Action.PHYSICAL) {
            handlePhysicalInteract(event);
        }

        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final WorldConfiguration wcfg = cfg.get(BukkitAdapter.adapt(world));

        if (wcfg.removeInfiniteStacks
                && !plugin.hasPermission(player, "worldguard.override.infinite-stack")) {
            final int slot = player.getInventory().getHeldItemSlot();
            final ItemStack heldItem = player.getInventory().getItem(slot);
            if (heldItem != null && heldItem.getAmount() < 0) {
                player.getInventory().setItem(slot, null);
                player.sendMessage(ChatColor.RED + "Infinite stack removed.");
            }
        }
    }

    /**
     * Called when a player right clicks a block.
     *
     * @param event Thrown event
     */
    private void handleBlockRightClick(final PlayerInteractEvent event) {
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }

        final Block block = event.getClickedBlock();
        assert block != null;
        final World world = block.getWorld();
        final Material type = block.getType();
        final Player player = event.getPlayer();
        @Nullable final ItemStack item = event.getItem();

        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final WorldConfiguration wcfg = cfg.get(BukkitAdapter.adapt(world));

        // Infinite stack removal
        if (Materials.isInventoryBlock(type)
                && wcfg.removeInfiniteStacks
                && !plugin.hasPermission(player, "worldguard.override.infinite-stack")) {
            for (int slot = 0; slot < 40; slot++) {
                final ItemStack heldItem = player.getInventory().getItem(slot);
                if (heldItem != null && heldItem.getAmount() < 0) {
                    player.getInventory().setItem(slot, null);
                    player.sendMessage(ChatColor.RED + "Infinite stack in slot #" + slot + " removed.");
                }
            }
        }

        if (wcfg.useRegions) {
            //Block placedIn = block.getRelative(event.getBlockFace());
            final ApplicableRegionSet set =
                    WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().getApplicableRegions(BukkitAdapter.adapt(block.getLocation()));
            //ApplicableRegionSet placedInSet = plugin.getRegionContainer().createQuery().getApplicableRegions(placedIn.getLocation());
            final LocalPlayer localPlayer = plugin.wrapPlayer(player);

            if (item != null && item.getType().getKey().toString().equals(wcfg.regionWand) && plugin.hasPermission(player, "worldguard.region.wand")) {
                if (set.size() > 0) {
                    player.sendMessage(ChatColor.YELLOW + "Can you build? " + (set.testState(localPlayer, Flags.BUILD) ? "Yes" : "No"));

                    final StringBuilder str = new StringBuilder();
                    for (final Iterator<ProtectedRegion> it = set.iterator(); it.hasNext(); ) {
                        str.append(it.next().getId());
                        if (it.hasNext()) {
                            str.append(", ");
                        }
                    }

                    localPlayer.print("Applicable regions: " + str);
                } else {
                    localPlayer.print("WorldGuard: No defined regions here!");
                }

                event.setUseItemInHand(Event.Result.DENY);
            }
        }
    }

    /**
     * Called when a player steps on a pressure plate or tramples crops.
     *
     * @param event Thrown event
     */
    private void handlePhysicalInteract(final PlayerInteractEvent event) {
        if (event.useInteractedBlock() == Event.Result.DENY) return;

        final Player player = event.getPlayer();
        final Block block = event.getClickedBlock(); //not actually clicked but whatever
        //int type = block.getTypeId();
        final World world = player.getWorld();

        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final WorldConfiguration wcfg = cfg.get(BukkitAdapter.adapt(world));

        assert block != null;
        if (block.getType() == Material.FARMLAND && wcfg.disablePlayerCropTrampling) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(final PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        final LocalPlayer localPlayer = plugin.wrapPlayer(player);

        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final WorldConfiguration wcfg = cfg.get(localPlayer.getWorld());

        if (wcfg.useRegions) {
            final ApplicableRegionSet set =
                    WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().getApplicableRegions(localPlayer.getLocation());

            final com.sk89q.worldedit.util.Location spawn = set.queryValue(localPlayer, Flags.SPAWN_LOC);

            if (spawn != null) {
                event.setRespawnLocation(BukkitAdapter.adapt(spawn));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemHeldChange(final PlayerItemHeldEvent event) {
        final Player player = event.getPlayer();

        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final WorldConfiguration wcfg = cfg.get(BukkitAdapter.adapt(player.getWorld()));

        if (wcfg.removeInfiniteStacks
                && !plugin.hasPermission(player, "worldguard.override.infinite-stack")) {
            final int newSlot = event.getNewSlot();
            final ItemStack heldItem = player.getInventory().getItem(newSlot);
            if (heldItem != null && heldItem.getAmount() < 0) {
                player.getInventory().setItem(newSlot, null);
                player.sendMessage(ChatColor.RED + "Infinite stack removed.");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        final World world = event.getFrom().getWorld();
        final Player player = event.getPlayer();
        final LocalPlayer localPlayer = plugin.wrapPlayer(player);
        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final WorldConfiguration wcfg = cfg.get(localPlayer.getWorld());

        if (wcfg.useRegions && cfg.usePlayerTeleports) {
            final ApplicableRegionSet set =
                    WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().getApplicableRegions(BukkitAdapter.adapt(event.getTo()));
            final ApplicableRegionSet setFrom =
                    WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().getApplicableRegions(BukkitAdapter.adapt(event.getFrom()));

            if (event.getCause() == TeleportCause.ENDER_PEARL) {
                if (!WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(localPlayer, localPlayer.getWorld())) {
                    boolean cancel = false;
                    String message = null;
                    if (!setFrom.testState(localPlayer, Flags.ENDERPEARL)) {
                        cancel = true;
                        message = set.queryValue(localPlayer, Flags.EXIT_DENY_MESSAGE);
                    } else if (!set.testState(localPlayer, Flags.ENDERPEARL)) {
                        cancel = true;
                        message = set.queryValue(localPlayer, Flags.ENTRY_DENY_MESSAGE);
                    }
                    if (cancel) {
                        assert message != null;
                        player.sendMessage(message);
                        event.setCancelled(true);
                        return;
                    }
                }
            }
            if (event.getCause() == TeleportCause.CHORUS_FRUIT) {
                if (!WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(localPlayer, localPlayer.getWorld())) {
                    boolean cancel = false;
                    String message = null;
                    if (!setFrom.testState(localPlayer, Flags.CHORUS_TELEPORT)) {
                        cancel = true;
                        message = set.queryValue(localPlayer, Flags.EXIT_DENY_MESSAGE);
                    } else if (!set.testState(localPlayer, Flags.CHORUS_TELEPORT)) {
                        cancel = true;
                        message = set.queryValue(localPlayer, Flags.ENTRY_DENY_MESSAGE);
                    }
                    if (cancel) {
                        assert message != null;
                        player.sendMessage(message);
                        event.setCancelled(true);
                        return;
                    }
                }
            }
            if (null != WorldGuard.getInstance().getPlatform().getSessionManager().get(localPlayer).testMoveTo(localPlayer,
                    BukkitAdapter.adapt(event.getTo()),
                    MoveType.TELEPORT)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerPortal(final PlayerPortalEvent event) {
        if (event.getTo() == null) { // apparently this counts as a cancelled event, implementation specific though
            return;
        }
        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final LocalPlayer localPlayer = plugin.wrapPlayer(event.getPlayer());
        final WorldConfiguration wcfg = cfg.get(BukkitAdapter.adapt(event.getTo().getWorld()));
        if (!wcfg.regionNetherPortalProtection) return;
        if (event.getCause() != TeleportCause.NETHER_PORTAL) {
            return;
        }
        try {
            if (!event.useTravelAgent()) { // either end travel (even though we checked cause) or another plugin is fucking with us, shouldn't create a portal though
                return;
            }
        }
        catch (final NoSuchMethodError ignored) {
            return;
        }
        final TravelAgent pta = event.getPortalTravelAgent();
        pta.findPortal(event.getTo());
        return; // portal exists...it shouldn't make a new one
        // HOPEFULLY covered everything the server can throw at us...proceed protection checking
        // a lot of that is implementation specific though

        // hackily estimate the area that could be effected by this event, since the server refuses to tell us
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        final Player player = event.getPlayer();
        final LocalPlayer localPlayer = plugin.wrapPlayer(player);
        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final WorldConfiguration wcfg = cfg.get(localPlayer.getWorld());

        if (wcfg.useRegions && !WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(localPlayer, localPlayer.getWorld())) {
            final ApplicableRegionSet set =
                    WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().getApplicableRegions(localPlayer.getLocation());

            final Set<String> allowedCommands = set.queryValue(localPlayer, Flags.ALLOWED_CMDS);
            final Set<String> blockedCommands = set.queryValue(localPlayer, Flags.BLOCKED_CMDS);
            final CommandFilter test = new CommandFilter(allowedCommands, blockedCommands);

            if (!test.apply(event.getMessage())) {
                final String message = set.queryValue(localPlayer, Flags.DENY_MESSAGE);
                RegionProtectionListener.formatAndSendDenyMessage("use " + event.getMessage(), localPlayer, message);
                event.setCancelled(true);
                return;
            }
        }

        if (cfg.blockInGameOp) {
            if (opPattern.matcher(event.getMessage()).matches()) {
                player.sendMessage(ChatColor.RED + "/op and /deop can only be used in console (as set by a WG setting).");
                event.setCancelled(true);
            }
        }
    }
}
