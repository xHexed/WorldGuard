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

import com.sk89q.worldedit.world.entity.EntityType;
import com.sk89q.worldedit.world.entity.EntityTypes;

import javax.annotation.Nullable;

/**
 * Stores an entity type.
 */
public class EntityTypeFlag extends Flag<EntityType> {

    protected EntityTypeFlag(final String name, @Nullable final RegionGroup defaultGroup) {
        super(name, defaultGroup);
    }

    protected EntityTypeFlag(final String name) {
        super(name);
    }

    @Override
    public EntityType parseInput(final FlagContext context) throws InvalidFlagFormat {
        String input = context.getUserInput();
        input = input.trim();
        final EntityType entityType = unmarshal(input);
        if (entityType == null) {
            throw new InvalidFlagFormat("Unknown entity type: " + input);
        }
        return entityType;
    }

    @Override
    public EntityType unmarshal(@Nullable final Object o) {
        return EntityTypes.get(String.valueOf(o).toLowerCase());
    }

    @Override
    public Object marshal(final EntityType o) {
        return o.getId();
    }
}
