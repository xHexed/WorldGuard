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

package com.sk89q.worldguard.bukkit.listener.debounce;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.sk89q.worldguard.bukkit.util.Events;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

public class EventDebounce<K> {

    private final LoadingCache<K, Entry> cache;

    public EventDebounce(final int debounceTime) {
        cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(debounceTime, TimeUnit.MILLISECONDS)
                .concurrencyLevel(2)
                .build(new CacheLoader<K, Entry>() {
                    @Override
                    public Entry load(final K key) {
                        return new Entry();
                    }
                });
    }

    public static <K> EventDebounce<K> create(final int debounceTime) {
        return new EventDebounce<>(debounceTime);
    }

    public <T extends Event & Cancellable> void fireToCancel(final Cancellable originalEvent, final T firedEvent, final K key) {
        final Entry entry = cache.getUnchecked(key);
        if (entry.cancelled != null) {
            if (entry.cancelled) {
                originalEvent.setCancelled(true);
            }
        }
        else {
            final boolean cancelled = Events.fireAndTestCancel(firedEvent);
            if (cancelled) {
                originalEvent.setCancelled(true);
            }
            entry.cancelled = cancelled;
        }
    }

    @Nullable
    public <T extends Event & Cancellable> Entry getIfNotPresent(final K key, final Cancellable originalEvent) {
        final Entry entry = cache.getUnchecked(key);
        if (entry.cancelled != null) {
            if (entry.cancelled) {
                originalEvent.setCancelled(true);
            }
            return null;
        }
        else {
            return entry;
        }
    }

    public static class Entry {
        private Boolean cancelled;

        public void setCancelled(final boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

}
