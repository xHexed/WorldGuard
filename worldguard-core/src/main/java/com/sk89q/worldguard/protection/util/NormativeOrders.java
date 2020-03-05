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

package com.sk89q.worldguard.protection.util;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.FlagValueCalculator;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Sorts a list of regions so that higher priority regions always take
 * precedence over lower priority ones, and after sorting by priority, so
 * child regions always take priority over their parent regions.
 *
 * <p>For example, if the regions are a, aa, aaa, aab, aac, b, ba, bc, where
 * aa implies that the second 'a' is a child of the first 'a', the sorted
 * order must reflect the following properties (where regions on the
 * left of &lt; appear before in the sorted list):</p>
 *
 * <ul>
 *     <li>[aaa, aab, aac] &lt; aa &lt; a</li>
 *     <li>[ba, bc] &lt; b</li>
 * </ul>
 *
 * <p>In the case of "[aaa, aab, aac]," the relative order between these
 * regions is unimportant as they all share the same parent (aaa). The
 * following choices would be valid sorts:</p>
 *
 * <ul>
 *     <li>aaa, aab, aac, aa, a, ba, bc, b</li>
 *     <li>aab, aaa, aac, aa, a, bc, ba, b</li>
 *     <li>bc, ba, b, aab, aaa, aac, aa, a</li>
 *     <li>aab, aaa, bc, aac, aa, ba, a, b</li>
 * </ul>
 *
 * <p>These sorted lists are required for {@link FlagValueCalculator} and
 * some implementations of {@link ApplicableRegionSet}.</p>
 */
public final class NormativeOrders {

    private static final PriorityComparator PRIORITY_COMPARATOR = new PriorityComparator();

    private NormativeOrders() {
    }

    public static void sort(final List<ProtectedRegion> regions) {
        sortInto(Sets.newHashSet(regions), regions);
    }

    public static List<ProtectedRegion> fromSet(final Set<ProtectedRegion> regions) {
        final List<ProtectedRegion> sorted = Arrays.asList(new ProtectedRegion[regions.size()]);
        sortInto(regions, sorted);
        return sorted;
    }

    private static void sortInto(final Set<ProtectedRegion> regions, final List<ProtectedRegion> sorted) {
        final List<RegionNode> root = Lists.newArrayList();
        final Map<ProtectedRegion, RegionNode> nodes = Maps.newHashMap();
        for (final ProtectedRegion region : regions) {
            addNode(nodes, root, region);
        }

        int index = regions.size() - 1;
        for (RegionNode node : root) {
            while (node != null) {
                if (regions.contains(node.region)) {
                    sorted.set(index, node.region);
                    index--;
                }
                node = node.next;
            }
        }

        sorted.sort(PRIORITY_COMPARATOR);
    }

    private static RegionNode addNode(final Map<ProtectedRegion, RegionNode> nodes, final List<RegionNode> root, final ProtectedRegion region) {
        RegionNode node = nodes.get(region);
        if (node == null) {
            node = new RegionNode(region);
            nodes.put(region, node);
            if (region.getParent() != null) {
                addNode(nodes, root, region.getParent()).insertAfter(node);
            }
            else {
                root.add(node);
            }
        }
        return node;
    }

    private static class RegionNode {
        @Nullable
        private RegionNode next;
        private final ProtectedRegion region;

        private RegionNode(final ProtectedRegion region) {
            this.region = region;
        }

        private void insertAfter(final RegionNode node) {
            if (next != null) {
                node.next = next;
            }
            next = node;
        }
    }

    private static class PriorityComparator implements Comparator<ProtectedRegion> {
        @Override
        public int compare(final ProtectedRegion o1, final ProtectedRegion o2) {
            return Integer.compare(o2.getPriority(), o1.getPriority());
        }
    }

}
