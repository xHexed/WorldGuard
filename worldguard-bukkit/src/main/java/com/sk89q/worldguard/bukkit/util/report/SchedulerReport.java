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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.reflect.TypeToken;
import com.sk89q.worldedit.util.report.DataReport;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SchedulerReport extends DataReport {

    private final LoadingCache<Class<?>, Optional<Field>> taskFieldCache = CacheBuilder.newBuilder()
            .build(new CacheLoader<Class<?>, Optional<Field>>() {
                @Override
                public Optional<Field> load(final Class<?> clazz) {
                    try {
                        final Field field = clazz.getDeclaredField("task");
                        field.setAccessible(true);
                        return Optional.of(field);
                    }
                    catch (final NoSuchFieldException ignored) {
                        return Optional.empty();
                    }
                }
            });

    public SchedulerReport() {
        super("Scheduler");

        final List<BukkitTask> tasks = Bukkit.getServer().getScheduler().getPendingTasks();

        append("Pending Task Count", tasks.size());

        for (final BukkitTask task : tasks) {
            final Class<?> taskClass = getTaskClass(task);

            final DataReport report = new DataReport("Task: #" + task.getTaskId());
            report.append("Owner", task.getOwner().getName());
            report.append("Runnable", taskClass != null ? taskClass.getName() : "<Unknown>");
            report.append("Synchronous?", task.isSync());
            append(report.getTitle(), report);
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private Class<?> getTaskClass(final BukkitTask task) {
        try {
            final Class<?> clazz = task.getClass();
            final Set<Class<?>> classes = (Set) TypeToken.of(clazz).getTypes().rawTypes();

            for (final Class<?> type : classes) {
                final Optional<Field> field = taskFieldCache.getUnchecked(type);
                if (field.isPresent()) {
                    final Object res = field.get().get(task);
                    return res == null ? null : res.getClass();
                }
            }
        }
        catch (final IllegalAccessException | NoClassDefFoundError ignored) {
        }

        return null;
    }
}
