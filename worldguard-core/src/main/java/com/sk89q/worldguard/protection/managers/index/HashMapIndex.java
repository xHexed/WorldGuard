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

package com.sk89q.worldguard.protection.managers.index;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.managers.RegionDifference;
import com.sk89q.worldguard.protection.managers.RemovalStrategy;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.sk89q.worldguard.util.Normal.normalize;

/**
 * An index that stores regions in a hash map, which allows for fast lookup
 * by ID but O(n) performance for spatial queries.
 *
 * <p>This implementation supports concurrency to the extent that
 * a {@link ConcurrentMap} does.</p>
 */
public class HashMapIndex extends AbstractRegionIndex implements ConcurrentRegionIndex {

    private final ConcurrentMap<String, ProtectedRegion> regions = new ConcurrentHashMap<>();
    private Set<ProtectedRegion> removed = new HashSet<>();
    private final Object lock = new Object();

    /**
     * Called to rebuild the index after changes.
     */
    protected void rebuildIndex() {
        // Can be implemented by subclasses
    }

    /**
     * Perform the add operation.
     *
     * @param region the region
     */
    private void performAdd(final ProtectedRegion region) {
        checkNotNull(region);

        region.setDirty(true);

        synchronized (lock) {
            final String normalId = normalize(region.getId());

            final ProtectedRegion existing = regions.get(normalId);

            // Casing / form of ID has changed
            if (existing != null && !existing.getId().equals(region.getId())) {
                removed.add(existing);
            }

            regions.put(normalId, region);

            removed.remove(region);

            final ProtectedRegion parent = region.getParent();
            if (parent != null) {
                performAdd(parent);
            }
        }
    }

    @Override
    public void addAll(final Collection<ProtectedRegion> regions) {
        checkNotNull(regions);

        synchronized (lock) {
            for (final ProtectedRegion region : regions) {
                performAdd(region);
            }

            rebuildIndex();
        }
    }

    @Override
    public void bias(final BlockVector2 chunkPosition) {
        // Nothing to do
    }

    @Override
    public void biasAll(final Collection<BlockVector2> chunkPositions) {
        // Nothing to do
    }

    @Override
    public void forget(final BlockVector2 chunkPosition) {
        // Nothing to do
    }

    @Override
    public void forgetAll() {
        // Nothing to do
    }

    @Override
    public void add(final ProtectedRegion region) {
        synchronized (lock) {
            performAdd(region);

            rebuildIndex();
        }
    }

    @Override
    public Set<ProtectedRegion> remove(final String id, final RemovalStrategy strategy) {
        checkNotNull(id);
        checkNotNull(strategy);

        final Set<ProtectedRegion> removedSet = new HashSet<>();

        synchronized (lock) {
            final ProtectedRegion removed = regions.remove(normalize(id));

            if (removed != null) {
                removedSet.add(removed);

                while (true) {
                    final int lastSize = removedSet.size();
                    final Iterator<ProtectedRegion> it = regions.values().iterator();

                    // Handle children
                    while (it.hasNext()) {
                        final ProtectedRegion current = it.next();
                        final ProtectedRegion parent = current.getParent();

                        if (parent != null && removedSet.contains(parent)) {
                            switch (strategy) {
                                case REMOVE_CHILDREN:
                                    removedSet.add(current);
                                    it.remove();
                                    break;
                                case UNSET_PARENT_IN_CHILDREN:
                                    current.clearParent();
                            }
                        }
                    }
                    if (strategy == RemovalStrategy.UNSET_PARENT_IN_CHILDREN
                        || removedSet.size() == lastSize) {
                        break;
                    }
                }
            }

            this.removed.addAll(removedSet);

            rebuildIndex();
        }

        return removedSet;
    }

    @Override
    public boolean contains(final String id) {
        return regions.containsKey(normalize(id));
    }

    @Nullable
    @Override
    public ProtectedRegion get(final String id) {
        return regions.get(normalize(id));
    }

    @Override
    public void apply(final Predicate<ProtectedRegion> consumer) {
        for (final ProtectedRegion region : regions.values()) {
            if (!consumer.test(region)) {
                break;
            }
        }
    }

    @Override
    public void applyContaining(final BlockVector3 position, final Predicate<ProtectedRegion> consumer) {
        apply(region -> !region.contains(position) || consumer.test(region));
    }

    @Override
    public void applyIntersecting(final ProtectedRegion region, final Predicate<ProtectedRegion> consumer) {
        for (final ProtectedRegion found : region.getIntersectingRegions(regions.values())) {
            if (!consumer.test(found)) {
                break;
            }
        }
    }

    @Override
    public int size() {
        return regions.size();
    }

    @Override
    public RegionDifference getAndClearDifference() {
        synchronized (lock) {
            final Set<ProtectedRegion> changed = new HashSet<>();
            final Set<ProtectedRegion> removed = this.removed;

            for (final ProtectedRegion region : regions.values()) {
                if (region.isDirty()) {
                    changed.add(region);
                    region.setDirty(false);
                }
            }

            this.removed = new HashSet<>();

            return new RegionDifference(changed, removed);
        }
    }

    @Override
    public boolean isDirty() {
        synchronized (lock) {
            if (!removed.isEmpty()) {
                return true;
            }

            for (final ProtectedRegion region : regions.values()) {
                if (region.isDirty()) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public Collection<ProtectedRegion> values() {
        return Collections.unmodifiableCollection(regions.values());
    }

    @Override
    public void setDirty(final RegionDifference difference) {
        synchronized (lock) {
            for (final ProtectedRegion changed : difference.getChanged()) {
                changed.setDirty(true);
            }
            removed.addAll(difference.getRemoved());
        }
    }

    @Override
    public void setDirty(final boolean dirty) {
        synchronized (lock) {
            if (!dirty) {
                removed.clear();
            }

            for (final ProtectedRegion region : regions.values()) {
                region.setDirty(dirty);
            }
        }
    }

    /**
     * A factory for new instances using this index.
     */
    public static final class Factory implements Supplier<HashMapIndex> {
        @Override
        public HashMapIndex get() {
            return new HashMapIndex();
        }
    }

}
