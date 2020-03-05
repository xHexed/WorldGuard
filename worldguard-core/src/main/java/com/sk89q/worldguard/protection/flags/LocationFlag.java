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

package com.sk89q.worldguard.protection.flags;

import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.internal.permission.RegionPermissionModel;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.HashMap;
import java.util.Map;

public class LocationFlag extends Flag<Location> {

    public LocationFlag(final String name, final RegionGroup defaultGroup) {
        super(name, defaultGroup);
    }

    public LocationFlag(final String name) {
        super(name);
    }

    @Override
    public Location parseInput(final FlagContext context) throws InvalidFlagFormat {
        final String input = context.getUserInput();
        final Player player = context.getPlayerSender();

        Location loc = null;
        if ("here".equalsIgnoreCase(input)) {
            final Location playerLoc = player.getLocation();
            loc = new LazyLocation(((World) playerLoc.getExtent()).getName(),
                                   playerLoc.toVector(), playerLoc.getYaw(), playerLoc.getPitch());
        }
        else {
            final String[] split = input.split(",");
            if (split.length >= 3) {
                try {
                    final World world = player.getWorld();
                    final double x = Double.parseDouble(split[0]);
                    final double y = Double.parseDouble(split[1]);
                    final double z = Double.parseDouble(split[2]);
                    final float yaw = split.length < 4 ? 0 : Float.parseFloat(split[3]);
                    final float pitch = split.length < 5 ? 0 : Float.parseFloat(split[4]);

                    loc = new LazyLocation(world.getName(), Vector3.at(x, y, z), yaw, pitch);
                }
                catch (final NumberFormatException ignored) {
                }
            }
        }
        if (loc != null) {
            final Object obj = context.get("region");
            if (obj instanceof ProtectedRegion) {
                final ProtectedRegion rg = (ProtectedRegion) obj;
                if (WorldGuard.getInstance().getPlatform().getGlobalStateManager().get(player.getWorld()).boundedLocationFlags) {
                    if (!rg.contains(loc.toVector().toBlockPoint())) {
                        if (new RegionPermissionModel(player).mayOverrideLocationFlagBounds(rg)) {
                            player.printDebug("WARNING: Flag location is outside of region.");
                        } else {
                            // no permission
                            throw new InvalidFlagFormat("You can't set that flag outside of the region boundaries.");
                        }
                    }
                    // clamp height to world limits
                    loc.setPosition(loc.toVector().clampY(0, player.getWorld().getMaxY()));
                    return loc;
                }
            }
            return loc;
        }
        throw new InvalidFlagFormat("Expected 'here' or x,y,z.");
    }

    @Override
    public Location unmarshal(final Object o) {
        if (o instanceof Map<?, ?>) {
            final Map<?, ?> map = (Map<?, ?>) o;

            final Object rawWorld = map.get("world");
            if (rawWorld == null) return null;

            final Object rawX = map.get("x");
            if (rawX == null) return null;

            final Object rawY = map.get("y");
            if (rawY == null) return null;

            final Object rawZ = map.get("z");
            if (rawZ == null) return null;

            final Object rawYaw = map.get("yaw");
            if (rawYaw == null) return null;

            final Object rawPitch = map.get("pitch");
            if (rawPitch == null) return null;

            final Vector3 position = Vector3.at(toNumber(rawX), toNumber(rawY), toNumber(rawZ));
            final float yaw = (float) toNumber(rawYaw);
            final float pitch = (float) toNumber(rawPitch);

            return new LazyLocation(String.valueOf(rawWorld), position, yaw, pitch);
        }

        return null;
    }

    @Override
    public Object marshal(final Location o) {
        final Vector3 position = o.toVector();
        final Map<String, Object> vec = new HashMap<>();
        if (o instanceof LazyLocation) {
            vec.put("world", ((LazyLocation) o).getWorldName());
        }
        else {
            try {
                if (o.getExtent() instanceof World) {
                    vec.put("world", ((World) o.getExtent()).getName());
                }
            }
            catch (final NullPointerException e) {
                return null;
            }
        }
        vec.put("x", position.getX());
        vec.put("y", position.getY());
        vec.put("z", position.getZ());
        vec.put("yaw", o.getYaw());
        vec.put("pitch", o.getPitch());
        return vec;
    }

    private double toNumber(final Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        else {
            return 0;
        }

    }
}
