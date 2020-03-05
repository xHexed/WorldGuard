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

package com.sk89q.worldguard.bukkit;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.weather.WeatherType;
import com.sk89q.worldedit.world.weather.WeatherTypes;
import com.sk89q.worldguard.LocalPlayer;
import org.bukkit.BanList.Type;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class BukkitPlayer extends com.sk89q.worldedit.bukkit.BukkitPlayer implements LocalPlayer {

    protected final WorldGuardPlugin plugin;
    private final boolean silenced;
    private String name;

    public BukkitPlayer(final WorldGuardPlugin plugin, final Player player) {
        this(plugin, player, false);
    }

    BukkitPlayer(final WorldGuardPlugin plugin, final Player player, final boolean silenced) {
        super(player);
        this.plugin   = plugin;
        this.silenced = silenced;
    }

    @Override
    public String getName() {
        if (name == null) {
            // getName() takes longer than before in newer versions of Minecraft
            name = getPlayer().getName();
        }
        return name;
    }

    @Override
    public boolean hasGroup(final String group) {
        return plugin.inGroup(getPlayer(), group);
    }

    @Override
    public void kick(final String msg) {
        if (!silenced) {
            getPlayer().kickPlayer(msg);
        }
    }

    @Override
    public void ban(final String msg) {
        if (!silenced) {
            Bukkit.getBanList(Type.NAME).addBan(getName(), null, null, null);
            getPlayer().kickPlayer(msg);
        }
    }

    @Override
    public double getHealth() {
        return getPlayer().getHealth();
    }

    @Override
    public void setHealth(final double health) {
        getPlayer().setHealth(health);
    }

    @Override
    public double getMaxHealth() {
        return getPlayer().getMaxHealth();
    }

    @Override
    public double getFoodLevel() {
        return getPlayer().getFoodLevel();
    }

    @Override
    public void setFoodLevel(final double foodLevel) {
        getPlayer().setFoodLevel((int) foodLevel);
    }

    @Override
    public double getSaturation() {
        return getPlayer().getSaturation();
    }

    @Override
    public void setSaturation(final double saturation) {
        getPlayer().setSaturation((float) saturation);
    }

    @Override
    public float getExhaustion() {
        return getPlayer().getExhaustion();
    }

    @Override
    public void setExhaustion(final float exhaustion) {
        getPlayer().setExhaustion(exhaustion);
    }

    @Override
    public WeatherType getPlayerWeather() {
        return null;
    }

    @Override
    public void setPlayerWeather(final WeatherType weather) {
        getPlayer().setPlayerWeather(weather == WeatherTypes.CLEAR ? org.bukkit.WeatherType.CLEAR : org.bukkit.WeatherType.DOWNFALL);
    }

    @Override
    public void resetPlayerWeather() {
        getPlayer().resetPlayerWeather();
    }

    @Override
    public boolean isPlayerTimeRelative() {
        return getPlayer().isPlayerTimeRelative();
    }

    @Override
    public long getPlayerTimeOffset() {
        return getPlayer().getPlayerTimeOffset();
    }

    @Override
    public void setPlayerTime(final long time, final boolean relative) {
        getPlayer().setPlayerTime(time, relative);
    }

    @Override
    public void resetPlayerTime() {
        getPlayer().resetPlayerTime();
    }

    @Override
    public int getFireTicks() {
        return getPlayer().getFireTicks();
    }

    @Override
    public void setFireTicks(final int fireTicks) {
        getPlayer().setFireTicks(fireTicks);
    }

    @Override
    public void setCompassTarget(final Location location) {
        getPlayer().setCompassTarget(BukkitAdapter.adapt(location));
    }

    @Override
    public void sendTitle(final String title, final String subtitle) {
        getPlayer().sendTitle(title, subtitle, -1, -1, -1);
    }

    @Override
    public String[] getGroups() {
        return plugin.getGroups(getPlayer());
    }

    @Override
    public void printRaw(final String msg) {
        if (!silenced) {
            super.printRaw(msg);
        }
    }

    @Override
    public boolean hasPermission(final String perm) {
        return plugin.hasPermission(getPlayer(), perm);
    }
}
