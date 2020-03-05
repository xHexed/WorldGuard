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

package com.sk89q.worldguard.util.profiler;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StackNode implements Comparable<StackNode> {

    private final String name;
    private final Map<String, StackNode> children = Maps.newHashMap();
    private long totalTime;

    public StackNode(final String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }

    public Collection<StackNode> getChildren() {
        final List<StackNode> list = Lists.newArrayList(children.values());
        Collections.sort(list);
        return list;
    }

    public StackNode getChild(final String name) {
        StackNode child = children.get(name);
        if (child == null) {
            child = new StackNode(name);
            children.put(name, child);
        }
        return child;
    }

    public StackNode getChild(final String className, final String methodName) {
        final StackTraceNode node = new StackTraceNode(className, methodName);
        StackNode child = children.get(node.getName());
        if (child == null) {
            child = node;
            children.put(node.getName(), node);
        }
        return child;
    }

    public long getTotalTime() {
        return totalTime;
    }

    public void log(final long time) {
        totalTime += time;
    }

    private void log(final StackTraceElement[] elements, final int skip, final long time) {
        log(time);

        if (elements.length - skip == 0) {
            return;
        }

        final StackTraceElement bottom = elements[elements.length - (skip + 1)];
        getChild(bottom.getClassName(), bottom.getMethodName())
                .log(elements, skip + 1, time);
    }

    public void log(final StackTraceElement[] elements, final long time) {
        log(elements, 0, time);
    }

    @Override
    public int compareTo(final StackNode o) {
        return name.compareTo(o.name);
    }

    void writeString(final StringBuilder builder, final int indent) {
        final StringBuilder b = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            b.append(" ");
        }
        final String padding = b.toString();

        for (final StackNode child : getChildren()) {
            builder.append(padding).append(child.name);
            builder.append(" ");
            builder.append(child.totalTime).append("ms");
            builder.append("\n");
            child.writeString(builder, indent + 1);
        }
    }
    
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        writeString(builder, 0);
        return builder.toString();
    }

}
