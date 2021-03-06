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

package com.sk89q.worldguard.bukkit.internal;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.blacklist.target.BlockTarget;
import com.sk89q.worldguard.blacklist.target.ItemTarget;
import com.sk89q.worldguard.blacklist.target.Target;
import com.sk89q.worldguard.blacklist.target.TargetMatcher;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;

public class TargetMatcherSet {

    private final Multimap<String, TargetMatcher> entries = HashMultimap.create();

    public boolean add(final TargetMatcher matcher) {
        checkNotNull(matcher);
        return entries.put(matcher.getMatchedTypeId(), matcher);
    }

    public boolean test(final Target target) {
        final Collection<TargetMatcher> matchers = entries.get(target.getTypeId());

        for (final TargetMatcher matcher : matchers) {
            if (matcher.test(target)) {
                return true;
            }
        }

        return false;
    }

    public boolean test(final Material material) {
        if (material.isBlock()) {
            return test(new BlockTarget(BukkitAdapter.asBlockType(material)));
        }
        else {
            return test(new ItemTarget(BukkitAdapter.asItemType(material)));
        }
    }

    public boolean test(final Block block) {
        return test(new BlockTarget(BukkitAdapter.asBlockType(block.getType())));
    }

    public boolean test(final BlockState state) {
        return test(new BlockTarget(BukkitAdapter.asBlockType(state.getType())));
    }

    public boolean test(final ItemStack itemStack) {
        return test(new ItemTarget(BukkitAdapter.asItemType(itemStack.getType())));
    }

    @Override
    public String toString() {
        return entries.toString();
    }

}
