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

package com.sk89q.worldguard.protection.managers.storage.sql;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.util.io.Closer;
import com.sk89q.worldguard.util.sql.DataSourceConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class RegionRemover {

    private final DataSourceConfig config;
    private final Connection conn;
    private final int worldId;
    private final List<String> regionQueue = new ArrayList<>();
    private final List<String> cuboidGeometryQueue = new ArrayList<>();
    private final List<String> polygonGeometryQueue = new ArrayList<>();

    RegionRemover(final DataUpdater updater) {
        config  = updater.config;
        conn    = updater.conn;
        worldId = updater.worldId;
    }

    public void removeRegionsEntirely(final Collection<String> names) {
        regionQueue.addAll(names);
    }

    public void removeGeometry(final ProtectedRegion region, final String currentType) {
        switch (currentType) {
            case "cuboid":
                cuboidGeometryQueue.add(region.getId());
                break;
            case "poly2d":
                polygonGeometryQueue.add(region.getId());
                break;
            case "global":
                // Nothing to do
                break;
            default:
                throw new RuntimeException("Unknown type of region in the database: " + currentType);
        }

    }

    private void removeRows(final Collection<String> names, final String table, final String field) throws SQLException {
        final Closer closer = Closer.create();
        try {
            final PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "DELETE FROM " + config.getTablePrefix() + table + " WHERE " + field + " = ? AND world_id = " + worldId));

            final StatementBatch batch = new StatementBatch(stmt, StatementBatch.MAX_BATCH_SIZE);
            for (final String name : names) {
                stmt.setString(1, name);
                batch.addBatch();
            }

            batch.executeRemaining();
        } finally {
            closer.closeQuietly();
        }
    }

    public void apply() throws SQLException {
        removeRows(regionQueue, "region", "id");
        removeRows(cuboidGeometryQueue, "region_cuboid", "region_id");
        removeRows(polygonGeometryQueue, "region_poly2d", "region_id");
    }
}
