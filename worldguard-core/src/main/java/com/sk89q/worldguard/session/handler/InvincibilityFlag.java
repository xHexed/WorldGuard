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
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;

import javax.annotation.Nullable;

public class InvincibilityFlag extends FlagValueChangeHandler<State> {

    public static final Factory FACTORY = new Factory();

    public InvincibilityFlag(final Session session) {
        super(session, Flags.INVINCIBILITY);
    }

    @Nullable
    private State invincibility;

    @Override
    protected void onInitialValue(final LocalPlayer player, final ApplicableRegionSet set, final State value) {
        invincibility = value;
    }

    @Override
    protected boolean onSetValue(final LocalPlayer player, final Location from, final Location to, final ApplicableRegionSet toSet, final State currentValue, final State lastValue, final MoveType moveType) {
        invincibility = currentValue;
        return true;
    }

    @Override
    protected boolean onAbsentValue(final LocalPlayer player, final Location from, final Location to, final ApplicableRegionSet toSet, final State lastValue, final MoveType moveType) {
        invincibility = null;
        return true;
    }

    @Override
    @Nullable
    public State getInvincibility(final LocalPlayer player) {
        if (invincibility == State.DENY && player.hasPermission("worldguard.god.override-regions")) {
            return null;
        }

        return invincibility;
    }

    public static class Factory extends Handler.Factory<InvincibilityFlag> {
        @Override
        public InvincibilityFlag create(final Session session) {
            return new InvincibilityFlag(session);
        }
    }

}
