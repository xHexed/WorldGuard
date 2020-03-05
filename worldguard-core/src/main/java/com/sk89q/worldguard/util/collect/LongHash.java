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

public abstract class LongHash {

    public static long toLong(final int msw, final int lsw) {
        return ((long) msw << 32) + lsw - Integer.MIN_VALUE;
    }

    public static int msw(final long l) {
        return (int) (l >> 32);
    }

    public static int lsw(final long l) {
        return (int) (l) + Integer.MIN_VALUE;
    }

    public boolean containsKey(final int msw, final int lsw) {
        return containsKey(toLong(msw, lsw));
    }

    public void remove(final int msw, final int lsw) {
        remove(toLong(msw, lsw));
    }

    public abstract boolean containsKey(long key);

    public abstract void remove(long key);

}
