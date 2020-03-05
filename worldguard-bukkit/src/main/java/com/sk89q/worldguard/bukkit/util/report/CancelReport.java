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

package com.sk89q.worldguard.bukkit.util.report;

import com.sk89q.worldedit.util.report.Report;
import com.sk89q.worldedit.util.report.StackTraceReport;
import com.sk89q.worldguard.bukkit.event.debug.CancelAttempt;
import com.sk89q.worldguard.bukkit.util.HandlerTracer;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Reports on cancelled events.
 */
public class CancelReport implements Report {

    private final Event event;
    private final Cancellable cancellable;
    private final List<CancelAttempt> cancels;
    private final HandlerTracer tracer;
    private final int stackTruncateLength;
    private boolean detectingPlugin = true;

    public <T extends Event & Cancellable> CancelReport(final T event, final List<CancelAttempt> cancels, final int stackTruncateLength) {
        checkNotNull(event, "event");
        checkNotNull(cancels, "cancels");
        this.event               = event;
        cancellable              = event;
        this.cancels             = cancels;
        tracer                   = new HandlerTracer(event);
        this.stackTruncateLength = stackTruncateLength;
    }

    public boolean isDetectingPlugin() {
        return detectingPlugin;
    }

    private static String getCancelText(final boolean flag) {
        return flag ? "BLOCKED" : "ALLOWED";
    }

    public void setDetectingPlugin(final boolean detectingPlugin) {
        this.detectingPlugin = detectingPlugin;
    }

    @Override
    public String getTitle() {
        return null;
    }

    private StackTraceElement[] truncateStackTrace(final StackTraceElement[] elements) {
        final int newLength = elements.length - stackTruncateLength;
        if (newLength <= 0) {
            return new StackTraceElement[0];
        }
        else {
            return Arrays.copyOf(elements, newLength);
        }
    }

    @Override
    public String toString() {
        if (!cancels.isEmpty()) {
            final StringBuilder builder = new StringBuilder();

            builder.append("Was the action blocked? ").append(cancellable.isCancelled() ? "YES" : "NO").append("\n");

            if (cancels.size() != 1) {
                builder.append("Entry #1 had the last word.\n");
            }

            for (int i = cancels.size() - 1; i >= 0; i--) {
                final CancelAttempt cancel = cancels.get(i);
                final int index = cancels.size() - i;

                final StackTraceElement[] stackTrace = truncateStackTrace(cancel.getStackTrace());
                final Plugin cause = tracer.detectPlugin(stackTrace);

                builder.append("#").append(index).append(" ");
                builder.append(getCancelText(cancel.getAfter()));
                builder.append(" by ");

                if (detectingPlugin && cause != null) {
                    builder.append(cause.getName());
                }
                else {
                    builder.append(" (NOT KNOWN - use the stack trace below)");
                    builder.append("\n");
                    builder.append(new StackTraceReport(stackTrace).toString().replaceAll("(?m)^", "\t"));
                }

                builder.append("\n");
            }

            return builder.toString();
        } else {
            return "No plugins cancelled the event. Other causes for cancellation: " +
                    "(1) Bukkit may be using a different event for the action " +
                    " (example: buckets have their own bucket events); or " +
                    "(2) Minecraft's spawn protection has not been disabled.";
        }
    }

}
