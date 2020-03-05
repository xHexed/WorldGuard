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

package com.sk89q.worldguard.session.handler;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.SessionManager;

import javax.annotation.Nullable;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Stores session data and possibly acts on it.
 */
public abstract class Handler {

    public abstract static class Factory<T extends Handler> {
        public abstract T create(Session session);
    }

    private final Session session;

    /**
     * Create a new handler.
     *
     * @param session The session
     */
    protected Handler(final Session session) {
        checkNotNull(session, "session");
        this.session = session;
    }

    /**
     * Get the session.
     *
     * @return The session
     */
    public Session getSession() {
        return session;
    }

    /**
     * Called when the session is first being created or
     * {@code /wg flushstates} is used.
     *
     * @param player  The player
     * @param current The player's current location
     * @param set     The regions for the current location
     */
    public void initialize(final LocalPlayer player, final Location current, final ApplicableRegionSet set) {
    }

    /**
     * Called when {@link Session#testMoveTo(LocalPlayer, Location, MoveType)} is called.
     *
     * <p>If this method returns {@code false}, then no other handlers
     * will be run (for this move attempt).</p>
     *
     * @param player The player
     * @param from The previous, valid, location
     * @param to The new location to test
     * @param toSet The regions for the new location
     * @param moveType The type of movement
     * @return Whether the movement should be allowed
     */
    public boolean testMoveTo(final LocalPlayer player, final Location from, final Location to, final ApplicableRegionSet toSet, final MoveType moveType) {
        return true;
    }

    /**
     * Called when a player has moved into a new location.
     *
     * <p>This is called only if the move test
     * ({@link Session#testMoveTo(LocalPlayer, Location, MoveType)}) was successful.</p>
     *
     * <p>If this method returns {@code false}, then no other handlers
     * will be run (for this move attempt).</p>
     *
     * @param player The player
     * @param from The previous, valid, location
     * @param to The new location to test
     * @param toSet The regions for the new location
     * @param entered The list of regions that have been entered
     * @param exited The list of regions that have been left
     * @param moveType The type of move
     * @return Whether the movement should be allowed
     */
    public boolean onCrossBoundary(final LocalPlayer player, final Location from, final Location to, final ApplicableRegionSet toSet,
                                   final Set<ProtectedRegion> entered, final Set<ProtectedRegion> exited, final MoveType moveType) {
        return true;
    }

    /**
     * Called periodically (at least once every second) by
     * {@link SessionManager} in the server's main thread.
     *
     * @param player The player
     * @param set    The regions for the player's current location
     */
    public void tick(final LocalPlayer player, final ApplicableRegionSet set) {
    }

    /**
     * Return whether the player should be invincible.
     *
     * <p>{@link State#DENY} can be returned to prevent invincibility
     * even if another handler permits it.</p>
     *
     * @param player The player
     *
     * @return Invincibility state
     */
    @Nullable
    public State getInvincibility(final LocalPlayer player) {
        return null;
    }

}
