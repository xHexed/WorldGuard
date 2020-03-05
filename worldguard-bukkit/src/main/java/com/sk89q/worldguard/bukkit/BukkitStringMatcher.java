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

import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.internal.platform.StringMatcher;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class BukkitStringMatcher implements StringMatcher {

    @Override
    public World matchWorld(final Actor sender, final String filter) throws CommandException {
        final List<? extends World> worlds = WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.GAME_HOOKS).getWorlds();

        // Handle special hash tag groups
        if (filter.charAt(0) == '#') {
            // #main for the main world
            if (filter.equalsIgnoreCase("#main")) {
                return worlds.get(0);

                // #normal for the first normal world
            }
            else if (filter.equalsIgnoreCase("#normal")) {
                for (final World world : worlds) {
                    if (BukkitAdapter.adapt(world).getEnvironment() == org.bukkit.World.Environment.NORMAL) {
                        return world;
                    }
                }

                throw new CommandException("No normal world found.");

                // #nether for the first nether world
            } else if (filter.equalsIgnoreCase("#nether")) {
                for (final World world : worlds) {
                    if (BukkitAdapter.adapt(world).getEnvironment() == org.bukkit.World.Environment.NETHER) {
                        return world;
                    }
                }

                throw new CommandException("No nether world found.");

                // #end for the first nether world
            } else if (filter.equalsIgnoreCase("#end")) {
                for (final World world : worlds) {
                    if (BukkitAdapter.adapt(world).getEnvironment() == org.bukkit.World.Environment.THE_END) {
                        return world;
                    }
                }

                throw new CommandException("No end world found.");

                // Handle getting a world from a player
            } else if (filter.matches("^#player$")) {
                final String[] parts = filter.split(":", 2);

                // They didn't specify an argument for the player!
                if (parts.length == 1) {
                    throw new CommandException("Argument expected for #player.");
                }

                return matchPlayers(sender, parts[1]).iterator().next().getWorld();
            } else {
                throw new CommandException("Invalid identifier '" + filter + "'.");
            }
        }

        for (final World world : worlds) {
            if (world.getName().equals(filter)) {
                return world;
            }
        }

        throw new CommandException("No world by that exact name found.");
    }

    @Override
    public List<LocalPlayer> matchPlayerNames(String filter) {
        final List<LocalPlayer> wgPlayers = Bukkit.getServer().getOnlinePlayers().stream().map(player -> WorldGuardPlugin.inst().wrapPlayer(player)).collect(Collectors.toList());

        filter = filter.toLowerCase();

        // Allow exact name matching
        if (filter.charAt(0) == '@' && filter.length() >= 2) {
            filter = filter.substring(1);

            for (final LocalPlayer player : wgPlayers) {
                if (player.getName().equalsIgnoreCase(filter)) {
                    final List<LocalPlayer> list = new ArrayList<>();
                    list.add(player);
                    return list;
                }
            }

            return new ArrayList<>();
            // Allow partial name matching
        } else if (filter.charAt(0) == '*' && filter.length() >= 2) {
            filter = filter.substring(1);

            final List<LocalPlayer> list = new ArrayList<>();

            for (final LocalPlayer player : wgPlayers) {
                if (player.getName().toLowerCase().contains(filter)) {
                    list.add(player);
                }
            }

            return list;

            // Start with name matching
        } else {
            final List<LocalPlayer> list = new ArrayList<>();

            for (final LocalPlayer player : wgPlayers) {
                if (player.getName().toLowerCase().startsWith(filter)) {
                    list.add(player);
                }
            }

            return list;
        }
    }

    @Override
    public Iterable<? extends LocalPlayer> matchPlayers(final Actor source, final String filter) throws CommandException {
        if (Bukkit.getServer().getOnlinePlayers().isEmpty()) {
            throw new CommandException("No players matched query.");
        }

        final List<LocalPlayer> wgPlayers = Bukkit.getServer().getOnlinePlayers().stream().map(player -> WorldGuardPlugin.inst().wrapPlayer(player)).collect(Collectors.toList());

        if (filter.equals("*")) {
            return checkPlayerMatch(wgPlayers);
        }

        // Handle special hash tag groups
        if (filter.charAt(0) == '#') {
            // Handle #world, which matches player of the same world as the
            // calling source
            if (filter.equalsIgnoreCase("#world")) {
                final List<LocalPlayer> players = new ArrayList<>();
                final LocalPlayer sourcePlayer = WorldGuard.getInstance().checkPlayer(source);
                final World sourceWorld = sourcePlayer.getWorld();

                for (final LocalPlayer player : wgPlayers) {
                    if (player.getWorld().equals(sourceWorld)) {
                        players.add(player);
                    }
                }

                return checkPlayerMatch(players);

                // Handle #near, which is for nearby players.
            } else if (filter.equalsIgnoreCase("#near")) {
                final List<LocalPlayer> players = new ArrayList<>();
                final LocalPlayer sourcePlayer = WorldGuard.getInstance().checkPlayer(source);
                final World sourceWorld = sourcePlayer.getWorld();
                final Vector3 sourceVector = sourcePlayer.getLocation().toVector();

                for (final LocalPlayer player : wgPlayers) {
                    if (player.getWorld().equals(sourceWorld) && player.getLocation().toVector().distanceSq(sourceVector) < 900) {
                        players.add(player);
                    }
                }

                return checkPlayerMatch(players);

            } else {
                throw new CommandException("Invalid group '" + filter + "'.");
            }
        }

        final List<LocalPlayer> players = matchPlayerNames(filter);

        return checkPlayerMatch(players);
    }

    @Override
    public Actor matchPlayerOrConsole(final Actor sender, final String filter) throws CommandException {
        // Let's see if console is wanted
        if (filter.equalsIgnoreCase("#console")
                || filter.equalsIgnoreCase("*console*")
                || filter.equalsIgnoreCase("!")) {
            return WorldGuardPlugin.inst().wrapCommandSender(Bukkit.getServer().getConsoleSender());
        }

        return matchSinglePlayer(sender, filter);
    }

    @Override
    public World getWorldByName(final String worldName) {
        final org.bukkit.World bukkitW = Bukkit.getServer().getWorld(worldName);
        if (bukkitW == null) {
            return null;
        }
        return BukkitAdapter.adapt(bukkitW);
    }

    @Override
    public String replaceMacros(final Actor sender, String message) {
        final Collection<? extends Player> online = Bukkit.getServer().getOnlinePlayers();

        message = message.replace("%name%", sender.getName());
        message = message.replace("%id%", sender.getUniqueId().toString());
        message = message.replace("%online%", String.valueOf(online.size()));

        if (sender instanceof LocalPlayer) {
            final LocalPlayer player = (LocalPlayer) sender;
            final World world = (World) player.getExtent();

            message = message.replace("%world%", world.getName());
            message = message.replace("%health%", String.valueOf(player.getHealth()));
        }

        return message;
    }
}
