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

package com.sk89q.worldguard.session;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.session.handler.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class AbstractSessionManager implements SessionManager {

    public static final int RUN_DELAY = 20;
    public static final long SESSION_LIFETIME = 10;

    static {
        final Handler.Factory<?>[] factories = {
                HealFlag.FACTORY,
                FeedFlag.FACTORY,
                NotifyEntryFlag.FACTORY,
                NotifyExitFlag.FACTORY,
                EntryFlag.FACTORY,
                ExitFlag.FACTORY,
                FarewellFlag.FACTORY,
                GreetingFlag.FACTORY,
                GameModeFlag.FACTORY,
                InvincibilityFlag.FACTORY,
                TimeLockFlag.FACTORY,
                WeatherLockFlag.FACTORY,
                GodMode.FACTORY,
                WaterBreathing.FACTORY
        };
        defaultHandlers.addAll(Arrays.asList(factories));
    }

    private final LoadingCache<WorldPlayerTuple, Boolean> bypassCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(2, TimeUnit.SECONDS)
            .build(new CacheLoader<WorldPlayerTuple, Boolean>() {
                @Override
                public Boolean load(@Nonnull final WorldPlayerTuple tuple) {
                    return tuple.getPlayer().hasPermission("worldguard.region.bypass." + tuple.getWorld().getName());
                }
            });
    private final List<Handler.Factory<? extends Handler>> handlers = new LinkedList<>();

    private static final List<Handler.Factory<? extends Handler>> defaultHandlers = new LinkedList<>();
    private final LoadingCache<CacheKey, Session> sessions = CacheBuilder.newBuilder()
            .expireAfterAccess(SESSION_LIFETIME, TimeUnit.MINUTES)
            .build(new CacheLoader<CacheKey, Session>() {
                @Override
                public Session load(@Nonnull final CacheKey key) {
                    return createSession(key.playerRef.get());
                }
            });

    protected AbstractSessionManager() {
        handlers.addAll(defaultHandlers);
    }

    @Override
    public boolean registerHandler(final Handler.Factory<? extends Handler> factory, @Nullable final Handler.Factory<? extends Handler> after) {
        if (factory == null) return false;
        WorldGuard.logger.log(Level.INFO, "Registering session handler "
                + factory.getClass().getEnclosingClass().getName());
        if (after == null) {
            handlers.add(factory);
        }
        else {
            final int index = handlers.indexOf(after);
            if (index == -1) return false;

            handlers.add(index, factory); // shifts "after" right one, and everything after "after" right one
        }
        return true;
    }

    @Override
    public boolean unregisterHandler(final Handler.Factory<? extends Handler> factory) {
        if (defaultHandlers.contains(factory)) {
            WorldGuard.logger.log(Level.WARNING, "Someone is unregistering a default WorldGuard handler: "
                    + factory.getClass().getEnclosingClass().getName() + ". This may cause parts of WorldGuard to stop functioning");
        }
        else {
            WorldGuard.logger.log(Level.INFO, "Unregistering session handler "
                    + factory.getClass().getEnclosingClass().getName());
        }
        return handlers.remove(factory);
    }

    @Override
    public boolean hasBypass(final LocalPlayer player, final World world) {
        return bypassCache.getUnchecked(new WorldPlayerTuple(world, player));
    }

    @Override
    public void resetState(final LocalPlayer player) {
        checkNotNull(player, "player");
        @Nullable final Session session = sessions.getIfPresent(new CacheKey(player));
        if (session != null) {
            session.resetState(player);
        }
    }

    @Override
    @Nullable
    public Session getIfPresent(final LocalPlayer player) {
        return sessions.getIfPresent(new CacheKey(player));
    }

    @Override
    public Session get(final LocalPlayer player) {
        return sessions.getUnchecked(new CacheKey(player));
    }

    @Override
    public Session createSession(final LocalPlayer player) {
        final Session session = new Session(this);
        for (final Handler.Factory<? extends Handler> factory : handlers) {
            session.register(factory.create(session));
        }
        session.initialize(player);
        return session;
    }

    protected static final class CacheKey {
        final WeakReference<LocalPlayer> playerRef;
        final UUID uuid;

        CacheKey(final LocalPlayer player) {
            playerRef = new WeakReference<>(player);
            uuid      = player.getUniqueId();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final CacheKey cacheKey = (CacheKey) o;
            return uuid.equals(cacheKey.uuid);

        }

        @Override
        public int hashCode() {
            return uuid.hashCode();
        }
    }
}
