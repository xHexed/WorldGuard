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

package com.sk89q.worldguard.protection.flags.registry;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

public class SimpleFlagRegistry implements FlagRegistry {

    private static final Logger log = Logger.getLogger(SimpleFlagRegistry.class.getCanonicalName());

    private final Object lock = new Object();
    private final ConcurrentMap<String, Flag<?>> flags = Maps.newConcurrentMap();
    private boolean initialized;

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(final boolean initialized) {
        this.initialized = initialized;
    }

    @Override
    public void register(final Flag<?> flag) throws FlagConflictException {
        synchronized (lock) {
            if (initialized) {
                throw new IllegalStateException("New flags cannot be registered at this time");
            }

            forceRegister(flag);
        }
    }

    @Override
    public void registerAll(final Collection<Flag<?>> flags) {
        synchronized (lock) {
            for (final Flag<?> flag : flags) {
                try {
                    register(flag);
                }
                catch (final FlagConflictException e) {
                    log.log(Level.WARNING, e.getMessage());
                }
            }
        }
    }

    private Flag<?> forceRegister(final Flag<?> flag) throws FlagConflictException {
        checkNotNull(flag, "flag");
        checkNotNull(flag.getName(), "flag.getName()");

        synchronized (lock) {
            final String name = flag.getName().toLowerCase();
            if (flags.containsKey(name)) {
                throw new FlagConflictException("A flag already exists by the name " + name);
            }

            flags.put(name, flag);
        }

        return flag;
    }

    @Override
    @Nullable
    public Flag<?> get(final String name) {
        checkNotNull(name, "name");
        return flags.get(name.toLowerCase());
    }

    @Override
    public List<Flag<?>> getAll() {
        return Lists.newArrayList(flags.values());
    }

    private Flag<?> getOrCreate(final String name) {
        Flag<?> flag = get(name);

        if (flag != null) {
            return flag;
        }

        synchronized (lock) {
            flag = get(name); // Load again because the previous load was not synchronized
            return flag != null ? flag : forceRegister(new UnknownFlag(name));
        }
    }

    @Override
    public Map<Flag<?>, Object> unmarshal(final Map<String, Object> rawValues, final boolean createUnknown) {
        checkNotNull(rawValues, "rawValues");

        // Ensure that flags are registered.
        Flags.registerAll();

        final ConcurrentMap<Flag<?>, Object> values = Maps.newConcurrentMap();
        final ConcurrentMap<String, Object> regionFlags = Maps.newConcurrentMap();

        for (final Entry<String, Object> entry : rawValues.entrySet()) {
            if (entry.getKey().endsWith("-group")) {
                regionFlags.put(entry.getKey(), entry.getValue());
                continue;
            }
            final Flag<?> flag = createUnknown ? getOrCreate(entry.getKey()) : get(entry.getKey());

            if (flag != null) {
                try {
                    final Object unmarshalled = flag.unmarshal(entry.getValue());

                    if (unmarshalled != null) {
                        values.put(flag, unmarshalled);
                    }
                    else {
                        log.warning("Failed to parse flag '" + flag.getName() + "' with value '" + entry.getValue() + "'");
                    }
                }
                catch (final Exception e) {
                    log.log(Level.WARNING, "Failed to unmarshal flag value for " + flag, e);
                }
            }
        }
        for (final Entry<String, Object> entry : regionFlags.entrySet()) {
            final String parentName = entry.getKey().replaceAll("-group", "");
            final Flag<?> parent = get(parentName);
            if (parent == null || parent instanceof UnknownFlag) {
                if (createUnknown && get(entry.getKey()) == null) {
                    final UnknownFlag unknownFlag = new UnknownFlag(entry.getKey());
                    forceRegister(unknownFlag);
                }
                final Flag<?> unk = get(entry.getKey());
                if (unk != null) {
                    values.put(unk, entry.getValue());
                }
            }
            else {
                values.put(parent.getRegionGroupFlag(), parent.getRegionGroupFlag().unmarshal(entry.getValue()));
            }
        }

        return values;
    }

    @Override
    public int size() {
        return flags.size();
    }

    @Override
    public Iterator<Flag<?>> iterator() {
        return Iterators.unmodifiableIterator(flags.values().iterator());
    }
}
