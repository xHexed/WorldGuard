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

package com.sk89q.worldguard.chest;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldguard.LocalPlayer;

/**
 * Sign-based chest protection.
 *
 * @author sk89q
 */
public abstract class SignChestProtection implements ChestProtection {

    public abstract Boolean isProtectedSign(Location block, LocalPlayer player);

    public boolean isProtected(final Location location, final LocalPlayer player) {
        final com.sk89q.worldedit.world.block.BlockState blockState = location.getExtent().getBlock(location.toVector().toBlockPoint());
        if (isChest(blockState.getBlockType())) {
            return isProtectedSignAround(location.setY(location.getY() - 1), player);
        }
        else if (blockState.getBlockType() == BlockTypes.SIGN) {
            return isProtectedSignAndChestBinary(location, player);
        }
        else {
            final Boolean res = isProtectedSign(location.setY(location.getY() + 1), player);
            if (res != null) return res;
            return false;
        }
    }

    public boolean isProtectedPlacement(final Location block, final LocalPlayer player) {
        return isProtectedSignAround(block, player);
    }

    private boolean isProtectedSignAround(final Location searchBlock, final LocalPlayer player) {
        Location side;
        Boolean res;

        side = searchBlock;
        res  = isProtectedSign(side, player);
        if (res != null && res) return true;

        side = searchBlock.setX(searchBlock.getX() - 1);
        res  = isProtectedSignAndChest(side, player);
        if (res != null && res) return true;

        side = searchBlock.setX(searchBlock.getX() + 1);
        res = isProtectedSignAndChest(side, player);
        if (res != null && res) return true;

        side = searchBlock.setZ(searchBlock.getZ() - 1);
        res = isProtectedSignAndChest(side, player);
        if (res != null && res) return true;

        side = searchBlock.setZ(searchBlock.getZ() + 1);
        res = isProtectedSignAndChest(side, player);
        return res != null && res;
    }

    private Boolean isProtectedSignAndChest(final Location block, final LocalPlayer player) {
        if (!isChest(block.getExtent().getBlock(block.setY(block.getY() + 1).toVector().toBlockPoint()).getBlockType())) {
            return null;
        }
        return isProtectedSign(block, player);
    }

    private boolean isProtectedSignAndChestBinary(final Location block, final LocalPlayer player) {
        final Boolean res = isProtectedSignAndChest(block, player);
        return !(res == null || !res);
    }

    public boolean isAdjacentChestProtected(final Location searchBlock, final LocalPlayer player) {
        Location side;
        boolean res;

        side = searchBlock;
        res  = isProtected(side, player);
        if (res) return true;

        side = searchBlock.setX(searchBlock.getX() - 1);
        res  = isProtected(side, player);
        if (res) return true;

        side = searchBlock.setX(searchBlock.getX() + 1);
        res = isProtected(side, player);
        if (res) return true;

        side = searchBlock.setZ(searchBlock.getZ() - 1);
        res = isProtected(side, player);
        if (res) return true;

        side = searchBlock.setZ(searchBlock.getZ() + 1);
        res = isProtected(side, player);
        return res;
    }
}
