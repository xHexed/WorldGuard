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
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.util.formatting.component.Notify;

public class NotifyExitFlag extends FlagValueChangeHandler<Boolean> {

    public static final Factory FACTORY = new Factory();
    private final Boolean notifiedForLeave = false;

    public NotifyExitFlag(final Session session) {
        super(session, Flags.NOTIFY_LEAVE);
    }

    @Override
    protected void onInitialValue(final LocalPlayer player, final ApplicableRegionSet set, final Boolean value) {

    }

    @Override
    protected boolean onSetValue(final LocalPlayer player, final Location from, final Location to, final ApplicableRegionSet toSet, final Boolean currentValue, final Boolean lastValue, final MoveType moveType) {
        return true;
    }

    @Override
    protected boolean onAbsentValue(final LocalPlayer player, final Location from, final Location to, final ApplicableRegionSet toSet, final Boolean lastValue, final MoveType moveType) {
        WorldGuard.getInstance().getPlatform().broadcastNotification(new Notify(player.getName(), " left NOTIFY region").create());
        return true;
    }

    public static class Factory extends Handler.Factory<NotifyExitFlag> {
        @Override
        public NotifyExitFlag create(final Session session) {
            return new NotifyExitFlag(session);
        }
    }
}
