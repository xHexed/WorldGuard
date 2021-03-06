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

import com.google.common.collect.Lists;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.util.io.Closer;
import com.sk89q.worldguard.util.sql.DataSourceConfig;
import org.yaml.snakeyaml.Yaml;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Updates region data that needs to be updated for both inserts and updates.
 */
class RegionUpdater {

    private static final Logger log = Logger.getLogger(RegionUpdater.class.getCanonicalName());
    private final DataSourceConfig config;
    private final Connection conn;
    private final int worldId;
    private final DomainTableCache domainTableCache;

    private final Set<String> userNames = new HashSet<>();
    private final Set<UUID> userUuids = new HashSet<>();
    private final Set<String> groupNames = new HashSet<>();

    private final Yaml yaml = SQLRegionDatabase.createYaml();

    private final List<ProtectedRegion> typesToUpdate = new ArrayList<>();
    private final List<ProtectedRegion> parentsToSet = new ArrayList<>();
    private final List<ProtectedRegion> flagsToReplace = new ArrayList<>();
    private final List<ProtectedRegion> domainsToReplace = new ArrayList<>();

    RegionUpdater(final DataUpdater updater) {
        config           = updater.config;
        conn             = updater.conn;
        worldId          = updater.worldId;
        domainTableCache = updater.domainTableCache;
    }

    public void updateRegionType(final ProtectedRegion region) {
        typesToUpdate.add(region);
    }

    public void updateRegionProperties(final ProtectedRegion region) {
        if (region.getParent() != null) {
            parentsToSet.add(region);
        }

        flagsToReplace.add(region);
        domainsToReplace.add(region);

        addDomain(region.getOwners());
        addDomain(region.getMembers());
    }

    private void addDomain(final DefaultDomain domain) {
        for (final String name : domain.getPlayers()) {
            userNames.add(name.toLowerCase());
        }

        userUuids.addAll(domain.getUniqueIds());

        for (final String name : domain.getGroups()) {
            groupNames.add(name.toLowerCase());
        }
    }

    private void setParents() throws SQLException {
        final Closer closer = Closer.create();
        try {
            final PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "UPDATE " + config.getTablePrefix() + "region " +
                            "SET parent = ? " +
                            "WHERE id = ? AND world_id = " + worldId));

            for (final List<ProtectedRegion> partition : Lists.partition(parentsToSet, StatementBatch.MAX_BATCH_SIZE)) {
                for (final ProtectedRegion region : partition) {
                    final ProtectedRegion parent = region.getParent();
                    if (parent != null) { // Parent would be null due to a race condition
                        stmt.setString(1, parent.getId());
                        stmt.setString(2, region.getId());
                        stmt.addBatch();
                    }
                }

                stmt.executeBatch();
            }
        } finally {
            closer.closeQuietly();
        }
    }

    private void replaceFlags() throws SQLException {
        Closer closer = Closer.create();
        try {
            final PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "DELETE FROM " + config.getTablePrefix() + "region_flag " +
                            "WHERE region_id = ? " +
                            "AND world_id = " + worldId));

            for (final List<ProtectedRegion> partition : Lists.partition(flagsToReplace, StatementBatch.MAX_BATCH_SIZE)) {
                for (final ProtectedRegion region : partition) {
                    stmt.setString(1, region.getId());
                    stmt.addBatch();
                }

                stmt.executeBatch();
            }
        } finally {
            closer.closeQuietly();
        }

        closer = Closer.create();
        try {
            final PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "INSERT INTO " + config.getTablePrefix() + "region_flag " +
                            "(id, region_id, world_id, flag, value) " +
                            "VALUES " +
                            "(null, ?, " + worldId + ", ?, ?)"));

            final StatementBatch batch = new StatementBatch(stmt, StatementBatch.MAX_BATCH_SIZE);

            for (final ProtectedRegion region : flagsToReplace) {
                for (final Map.Entry<Flag<?>, Object> entry : region.getFlags().entrySet()) {
                    if (entry.getValue() == null) continue;

                    final Object flag = marshalFlagValue(entry.getKey(), entry.getValue());

                    stmt.setString(1, region.getId());
                    stmt.setString(2, entry.getKey().getName());
                    stmt.setObject(3, flag);
                    batch.addBatch();
                }
            }

            batch.executeRemaining();
        } finally {
            closer.closeQuietly();
        }
    }

    private void replaceDomainUsers() throws SQLException {
        // Remove users
        Closer closer = Closer.create();
        try {
            final PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "DELETE FROM " + config.getTablePrefix() + "region_players " +
                            "WHERE region_id = ? " +
                            "AND world_id = " + worldId));

            for (final List<ProtectedRegion> partition : Lists.partition(domainsToReplace, StatementBatch.MAX_BATCH_SIZE)) {
                for (final ProtectedRegion region : partition) {
                    stmt.setString(1, region.getId());
                    stmt.addBatch();
                }

                stmt.executeBatch();
            }
        } finally {
            closer.closeQuietly();
        }

        // Add users
        closer = Closer.create();
        try {
            final PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "INSERT INTO " + config.getTablePrefix() + "region_players " +
                            "(region_id, world_id, user_id, owner) " +
                            "VALUES (?, " + worldId + ",  ?, ?)"));

            final StatementBatch batch = new StatementBatch(stmt, StatementBatch.MAX_BATCH_SIZE);

            for (final ProtectedRegion region : domainsToReplace) {
                insertDomainUsers(stmt, batch, region, region.getMembers(), false); // owner = false
                insertDomainUsers(stmt, batch, region, region.getOwners(), true); // owner = true
            }

            batch.executeRemaining();
        }
        finally {
            closer.closeQuietly();
        }
    }

    private void insertDomainUsers(final PreparedStatement stmt, final StatementBatch batch, final ProtectedRegion region, final DefaultDomain domain, final boolean owner) throws SQLException {
        for (final String name : domain.getPlayers()) {
            final Integer id = domainTableCache.getUserNameCache().find(name);
            if (id != null) {
                stmt.setString(1, region.getId());
                stmt.setInt(2, id);
                stmt.setBoolean(3, owner);
                batch.addBatch();
            }
            else {
                log.log(Level.WARNING, "Did not find an ID for the user identified as '" + name + "'");
            }
        }

        for (final UUID uuid : domain.getUniqueIds()) {
            final Integer id = domainTableCache.getUserUuidCache().find(uuid);
            if (id != null) {
                stmt.setString(1, region.getId());
                stmt.setInt(2, id);
                stmt.setBoolean(3, owner);
                batch.addBatch();
            }
            else {
                log.log(Level.WARNING, "Did not find an ID for the user identified by '" + uuid + "'");
            }
        }
    }

    private void replaceDomainGroups() throws SQLException {
        // Remove groups
        Closer closer = Closer.create();
        try {
            final PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "DELETE FROM " + config.getTablePrefix() + "region_groups " +
                            "WHERE region_id = ? " +
                            "AND world_id = " + worldId));

            for (final List<ProtectedRegion> partition : Lists.partition(domainsToReplace, StatementBatch.MAX_BATCH_SIZE)) {
                for (final ProtectedRegion region : partition) {
                    stmt.setString(1, region.getId());
                    stmt.addBatch();
                }

                stmt.executeBatch();
            }
        } finally {
            closer.closeQuietly();
        }

        // Add groups
        closer = Closer.create();
        try {
            final PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "INSERT INTO " + config.getTablePrefix() + "region_groups " +
                            "(region_id, world_id, group_id, owner) " +
                            "VALUES (?, " + worldId + ",  ?, ?)"));

            final StatementBatch batch = new StatementBatch(stmt, StatementBatch.MAX_BATCH_SIZE);

            for (final ProtectedRegion region : domainsToReplace) {
                insertDomainGroups(stmt, batch, region, region.getMembers(), false); // owner = false
                insertDomainGroups(stmt, batch, region, region.getOwners(), true); // owner = true
            }

            batch.executeRemaining();
        }
        finally {
            closer.closeQuietly();
        }
    }

    private void insertDomainGroups(final PreparedStatement stmt, final StatementBatch batch, final ProtectedRegion region, final DefaultDomain domain, final boolean owner) throws SQLException {
        for (final String name : domain.getGroups()) {
            final Integer id = domainTableCache.getGroupNameCache().find(name);
            if (id != null) {
                stmt.setString(1, region.getId());
                stmt.setInt(2, id);
                stmt.setBoolean(3, owner);
                batch.addBatch();
            }
            else {
                log.log(Level.WARNING, "Did not find an ID for the group identified as '" + name + "'");
            }
        }
    }

    private void updateRegionTypes() throws SQLException {
        final Closer closer = Closer.create();
        try {
            final PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "UPDATE " + config.getTablePrefix() + "region " +
                            "SET type = ?, priority = ?, parent = NULL " +
                            "WHERE id = ? AND world_id = " + worldId));

            for (final List<ProtectedRegion> partition : Lists.partition(typesToUpdate, StatementBatch.MAX_BATCH_SIZE)) {
                for (final ProtectedRegion region : partition) {
                    stmt.setString(1, SQLRegionDatabase.getRegionTypeName(region));
                    stmt.setInt(2, region.getPriority());
                    stmt.setString(3, region.getId());
                    stmt.addBatch();
                }

                stmt.executeBatch();
            }
        } finally {
            closer.closeQuietly();
        }
    }

    public void apply() throws SQLException {
        domainTableCache.getUserNameCache().fetch(userNames);
        domainTableCache.getUserUuidCache().fetch(userUuids);
        domainTableCache.getGroupNameCache().fetch(groupNames);

        updateRegionTypes();
        setParents();
        replaceFlags();
        replaceDomainUsers();
        replaceDomainGroups();
    }

    @SuppressWarnings("unchecked")
    private <V> Object marshalFlagValue(final Flag<V> flag, final Object val) {
        return yaml.dump(flag.marshal((V) val));
    }

}
