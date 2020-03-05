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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Table;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.storage.RegionDatabaseUtils;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.util.io.Closer;
import com.sk89q.worldguard.util.sql.DataSourceConfig;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

class DataLoader {

    private static final Logger log = Logger.getLogger(DataLoader.class.getCanonicalName());

    final Connection conn;
    final DataSourceConfig config;
    final int worldId;
    final FlagRegistry flagRegistry;

    private final Map<String, ProtectedRegion> loaded = new HashMap<>();
    private final Map<ProtectedRegion, String> parentSets = new HashMap<>();
    private final Yaml yaml = SQLRegionDatabase.createYaml();

    DataLoader(final SQLRegionDatabase regionStore, final Connection conn, final FlagRegistry flagRegistry) {
        checkNotNull(regionStore);

        this.conn         = conn;
        config            = regionStore.getDataSourceConfig();
        worldId           = regionStore.getWorldId();
        this.flagRegistry = flagRegistry;
    }

    public Set<ProtectedRegion> load() throws SQLException {
        loadCuboids();
        loadPolygons();
        loadGlobals();

        loadFlags();
        loadDomainUsers();
        loadDomainGroups();

        RegionDatabaseUtils.relinkParents(loaded, parentSets);

        return new HashSet<>(loaded.values());
    }

    private void loadCuboids() throws SQLException {
        final Closer closer = Closer.create();
        try {
            final PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "SELECT g.min_z, g.min_y, g.min_x, " +
                            "       g.max_z, g.max_y, g.max_x, " +
                            "       r.id, r.priority, p.id AS parent " +
                            "FROM " + config.getTablePrefix() + "region_cuboid AS g " +
                            "LEFT JOIN " + config.getTablePrefix() + "region AS r " +
                            "          ON (g.region_id = r.id AND g.world_id = r.world_id) " +
                            "LEFT JOIN " + config.getTablePrefix() + "region AS p " +
                            "          ON (r.parent = p.id AND r.world_id = p.world_id) " +
                            "WHERE r.world_id = " + worldId));

            final ResultSet rs = closer.register(stmt.executeQuery());

            while (rs.next()) {
                final BlockVector3 pt1 = BlockVector3.at(rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"));
                final BlockVector3 pt2 = BlockVector3.at(rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z"));

                final BlockVector3 min = pt1.getMinimum(pt2);
                final BlockVector3 max = pt1.getMaximum(pt2);
                final ProtectedRegion region = new ProtectedCuboidRegion(rs.getString("id"), min, max);

                region.setPriority(rs.getInt("priority"));

                loaded.put(rs.getString("id"), region);

                final String parentId = rs.getString("parent");
                if (parentId != null) {
                    parentSets.put(region, parentId);
                }
            }
        } finally {
            closer.closeQuietly();
        }
    }

    private void loadGlobals() throws SQLException {
        final Closer closer = Closer.create();
        try {
            final PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "SELECT r.id, r.priority, p.id AS parent " +
                            "FROM " + config.getTablePrefix() + "region AS r " +
                            "LEFT JOIN " + config.getTablePrefix() + "region AS p " +
                            "          ON (r.parent = p.id AND r.world_id = p.world_id) " +
                            "WHERE r.type = 'global' AND r.world_id = " + worldId));

            final ResultSet rs = closer.register(stmt.executeQuery());

            while (rs.next()) {
                final ProtectedRegion region = new GlobalProtectedRegion(rs.getString("id"));

                region.setPriority(rs.getInt("priority"));

                loaded.put(rs.getString("id"), region);

                final String parentId = rs.getString("parent");
                if (parentId != null) {
                    parentSets.put(region, parentId);
                }
            }
        } finally {
            closer.closeQuietly();
        }
    }

    private void loadPolygons() throws SQLException {
        final ListMultimap<String, BlockVector2> pointsCache = ArrayListMultimap.create();

        // First get all the vertices and store them in memory
        Closer closer = Closer.create();
        try {
            final PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "SELECT region_id, x, z " +
                            "FROM " + config.getTablePrefix() + "region_poly2d_point " +
                            "WHERE world_id = " + worldId));

            final ResultSet rs = closer.register(stmt.executeQuery());

            while (rs.next()) {
                pointsCache.put(rs.getString("region_id"), BlockVector2.at(rs.getInt("x"), rs.getInt("z")));
            }
        } finally {
            closer.closeQuietly();
        }

        // Now we pull the regions themselves
        closer = Closer.create();
        try {
            final PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "SELECT g.min_y, g.max_y, r.id, r.priority, p.id AS parent " +
                            "FROM " + config.getTablePrefix() + "region_poly2d AS g " +
                            "LEFT JOIN " + config.getTablePrefix() + "region AS r " +
                            "          ON (g.region_id = r.id AND g.world_id = r.world_id) " +
                            "LEFT JOIN " + config.getTablePrefix() + "region AS p " +
                            "          ON (r.parent = p.id AND r.world_id = p.world_id) " +
                            "WHERE r.world_id = " + worldId
            ));

            final ResultSet rs = closer.register(stmt.executeQuery());

            while (rs.next()) {
                final String id = rs.getString("id");

                // Get the points from the cache
                final List<BlockVector2> points = pointsCache.get(id);

                if (points.size() < 3) {
                    log.log(Level.WARNING, "Invalid polygonal region '" + id + "': region has " + points.size() + " point(s) (less than the required 3). Skipping this region.");
                    continue;
                }

                final int minY = rs.getInt("min_y");
                final int maxY = rs.getInt("max_y");

                final ProtectedRegion region = new ProtectedPolygonalRegion(id, points, minY, maxY);
                region.setPriority(rs.getInt("priority"));

                loaded.put(id, region);

                final String parentId = rs.getString("parent");
                if (parentId != null) {
                    parentSets.put(region, parentId);
                }
            }
        } finally {
            closer.closeQuietly();
        }
    }

    private void loadFlags() throws SQLException {
        final Closer closer = Closer.create();
        try {
            final PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "SELECT region_id, flag, value " +
                            "FROM " + config.getTablePrefix() + "region_flag " +
                            "WHERE world_id = " + worldId +
                            " AND region_id IN " +
                            "(SELECT id FROM " +
                            config.getTablePrefix() + "region " +
                            "WHERE world_id = " + worldId + ")"));

            final ResultSet rs = closer.register(stmt.executeQuery());

            final Table<String, String, Object> data = HashBasedTable.create();
            while (rs.next()) {
                data.put(
                        rs.getString("region_id"),
                        rs.getString("flag"),
                        unmarshalFlagValue(rs.getString("value")));
            }

            for (final Entry<String, Map<String, Object>> entry : data.rowMap().entrySet()) {
                final ProtectedRegion region = loaded.get(entry.getKey());
                region.setFlags(flagRegistry.unmarshal(entry.getValue(), true));
            }
        } finally {
            closer.closeQuietly();
        }
    }

    private void loadDomainUsers() throws SQLException {
        final Closer closer = Closer.create();
        try {
            final PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "SELECT p.region_id, u.name, u.uuid, p.owner " +
                            "FROM " + config.getTablePrefix() + "region_players AS p " +
                            "LEFT JOIN " + config.getTablePrefix() + "user AS u " +
                            "          ON (p.user_id = u.id) " +
                            "WHERE p.world_id = " + worldId));

            final ResultSet rs = closer.register(stmt.executeQuery());

            while (rs.next()) {
                final ProtectedRegion region = loaded.get(rs.getString("region_id"));

                if (region != null) {
                    final DefaultDomain domain;

                    if (rs.getBoolean("owner")) {
                        domain = region.getOwners();
                    }
                    else {
                        domain = region.getMembers();
                    }

                    final String name = rs.getString("name");
                    final String uuid = rs.getString("uuid");

                    if (name != null) {
                        domain.addPlayer(name);
                    }
                    else if (uuid != null) {
                        try {
                            domain.addPlayer(UUID.fromString(uuid));
                        }
                        catch (final IllegalArgumentException e) {
                            log.warning("Invalid UUID '" + uuid + "' for region '" + region.getId() + "'");
                        }
                    }
                }
            }
        } finally {
            closer.closeQuietly();
        }
    }

    private void loadDomainGroups() throws SQLException {
        final Closer closer = Closer.create();
        try {
            final PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "SELECT rg.region_id, g.name, rg.owner " +
                            "FROM `" + config.getTablePrefix() + "region_groups` AS rg " +
                            "INNER JOIN `" + config.getTablePrefix() + "group` AS g ON (rg.group_id = g.id) " +
                            // LEFT JOIN is returning NULLS for reasons unknown
                            "AND rg.world_id = " + worldId));

            final ResultSet rs = closer.register(stmt.executeQuery());

            while (rs.next()) {
                final ProtectedRegion region = loaded.get(rs.getString("region_id"));

                if (region != null) {
                    final DefaultDomain domain;

                    if (rs.getBoolean("owner")) {
                        domain = region.getOwners();
                    } else {
                        domain = region.getMembers();
                    }

                    domain.addGroup(rs.getString("name"));
                }
            }
        } finally {
            closer.closeQuietly();
        }
    }

    private Object unmarshalFlagValue(final String rawValue) {
        try {
            return yaml.load(rawValue);
        }
        catch (final YAMLException e) {
            return String.valueOf(rawValue);
        }
    }

}
