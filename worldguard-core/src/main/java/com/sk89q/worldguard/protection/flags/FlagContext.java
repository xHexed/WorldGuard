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

import com.google.common.collect.Maps;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;

import javax.annotation.Nullable;
import java.util.Map;

public final class FlagContext {

    private final Actor sender;
    private final String input;

    private final Map<String, Object> context;

    private FlagContext(final Actor sender, final String input, final Map<String, Object> values) {
        this.sender = sender;
        this.input  = input;
        context     = values;
    }

    public static FlagContextBuilder create() {
        return new FlagContextBuilder();
    }

    public void put(final String name, final Object value) {
        context.put(name, value);
    }

    public Actor getSender() {
        return sender;
    }

    public String getUserInput() {
        return input;
    }

    /**
     * Gets the CommandSender as a player.
     *
     * @return Player
     * @throws InvalidFlagFormat if the sender is not a player
     */
    public LocalPlayer getPlayerSender() throws InvalidFlagFormat {
        if (sender.isPlayer() && sender instanceof LocalPlayer) {
            return (LocalPlayer) sender;
        } else {
            throw new InvalidFlagFormat("Not a player");
        }
    }

    public Integer getUserInputAsInt() throws InvalidFlagFormat {
        try {
            return Integer.parseInt(input);
        }
        catch (final NumberFormatException e) {
            throw new InvalidFlagFormat("Not a number: " + input);
        }
    }

    public Double getUserInputAsDouble() throws InvalidFlagFormat {
        try {
            return Double.parseDouble(input);
        }
        catch (final NumberFormatException e) {
            throw new InvalidFlagFormat("Not a number: " + input);
        }
    }

    /**
     * Get an object from the context by key name.
     * May return null if the object does not exist in the context.
     *
     * @param name key name of the object
     *
     * @return the object matching the key, or null
     */
    @Nullable
    public Object get(final String name) {
        return get(name, null);
    }

    /**
     * Get an object from the context by key name.
     * Will only return null if
     * a) you provide null as the default
     * b) the key has explicity been set to null
     *
     * @param name key name of the object
     *
     * @return the object matching the key
     */
    @Nullable
    public Object get(final String name, final Object defaultValue) {
        final Object obj;
        return (((obj = context.get(name)) != null) || context.containsKey(name)
                ? obj : defaultValue);
    }

    /**
     * Create a copy of this FlagContext, with optional substitutions for values
     *
     * If any supplied variable is null, it will be ignored.
     * If a map is supplied, it will override this FlagContext's values of the same key,
     * but unprovided keys will not be overriden and will be returned as shallow copies.
     *
     * @param commandSender CommandSender for the new FlagContext to run under
     * @param s String of the user input for the new FlagContext
     * @param values map of values to override from the current FlagContext
     * @return a copy of this FlagContext
     */
    public FlagContext copyWith(@Nullable final Actor commandSender, @Nullable final String s, @Nullable final Map<String, Object> values) {
        final Map<String, Object> map = Maps.newHashMap();
        map.putAll(context);
        if (values != null) {
            map.putAll(values);
        }
        return new FlagContext(commandSender == null ? sender : commandSender, s == null ? input : s, map);
    }

    public static class FlagContextBuilder {
        private Actor sender;
        private String input;
        private final Map<String, Object> map = Maps.newHashMap();

        public FlagContextBuilder setSender(final Actor sender) {
            this.sender = sender;
            return this;
        }

        public FlagContextBuilder setInput(final String input) {
            this.input = input;
            return this;
        }

        public FlagContextBuilder setObject(final String key, final Object value) {
            map.put(key, value);
            return this;
        }

        public boolean tryAddToMap(final String key, final Object value) {
            if (map.containsKey(key)) return false;
            map.put(key, value);
            return true;
        }

        public FlagContext build() {
            WorldGuard.getInstance().getPlatform().notifyFlagContextCreate(this);

            return new FlagContext(sender, input, map);
        }
    }

}
