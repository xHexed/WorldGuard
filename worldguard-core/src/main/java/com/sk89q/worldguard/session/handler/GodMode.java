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

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.session.Session;

import javax.annotation.Nullable;

public class GodMode extends Handler {

    public static final Factory FACTORY = new Factory();

    public GodMode(final Session session) {
        super(session);
    }

    private boolean godMode;

    public static boolean set(final LocalPlayer player, final Session session, final boolean value) {
        final GodMode godMode = session.getHandler(GodMode.class);
        if (godMode != null) {
            godMode.setGodMode(player, value);
            return true;
        }
        else {
            return false;
        }
    }

    public boolean hasGodMode(final LocalPlayer player) {
        // TODO
//        if (getPlugin().getGlobalStateManager().hasCommandBookGodMode()) {
//            GodComponent god = CommandBook.inst().getComponentManager().getComponent(GodComponent.class);
//            if (god != null) {
//                return god.hasGodMode(player);
//            }
//        }

        return godMode;
    }

    public void setGodMode(final LocalPlayer player, final boolean godMode) {
//        if (getPlugin().getGlobalStateManager().hasCommandBookGodMode()) {
//            GodComponent god = CommandBook.inst().getComponentManager().getComponent(GodComponent.class);
//            if (god != null) {
//                god.enableGodMode(player);
//            }
//        }

        this.godMode = godMode;
    }

    @Nullable
    @Override
    public State getInvincibility(final LocalPlayer player) {
        return hasGodMode(player) ? State.ALLOW : null;
    }

    public static class Factory extends Handler.Factory<GodMode> {
        @Override
        public GodMode create(final Session session) {
            return new GodMode(session);
        }
    }

}
