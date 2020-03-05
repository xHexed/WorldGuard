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

package com.sk89q.worldguard.bukkit;

import com.google.common.collect.ImmutableList;
import com.sk89q.bukkit.util.CommandsManagerRegistration;
import com.sk89q.minecraft.util.commands.*;
import com.sk89q.wepif.PermissionsResolverManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitCommandSender;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.event.player.ProcessPlayerEvent;
import com.sk89q.worldguard.bukkit.listener.*;
import com.sk89q.worldguard.bukkit.session.BukkitSessionManager;
import com.sk89q.worldguard.bukkit.util.Events;
import com.sk89q.worldguard.bukkit.util.logging.ClassSourceValidator;
import com.sk89q.worldguard.commands.GeneralCommands;
import com.sk89q.worldguard.commands.ProtectionCommands;
import com.sk89q.worldguard.commands.ToggleCommands;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.util.logging.RecordMessagePrefixer;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;

/**
 * The main class for WorldGuard as a Bukkit plugin.
 */
public class WorldGuardPlugin extends JavaPlugin {

    private static WorldGuardPlugin inst;
    private static BukkitWorldGuardPlatform platform;
    private final CommandsManager<Actor> commands;
    private PlayerMoveListener playerMoveListener;

    /**
     * Construct objects. Actual loading occurs when the plugin is enabled, so
     * this merely instantiates the objects.
     */
    public WorldGuardPlugin() {
        inst = this;
        commands = new CommandsManager<Actor>() {
            @Override
            public boolean hasPermission(final Actor player, final String perm) {
                return player.hasPermission(perm);
            }
        };
    }

    /**
     * Get the current instance of WorldGuard
     * @return WorldGuardPlugin instance
     */
    public static WorldGuardPlugin inst() {
        return inst;
    }

    /**
     * Called on plugin enable.
     */
    @Override
    public void onEnable() {
        configureLogger();

        getDataFolder().mkdirs(); // Need to create the plugins/WorldGuard folder

        PermissionsResolverManager.initialize(this);

        WorldGuard.getInstance().setPlatform(platform = new BukkitWorldGuardPlatform()); // Initialise WorldGuard
        WorldGuard.getInstance().setup();
        final BukkitSessionManager sessionManager = (BukkitSessionManager) platform.getSessionManager();

        // Set the proper command injector
        commands.setInjector(new SimpleInjector(WorldGuard.getInstance()));

        // Catch bad things being done by naughty plugins that include
        // WorldGuard's classes
        final ClassSourceValidator verifier = new ClassSourceValidator(this);
        verifier.reportMismatches(ImmutableList.of(ProtectedRegion.class, ProtectedCuboidRegion.class, Flag.class));

        // Register command classes
        final CommandsManagerRegistration reg = new CommandsManagerRegistration(this, commands);
        reg.register(ToggleCommands.class);
        reg.register(ProtectionCommands.class);

        getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
            if (!platform.getGlobalStateManager().hasCommandBookGodMode()) {
                reg.register(GeneralCommands.class);
            }
        }, 0L);

        getServer().getScheduler().scheduleSyncRepeatingTask(this, sessionManager, BukkitSessionManager.RUN_DELAY, BukkitSessionManager.RUN_DELAY);

        // Register events
        getServer().getPluginManager().registerEvents(sessionManager, this);
        (new WorldGuardPlayerListener(this)).registerEvents();
        (new WorldGuardBlockListener(this)).registerEvents();
        (new WorldGuardEntityListener(this)).registerEvents();
        (new WorldGuardWeatherListener(this)).registerEvents();
        (new WorldGuardVehicleListener(this)).registerEvents();
        (new WorldGuardServerListener(this)).registerEvents();
        (new WorldGuardHangingListener(this)).registerEvents();

        // Modules
        (playerMoveListener = new PlayerMoveListener(this)).registerEvents();
        (new BlacklistListener(this)).registerEvents();
        (new ChestProtectionListener(this)).registerEvents();
        (new RegionProtectionListener(this)).registerEvents();
        (new RegionFlagsListener(this)).registerEvents();
        (new WorldRulesListener(this)).registerEvents();
        (new BlockedPotionsListener(this)).registerEvents();
        (new EventAbstractionListener(this)).registerEvents();
        (new PlayerModesListener(this)).registerEvents();
        (new BuildPermissionListener(this)).registerEvents();
        (new InvincibilityListener(this)).registerEvents();
        if ("true".equalsIgnoreCase(System.getProperty("worldguard.debug.listener"))) {
            (new DebuggingListener(this, WorldGuard.logger)).registerEvents();
        }

        platform.getGlobalStateManager().updateCommandBookGodMode();

        if (getServer().getPluginManager().isPluginEnabled("CommandBook")) {
            getServer().getPluginManager().registerEvents(new WorldGuardCommandBookListener(this), this);
        }

        // handle worlds separately to initialize already loaded worlds
        final WorldGuardWorldListener worldListener = (new WorldGuardWorldListener(this));
        for (final World world : getServer().getWorlds()) {
            worldListener.initWorld(world);
        }
        worldListener.registerEvents();

        Bukkit.getScheduler().runTask(this, () -> {
            for (final Player player : Bukkit.getServer().getOnlinePlayers()) {
                final ProcessPlayerEvent event = new ProcessPlayerEvent(player);
                Events.fire(event);
            }
        });

        ((SimpleFlagRegistry) WorldGuard.getInstance().getFlagRegistry()).setInitialized(true);

        // Enable metrics
        new Metrics(this);
    }

    @Override
    public void onDisable() {
        WorldGuard.getInstance().disable();
        getServer().getScheduler().cancelTasks(this);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        try {
            final Actor actor = wrapCommandSender(sender);
            try {
                commands.execute(cmd.getName(), args, actor, actor);
            }
            catch (final Throwable t) {
                Throwable next = t;
                do {
                    try {
                        WorldGuard.getInstance().getExceptionConverter().convert(next);
                    }
                    catch (final org.enginehub.piston.exception.CommandException pce) {
                        if (pce.getCause() instanceof CommandException) {
                            throw ((CommandException) pce.getCause());
                        }
                    }
                    next = next.getCause();
                }
                while (next != null);

                throw t;
            }
        }
        catch (final CommandPermissionsException e) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
        }
        catch (final MissingNestedCommandException e) {
            sender.sendMessage(ChatColor.RED + e.getUsage());
        }
        catch (final CommandUsageException e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
            sender.sendMessage(ChatColor.RED + e.getUsage());
        }
        catch (final WrappedCommandException e) {
            sender.sendMessage(ChatColor.RED + e.getCause().getMessage());
        }
        catch (final CommandException e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
        }

        return true;
    }

    /**
     * Check whether a player is in a group.
     * This calls the corresponding method in PermissionsResolverManager
     *
     * @param player The player to check
     * @param group  The group
     *
     * @return whether {@code player} is in {@code group}
     */
    public boolean inGroup(final OfflinePlayer player, final String group) {
        try {
            return PermissionsResolverManager.getInstance().inGroup(player, group);
        }
        catch (final Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    /**
     * Get the groups of a player.
     * This calls the corresponding method in PermissionsResolverManager.
     *
     * @param player The player to check
     *
     * @return The names of each group the playe is in.
     */
    public String[] getGroups(final OfflinePlayer player) {
        try {
            return PermissionsResolverManager.getInstance().getGroups(player);
        }
        catch (final Throwable t) {
            t.printStackTrace();
            return new String[0];
        }
    }

    /**
     * Checks permissions.
     *
     * @param sender The sender to check the permission on.
     * @param perm   The permission to check the permission on.
     *
     * @return whether {@code sender} has {@code perm}
     */
    public boolean hasPermission(final CommandSender sender, final String perm) {
        if (sender.isOp()) {
            if (sender instanceof Player) {
                if (platform.getGlobalStateManager().get(BukkitAdapter.adapt(((Player) sender).getWorld())).opPermissions) {
                    return true;
                }
            }
            else {
                return true;
            }
        }

        // Invoke the permissions resolver
        if (sender instanceof Player) {
            final Player player = (Player) sender;
            return PermissionsResolverManager.getInstance().hasPermission(player.getWorld().getName(), player, perm);
        }

        return false;
    }

    /**
     * Checks permissions and throws an exception if permission is not met.
     *
     * @param sender The sender to check the permission on.
     * @param perm   The permission to check the permission on.
     *
     * @throws CommandPermissionsException if {@code sender} doesn't have {@code perm}
     */
    public void checkPermission(final CommandSender sender, final String perm)
            throws CommandPermissionsException {
        if (!hasPermission(sender, perm)) {
            throw new CommandPermissionsException();
        }
    }

    /**
     * Gets a copy of the WorldEdit plugin.
     *
     * @return The WorldEditPlugin instance
     * @throws CommandException If there is no WorldEditPlugin available
     */
    public WorldEditPlugin getWorldEdit() throws CommandException {
        final Plugin worldEdit = getServer().getPluginManager().getPlugin("WorldEdit");
        if (worldEdit == null) {
            throw new CommandException("WorldEdit does not appear to be installed.");
        }

        if (worldEdit instanceof WorldEditPlugin) {
            return (WorldEditPlugin) worldEdit;
        } else {
            throw new CommandException("WorldEdit detection failed (report error).");
        }
    }

    /**
     * Wrap a player as a LocalPlayer.
     *
     * @param player The player to wrap
     *
     * @return The wrapped player
     */
    public LocalPlayer wrapPlayer(final Player player) {
        return new BukkitPlayer(this, player);
    }

    /**
     * Wrap a player as a LocalPlayer.
     *
     * @param player   The player to wrap
     * @param silenced True to silence messages
     *
     * @return The wrapped player
     */
    public LocalPlayer wrapPlayer(final Player player, final boolean silenced) {
        return new BukkitPlayer(this, player, silenced);
    }

    public Actor wrapCommandSender(final CommandSender sender) {
        if (sender instanceof Player) {
            return wrapPlayer((Player) sender);
        }

        try {
            return new BukkitCommandSender(getWorldEdit(), sender);
        }
        catch (final CommandException e) {
            e.printStackTrace();
        }
        return null;
    }

    public CommandSender unwrapActor(final Actor sender) {
        if (sender instanceof BukkitPlayer) {
            return ((BukkitPlayer) sender).getPlayer();
        }
        else if (sender instanceof BukkitCommandSender) {
            return Bukkit.getConsoleSender(); // TODO Fix
        }
        else {
            throw new IllegalArgumentException("Unknown actor type. Please report");
        }
    }

    /**
     * Wrap a player as a LocalPlayer.
     *
     * <p>This implementation is incomplete -- permissions cannot be checked.</p>
     *
     * @param player The player to wrap
     *
     * @return The wrapped player
     */
    public LocalPlayer wrapOfflinePlayer(final OfflinePlayer player) {
        return new BukkitOfflinePlayer(this, player);
    }

    /**
     * Return a protection query helper object that can be used by another
     * plugin to test whether WorldGuard permits an action at a particular
     * place.
     *
     * @return an instance
     */
    public ProtectionQuery createProtectionQuery() {
        return new ProtectionQuery();
    }

    /**
     * Configure WorldGuard's loggers.
     */
    private void configureLogger() {
        RecordMessagePrefixer.register(Logger.getLogger("com.sk89q.worldguard"), "[WorldGuard] ");
    }

    /**
     * Create a default configuration file from the .jar.
     *
     * @param actual      The destination file
     * @param defaultName The name of the file inside the jar's defaults folder
     */
    public void createDefaultConfiguration(final File actual, final String defaultName) {

        // Make parent directories
        final File parent = actual.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }

        if (actual.exists()) {
            return;
        }

        InputStream input = null;
        try {
            final JarFile file = new JarFile(getFile());
            final ZipEntry copy = file.getEntry("defaults/" + defaultName);
            if (copy == null) throw new FileNotFoundException();
            input = file.getInputStream(copy);
        }
        catch (final IOException e) {
            WorldGuard.logger.severe("Unable to read default configuration: " + defaultName);
        }

        if (input != null) {
            FileOutputStream output = null;

            try {
                output = new FileOutputStream(actual);
                final byte[] buf = new byte[8192];
                int length;
                while ((length = input.read(buf)) > 0) {
                    output.write(buf, 0, length);
                }

                WorldGuard.logger.info("Default configuration file written: "
                                               + actual.getAbsolutePath());
            }
            catch (final IOException e) {
                e.printStackTrace();
            }
            finally {
                try {
                    input.close();
                }
                catch (final IOException ignore) {
                }

                try {
                    if (output != null) {
                        output.close();
                    }
                } catch (final IOException ignore) {
                }
            }
        }
    }

    public PlayerMoveListener getPlayerMoveListener() {
        return playerMoveListener;
    }

}
