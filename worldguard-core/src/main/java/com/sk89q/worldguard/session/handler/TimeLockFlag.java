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
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

public class TimeLockFlag extends FlagValueChangeHandler<String> {

    public static final Factory FACTORY = new Factory();
    private static final Pattern timePattern = Pattern.compile("([+\\-])?\\d+");

    private Long initialTime;
    private boolean initialRelative;

    public TimeLockFlag(final Session session) {
        super(session, Flags.TIME_LOCK);
    }

    private void updatePlayerTime(final LocalPlayer player, @Nullable final String value) {
        if (value == null || !timePattern.matcher(value).matches()) {
            // invalid input
            return;
        }
        final boolean relative = value.startsWith("+") || value.startsWith("-");
        final long time = Long.parseLong(value);
//        if (!relative && (time < 0L || time > 24000L)) { // invalid time, reset to 0
//            time = 0L;
//        }
        player.setPlayerTime(time, relative);
    }

    @Override
    protected void onInitialValue(final LocalPlayer player, final ApplicableRegionSet set, final String value) {
        initialRelative = player.isPlayerTimeRelative();
        initialTime     = player.getPlayerTimeOffset();
        updatePlayerTime(player, value);
    }

    @Override
    protected boolean onSetValue(final LocalPlayer player, final Location from, final Location to, final ApplicableRegionSet toSet, final String currentValue, final String lastValue, final MoveType moveType) {
        updatePlayerTime(player, currentValue);
        return true;
    }

    @Override
    protected boolean onAbsentValue(final LocalPlayer player, final Location from, final Location to, final ApplicableRegionSet toSet, final String lastValue, final MoveType moveType) {
        player.setPlayerTime(initialTime, initialRelative);
        initialRelative = true;
        initialTime     = 0L;
        return true;
    }

    public static class Factory extends Handler.Factory<TimeLockFlag> {
        @Override
        public TimeLockFlag create(final Session session) {
            return new TimeLockFlag(session);
        }
    }

}
