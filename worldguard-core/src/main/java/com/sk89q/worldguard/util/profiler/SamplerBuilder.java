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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class SamplerBuilder {

    private static final Timer timer = new Timer("WorldGuard Sampler", true);
    private int interval = 100;
    private long runTime = TimeUnit.MINUTES.toMillis(5);
    private Predicate<ThreadInfo> threadFilter = thread -> true;

    public int getInterval() {
        return interval;
    }

    public void setInterval(final int interval) {
        checkArgument(interval >= 1, "interval >= 1");
        this.interval = interval;
    }

    public Predicate<ThreadInfo> getThreadFilter() {
        return threadFilter;
    }

    public void setThreadFilter(final Predicate<ThreadInfo> threadFilter) {
        checkNotNull(threadFilter, "threadFilter");
        this.threadFilter = threadFilter;
    }

    public long getRunTime(final TimeUnit timeUnit) {
        return timeUnit.convert(runTime, TimeUnit.MILLISECONDS);
    }

    public void setRunTime(final long time, final TimeUnit timeUnit) {
        checkArgument(time > 0, "time > 0");
        runTime = timeUnit.toMillis(time);
    }

    public Sampler start() {
        final Sampler sampler = new Sampler(interval, threadFilter, System.currentTimeMillis() + runTime);
        timer.scheduleAtFixedRate(sampler, 0, interval);
        return sampler;
    }

    public static class Sampler extends TimerTask {
        private final int interval;
        private final Predicate<ThreadInfo> threadFilter;
        private final long endTime;

        private final SortedMap<String, StackNode> nodes = new TreeMap<>();
        private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        private final SettableFuture<Sampler> future = SettableFuture.create();

        private Sampler(final int interval, final Predicate<ThreadInfo> threadFilter, final long endTime) {
            this.interval     = interval;
            this.threadFilter = threadFilter;
            this.endTime      = endTime;
        }

        public ListenableFuture<Sampler> getFuture() {
            return future;
        }

        private Map<String, StackNode> getData() {
            return nodes;
        }

        private StackNode getNode(final String name) {
            StackNode node = nodes.get(name);
            if (node == null) {
                node = new StackNode(name);
                nodes.put(name, node);
            }
            return node;
        }

        @Override
        public boolean cancel() {
            future.setException(new CancellationException());
            return super.cancel();
        }

        @Override
        public synchronized void run() {
            try {
                if (endTime <= System.currentTimeMillis()) {
                    future.set(this);
                    cancel();
                    return;
                }

                final ThreadInfo[] threadDumps = threadBean.dumpAllThreads(false, false);
                for (final ThreadInfo threadInfo : threadDumps) {
                    final String threadName = threadInfo.getThreadName();
                    final StackTraceElement[] stack = threadInfo.getStackTrace();

                    if (threadName != null && stack != null && threadFilter.test(threadInfo)) {
                        final StackNode node = getNode(threadName);
                        node.log(stack, interval);
                    }
                }
            }
            catch (final Throwable t) {
                future.setException(t);
                super.cancel();
            }
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            for (final Map.Entry<String, StackNode> entry : getData().entrySet()) {
                builder.append(entry.getKey());
                builder.append(" ");
                builder.append(entry.getValue().getTotalTime()).append("ms");
                builder.append("\n");
                entry.getValue().writeString(builder, 1);
            }
            return builder.toString();
        }
    }

}
