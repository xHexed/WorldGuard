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

package com.sk89q.worldguard.util.collect;

import java.util.ArrayList;

public class LongHashTable<V> extends LongBaseHashTable {

    public void put(final int msw, final int lsw, final V value) {
        put(toLong(msw, lsw), value);
    }

    public V get(final int msw, final int lsw) {
        return get(toLong(msw, lsw));
    }

    public synchronized void put(final long key, final V value) {
        put(new Entry(key, value));
    }

    @SuppressWarnings("unchecked")
    public synchronized V get(final long key) {
        final Entry entry = ((Entry) getEntry(key));
        return entry != null ? entry.value : null;
    }

    @SuppressWarnings("unchecked")
    public synchronized ArrayList<V> values() {
        final ArrayList<V> ret = new ArrayList<>();

        final ArrayList<EntryBase> entries = entries();

        for (final EntryBase entry : entries) {
            ret.add(((Entry) entry).value);
        }
        return ret;
    }

    private class Entry extends EntryBase {
        final V value;

        Entry(final long k, final V v) {
            super(k);
            value = v;
        }
    }

}
