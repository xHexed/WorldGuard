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

package com.sk89q.worldguard.protection.flags;

import com.sk89q.worldedit.world.weather.WeatherType;
import com.sk89q.worldedit.world.weather.WeatherTypes;

import javax.annotation.Nullable;

public class WeatherTypeFlag extends Flag<WeatherType> {

    protected WeatherTypeFlag(final String name, @Nullable final RegionGroup defaultGroup) {
        super(name, defaultGroup);
    }

    protected WeatherTypeFlag(final String name) {
        super(name);
    }

    @Override
    public WeatherType parseInput(final FlagContext context) throws InvalidFlagFormat {
        String input = context.getUserInput();
        input = input.trim();
        final WeatherType weatherType = unmarshal(input);
        if (weatherType == null) {
            throw new InvalidFlagFormat("Unknown weather type: " + input);
        }
        return weatherType;
    }

    @Override
    public WeatherType unmarshal(@Nullable final Object o) {
        return WeatherTypes.get(String.valueOf(o).toLowerCase());
    }

    @Override
    public Object marshal(final WeatherType o) {
        return o.getId();
    }
}
