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
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.gamemode.GameMode;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;

import javax.annotation.Nullable;

public class GameModeFlag extends FlagValueChangeHandler<GameMode> {

    public static final Factory FACTORY = new Factory();

    public GameModeFlag(final Session session) {
        super(session, Flags.GAME_MODE);
    }

    private GameMode originalGameMode;
    private GameMode setGameMode;

    private void updateGameMode(final LocalPlayer player, @Nullable final GameMode newValue, final World world) {
        if (!getSession().getManager().hasBypass(player, world) && newValue != null) {
            if (player.getGameMode() != newValue) {
                originalGameMode = player.getGameMode();
                player.setGameMode(newValue);
            }
            else if (originalGameMode == null) {
                originalGameMode = WorldGuard.getInstance().getPlatform().getDefaultGameMode();
            }
        }
        else {
            if (originalGameMode != null) {
                final GameMode mode = originalGameMode;
                originalGameMode = null;
                player.setGameMode(mode);
            }
        }
    }

    public GameMode getOriginalGameMode() {
        return originalGameMode;
    }

    public GameMode getSetGameMode() {
        return setGameMode;
    }

    @Override
    protected void onInitialValue(final LocalPlayer player, final ApplicableRegionSet set, final GameMode value) {
        updateGameMode(player, value, player.getWorld());
    }

    @Override
    protected boolean onSetValue(final LocalPlayer player, final Location from, final Location to, final ApplicableRegionSet toSet, final GameMode currentValue, final GameMode lastValue, final MoveType moveType) {
        updateGameMode(player, currentValue, (World) to.getExtent());
        return true;
    }

    @Override
    protected boolean onAbsentValue(final LocalPlayer player, final Location from, final Location to, final ApplicableRegionSet toSet, final GameMode lastValue, final MoveType moveType) {
        updateGameMode(player, null, (World) player.getExtent());
        return true;
    }

    public static class Factory extends Handler.Factory<GameModeFlag> {
        @Override
        public GameModeFlag create(final Session session) {
            return new GameModeFlag(session);
        }
    }

}
