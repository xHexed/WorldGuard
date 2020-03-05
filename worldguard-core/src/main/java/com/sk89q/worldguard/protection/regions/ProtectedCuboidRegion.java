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

package com.sk89q.worldguard.protection.regions;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.util.MathUtils;

import java.awt.*;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents a cuboid region that can be protected.
 *
 * @author sk89q
 */
public class ProtectedCuboidRegion extends ProtectedRegion {

    /**
     * Construct a new instance of this cuboid region.<br>
     * Equivalent to {@link #ProtectedCuboidRegion(String, boolean, BlockVector3, BlockVector3)
     * ProtectedCuboidRegion(id, false, pt1, pt2)}<br>
     * <code>transientRegion</code> will be set to false, and this region can be saved.
     *
     * @param id  the region id
     * @param pt1 the first point of this region
     * @param pt2 the second point of this region
     */
    public ProtectedCuboidRegion(final String id, final BlockVector3 pt1, final BlockVector3 pt2) {
        this(id, false, pt1, pt2);
    }

    /**
     * Construct a new instance of this cuboid region.
     *
     * @param id              the region id
     * @param transientRegion whether this region should only be kept in memory and not be saved
     * @param pt1             the first point of this region
     * @param pt2             the second point of this region
     */
    public ProtectedCuboidRegion(final String id, final boolean transientRegion, final BlockVector3 pt1, final BlockVector3 pt2) {
        super(id, transientRegion);
        setMinMaxPoints(pt1, pt2);
    }

    /**
     * Given any two points, sets the minimum and maximum points.
     *
     * @param position1 the first point of this region
     * @param position2 the second point of this region
     */
    private void setMinMaxPoints(final BlockVector3 position1, final BlockVector3 position2) {
        checkNotNull(position1);
        checkNotNull(position2);

        final List<BlockVector3> points = new ArrayList<>();
        points.add(position1);
        points.add(position2);
        setMinMaxPoints(points);
    }

    /**
     * Set the lower point of the cuboid.
     *
     * @param position the point to set as the minimum point
     */
    public void setMinimumPoint(final BlockVector3 position) {
        setMinMaxPoints(position, max);
    }

    /**
     * Set the upper point of the cuboid.
     *
     * @param position the point to set as the maximum point
     */
    public void setMaximumPoint(final BlockVector3 position) {
        setMinMaxPoints(min, position);
    }

    @Override
    public boolean isPhysicalArea() {
        return true;
    }

    @Override
    public List<BlockVector2> getPoints() {
        final List<BlockVector2> pts = new ArrayList<>();
        final int x1 = min.getBlockX();
        final int x2 = max.getBlockX();
        final int z1 = min.getBlockZ();
        final int z2 = max.getBlockZ();

        pts.add(BlockVector2.at(x1, z1));
        pts.add(BlockVector2.at(x2, z1));
        pts.add(BlockVector2.at(x2, z2));
        pts.add(BlockVector2.at(x1, z2));

        return pts;
    }

    @Override
    public boolean contains(final BlockVector3 pt) {
        final double x = pt.getX();
        final double y = pt.getY();
        final double z = pt.getZ();
        return x >= min.getBlockX() && x < max.getBlockX() + 1
                && y >= min.getBlockY() && y < max.getBlockY() + 1
                && z >= min.getBlockZ() && z < max.getBlockZ() + 1;
    }

    @Override
    public RegionType getType() {
        return RegionType.CUBOID;
    }

    @Override
    Area toArea() {
        final int x = getMinimumPoint().getBlockX();
        final int z = getMinimumPoint().getBlockZ();
        final int width = getMaximumPoint().getBlockX() - x + 1;
        final int height = getMaximumPoint().getBlockZ() - z + 1;
        return new Area(new Rectangle(x, z, width, height));
    }

    @Override
    protected boolean intersects(final ProtectedRegion region, final Area thisArea) {
        if (region instanceof ProtectedCuboidRegion) {
            return intersectsBoundingBox(region);
        }
        else {
            return super.intersects(region, thisArea);
        }
    }

    @Override
    public int volume() {
        final int xLength = max.getBlockX() - min.getBlockX() + 1;
        final int yLength = max.getBlockY() - min.getBlockY() + 1;
        final int zLength = max.getBlockZ() - min.getBlockZ() + 1;

        try {
            long v = MathUtils.checkedMultiply(xLength, yLength);
            v = MathUtils.checkedMultiply(v, zLength);
            if (v > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            else {
                return (int) v;
            }
        }
        catch (final ArithmeticException e) {
            return Integer.MAX_VALUE;
        }
    }

}
