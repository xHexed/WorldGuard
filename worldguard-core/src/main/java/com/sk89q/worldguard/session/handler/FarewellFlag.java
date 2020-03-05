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

import com.google.common.collect.Sets;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.util.MessagingUtil;

import java.util.Collections;
import java.util.Set;
import java.util.function.BiConsumer;

public class FarewellFlag extends Handler {

    public static final Factory FACTORY = new Factory();

    public FarewellFlag(final Session session) {
        super(session);
    }

    private Set<String> lastMessageStack = Collections.emptySet();
    private Set<String> lastTitleStack = Collections.emptySet();

    private Set<String> getMessages(final LocalPlayer player, final ApplicableRegionSet set, final Flag<String> flag) {
        return Sets.newLinkedHashSet(set.queryAllValues(player, flag));
    }

    @Override
    public void initialize(final LocalPlayer player, final Location current, final ApplicableRegionSet set) {
        lastMessageStack = getMessages(player, set, Flags.FAREWELL_MESSAGE);
        lastTitleStack   = getMessages(player, set, Flags.FAREWELL_TITLE);
    }

    @Override
    public boolean onCrossBoundary(final LocalPlayer player, final Location from, final Location to, final ApplicableRegionSet toSet,
                                   final Set<ProtectedRegion> entered, final Set<ProtectedRegion> exited, final MoveType moveType) {

        lastMessageStack = collectAndSend(player, toSet, Flags.FAREWELL_MESSAGE, lastMessageStack, MessagingUtil::sendStringToChat);
        lastTitleStack   = collectAndSend(player, toSet, Flags.FAREWELL_TITLE, lastTitleStack, MessagingUtil::sendStringToTitle);

        return true;
    }

    private Set<String> collectAndSend(final LocalPlayer player, final ApplicableRegionSet toSet, final Flag<String> flag,
                                       final Set<String> stack, final BiConsumer<LocalPlayer, String> msgFunc) {
        final Set<String> messages = getMessages(player, toSet, flag);

        if (!messages.isEmpty()) {
            // Due to flag priorities, we have to collect the lower
            // priority flag values separately
            for (final ProtectedRegion region : toSet) {
                final String message = region.getFlag(flag);
                if (message != null) {
                    messages.add(message);
                }
            }
        }

        for (final String message : stack) {
            if (!messages.contains(message)) {
                msgFunc.accept(player, message);
                break;
            }
        }
        return messages;
    }

    public static class Factory extends Handler.Factory<FarewellFlag> {
        @Override
        public FarewellFlag create(final Session session) {
            return new FarewellFlag(session);
        }
    }
}
