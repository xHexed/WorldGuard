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

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

public class LongHashSet extends LongHash {

    protected final long[][][] values = new long[256][][];
    protected final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    protected final ReadLock rl = rwl.readLock();
    protected final WriteLock wl = rwl.writeLock();
    protected int count;

    public boolean isEmpty() {
        rl.lock();
        try {
            return count == 0;
        }
        finally {
            rl.unlock();
        }
    }

    public int size() {
        return count;
    }

    public void add(final int msw, final int lsw) {
        add(toLong(msw, lsw));
    }

    public void add(final long key) {
        wl.lock();
        try {
            final int mainIdx = (int) (key & 255);
            long[][] outer = values[mainIdx];
            if (outer == null) values[mainIdx] = outer = new long[256][];

            final int outerIdx = (int) ((key >> 32) & 255);
            long[] inner = outer[outerIdx];

            if (inner == null) {
                synchronized (this) {
                    outer[outerIdx] = inner = new long[1];
                    inner[0]        = key;
                    count++;
                }
            }
            else {
                int i;
                for (i = 0; i < inner.length; i++) {
                    if (inner[i] == key) {
                        return;
                    }
                }
                inner           = Arrays.copyOf(inner, i + 1);
                outer[outerIdx] = inner;
                inner[i]        = key;
                count++;
            }
        } finally {
            wl.unlock();
        }
    }

    public boolean containsKey(final long key) {
        rl.lock();
        try {
            final long[][] outer = values[(int) (key & 255)];
            if (outer == null) return false;

            final long[] inner = outer[(int) ((key >> 32) & 255)];
            if (inner == null) return false;

            for (final long entry : inner) {
                if (entry == key) return true;
            }
            return false;
        } finally {
            rl.unlock();
        }
    }

    public void remove(final long key) {
        wl.lock();
        try {
            final long[][] outer = values[(int) (key & 255)];
            if (outer == null) return;

            final long[] inner = outer[(int) ((key >> 32) & 255)];
            if (inner == null) return;

            final int max = inner.length - 1;
            for (int i = 0; i <= max; i++) {
                if (inner[i] == key) {
                    count--;
                    if (i != max) {
                        inner[i] = inner[max];
                    }

                    outer[(int) ((key >> 32) & 255)] = (max == 0 ? null : Arrays.copyOf(inner, max));
                    return;
                }
            }
        } finally {
            wl.unlock();
        }
    }

    public long popFirst() {
        wl.lock();
        try {
            for (final long[][] outer : values) {
                if (outer == null) continue;

                for (int i = 0; i < outer.length; i++) {
                    final long[] inner = outer[i];
                    if (inner == null || inner.length == 0) continue;

                    count--;
                    final long ret = inner[inner.length - 1];
                    outer[i] = Arrays.copyOf(inner, inner.length - 1);

                    return ret;
                }
            }
        } finally {
            wl.unlock();
        }
        return 0;
    }

    public long[] popAll() {
        int index = 0;
        wl.lock();
        try {
            final long[] ret = new long[count];
            for (final long[][] outer : values) {
                if (outer == null) continue;

                for (int oIdx = outer.length - 1; oIdx >= 0; oIdx--) {
                    final long[] inner = outer[oIdx];
                    if (inner == null) continue;

                    for (final long entry : inner) {
                        ret[index++] = entry;
                    }
                    outer[oIdx] = null;
                }
            }
            count = 0;
            return ret;
        } finally {
            wl.unlock();
        }
    }

    public long[] keys() {
        int index = 0;
        rl.lock();
        try {
            final long[] ret = new long[count];
            for (final long[][] outer : values) {
                if (outer == null) continue;

                for (final long[] inner : outer) {
                    if (inner == null) continue;

                    for (final long entry : inner) {
                        ret[index++] = entry;
                    }
                }
            }
            return ret;
        } finally {
            rl.unlock();
        }
    }

}
