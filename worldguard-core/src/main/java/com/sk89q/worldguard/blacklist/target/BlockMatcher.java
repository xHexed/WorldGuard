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

package com.sk89q.worldguard.blacklist.target;

import com.sk89q.worldedit.world.block.BlockType;

public class BlockMatcher implements TargetMatcher {

    private final BlockType type;

    public BlockMatcher(final BlockType type) {
        this.type = type;
    }

    @Override
    public String getMatchedTypeId() {
        return type.getId();
    }

    @Override
    public boolean test(final Target target) {
        return target.getTypeId().equals(getMatchedTypeId());
    }
}
