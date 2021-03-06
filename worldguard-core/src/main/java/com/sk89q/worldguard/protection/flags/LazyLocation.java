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

import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.NullWorld;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.WorldGuard;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A location that stores the name of the world in case the world is unloaded.
 */
class LazyLocation extends Location {

    private final String worldName;

    LazyLocation(final String worldName, final Vector3 position, final float yaw, final float pitch) {
        super(Optional.ofNullable(findWorld(worldName)).orElse(NullWorld.getInstance()), position, yaw, pitch);
        this.worldName = worldName;
    }

    LazyLocation(final String worldName, final Vector3 position) {
        super(Optional.ofNullable(findWorld(worldName)).orElse(NullWorld.getInstance()), position);
        this.worldName = worldName;
    }

    @Nullable
    private static World findWorld(final String worldName) {
        return WorldGuard.getInstance().getPlatform().getMatcher().getWorldByName(worldName);
    }

    @Override
    public Extent getExtent() {
        if (super.getExtent() != NullWorld.getInstance()) {
            return super.getExtent();
        }
        // try loading the world again now
        // if it fails it will throw an error later (presumably when trying to teleport someone there)
        return Optional.ofNullable(findWorld(worldName)).orElse(new NullWorld() {
            @Override
            public String getName() {
                return worldName;
            }
        });
    }

    public String getWorldName() {
        return worldName;
    }

    public LazyLocation setAngles(final float yaw, final float pitch) {
        return new LazyLocation(worldName, toVector(), yaw, pitch);
    }

    @Override
    public LazyLocation setPosition(final Vector3 position) {
        return new LazyLocation(worldName, position, getYaw(), getPitch());
    }

    public LazyLocation add(final Vector3 other) {
        return setPosition(toVector().add(other));
    }

    public LazyLocation add(final double x, final double y, final double z) {
        return setPosition(toVector().add(x, y, z));
    }

    @Override
    public String toString() {
        if (getPitch() == 0 && getYaw() == 0) {
            return String.join(", ", worldName,
                               String.valueOf((int) getX()), String.valueOf((int) getY()), String.valueOf((int) getZ()));
        }
        else {
            return String.join(", ", worldName,
                               String.valueOf((int) getX()), String.valueOf((int) getY()), String.valueOf((int) getZ()),
                    String.valueOf((int) getPitch()), String.valueOf((int) getYaw()));
        }
    }
}
