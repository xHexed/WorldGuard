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

import com.google.common.collect.Sets;

import java.util.*;

/**
 * Stores a set of types.
 */
public class SetFlag<T> extends Flag<Set<T>> {

    private final Flag<T> subFlag;

    public SetFlag(final String name, final RegionGroup defaultGroup, final Flag<T> subFlag) {
        super(name, defaultGroup);
        this.subFlag = subFlag;
    }

    public SetFlag(final String name, final Flag<T> subFlag) {
        super(name);
        this.subFlag = subFlag;
    }

    /**
     * Get the flag that is stored in this flag.
     *
     * @return the stored flag type
     */
    public Flag<T> getType() {
        return subFlag;
    }

    @Override
    public Set<T> parseInput(final FlagContext context) throws InvalidFlagFormat {
        final String input = context.getUserInput();
        if (input.isEmpty()) {
            return Sets.newHashSet();
        }
        else {
            final Set<T> items = Sets.newHashSet();

            for (final String str : input.split(",")) {
                final FlagContext copy = context.copyWith(null, str, null);
                items.add(subFlag.parseInput(copy));
            }

            return items;
        }
    }

    @Override
    public Set<T> unmarshal(final Object o) {
        if (o instanceof Collection<?>) {
            final Collection<?> collection = (Collection<?>) o;
            final Set<T> items = new HashSet<>();

            for (final Object sub : collection) {
                final T item = subFlag.unmarshal(sub);
                if (item != null) {
                    items.add(item);
                }
            }

            return items;
        } else {
            return null;
        }
    }

    @Override
    public Object marshal(final Set<T> o) {
        final List<Object> list = new ArrayList<>();
        for (final T item : o) {
            list.add(subFlag.marshal(item));
        }

        return list;
    }
    
}
