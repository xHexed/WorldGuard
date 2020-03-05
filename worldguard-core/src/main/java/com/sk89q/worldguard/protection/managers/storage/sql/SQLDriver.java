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

import com.sk89q.worldguard.protection.managers.storage.RegionDatabase;
import com.sk89q.worldguard.protection.managers.storage.RegionDriver;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.util.io.Closer;
import com.sk89q.worldguard.util.sql.DataSourceConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Stores regions using a JDBC connection with support for SQL.
 *
 * <p>Note, however, that this implementation <strong>only supports MySQL.
 * </strong></p>
 */
public class SQLDriver implements RegionDriver {

    private static final Logger log = Logger.getLogger(SQLDriver.class.getCanonicalName());
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final int CONNECTION_TIMEOUT = 6000;

    private final DataSourceConfig config;
    private boolean initialized;

    /**
     * Create a new instance.
     *
     * @param config a configuration
     */
    public SQLDriver(final DataSourceConfig config) {
        checkNotNull(config);
        this.config = config;
    }

    @Override
    public RegionDatabase get(final String name) {
        return new SQLRegionDatabase(this, name);
    }

    @Override
    public List<RegionDatabase> getAll() throws StorageException {
        final Closer closer = Closer.create();
        try {
            final List<RegionDatabase> stores = new ArrayList<>();
            final Connection connection = closer.register(getConnection());
            final Statement stmt = connection.createStatement();
            final ResultSet rs = closer.register(stmt.executeQuery("SELECT name FROM " + config.getTablePrefix() + "world"));
            while (rs.next()) {
                stores.add(get(rs.getString(1)));
            }
            return stores;
        }
        catch (final SQLException e) {
            throw new StorageException("Failed to fetch list of worlds", e);
        }
        finally {
            closer.closeQuietly();
        }
    }

    /**
     * Perform initialization if it hasn't been (successfully) performed yet.
     *
     * @throws StorageException thrown on error
     */
    synchronized void initialize() throws StorageException {
        if (!initialized) {
            try {
                migrate();
            }
            catch (final SQLException e) {
                throw new StorageException("Failed to migrate database tables", e);
            }
            initialized = true;
        }
    }

    /**
     * Attempt to migrate the tables to the latest version.
     *
     * @throws StorageException thrown if migration fails
     * @throws SQLException thrown on SQL error
     */
    private void migrate() throws SQLException, StorageException {
        final Closer closer = Closer.create();
        final Connection conn = closer.register(getConnection());

        try {
            // Check some tables
            boolean tablesExist;
            boolean isRecent;
            boolean isBeforeMigrations;
            boolean hasMigrations;

            try {
                tablesExist        = tryQuery(conn, "SELECT * FROM " + config.getTablePrefix() + "region_cuboid LIMIT 1");
                isRecent = tryQuery(conn, "SELECT world_id FROM " + config.getTablePrefix() + "region_cuboid LIMIT 1");
                isBeforeMigrations = !tryQuery(conn, "SELECT uuid FROM " + config.getTablePrefix() + "user LIMIT 1");
                hasMigrations = tryQuery(conn, "SELECT * FROM " + config.getTablePrefix() + "migrations LIMIT 1");
            } finally {
                closer.closeQuietly();
            }

            // We don't bother with migrating really old tables
            if (tablesExist && !isRecent) {
                throw new StorageException(
                        "Sorry, your tables are too old for the region SQL auto-migration system. " +
                                "Please run region_manual_update_20110325.sql on your database, which comes " +
                                "with WorldGuard or can be found in http://github.com/sk89q/worldguard");
            }

            // Our placeholders
            final Map<String, String> placeHolders = new HashMap<>();
            placeHolders.put("tablePrefix", config.getTablePrefix());

            final Flyway flyway = new Flyway();

            // The SQL support predates the usage of Flyway, so let's do some
            // checks and issue messages appropriately
            if (!hasMigrations) {
                flyway.setInitOnMigrate(true);

                if (tablesExist) {
                    // Detect if this is before migrations
                    if (isBeforeMigrations) {
                        flyway.setInitVersion(MigrationVersion.fromVersion("1"));
                    }

                    log.log(Level.INFO, "The SQL region tables exist but the migrations table seems to not exist yet. Creating the migrations table...");
                } else {
                    // By default, if Flyway sees any tables at all in the schema, it
                    // will assume that we are up to date, so we have to manually
                    // check ourselves and then ask Flyway to start from the beginning
                    // if our test table doesn't exist
                    flyway.setInitVersion(MigrationVersion.fromVersion("0"));

                    log.log(Level.INFO, "SQL region tables do not exist: creating...");
                }
            }

            flyway.setClassLoader(getClass().getClassLoader());
            flyway.setLocations("migrations/region/" + getMigrationFolderName());
            flyway.setDataSource(config.getDsn(), config.getUsername(), config.getPassword());
            flyway.setTable(config.getTablePrefix() + "migrations");
            flyway.setPlaceholders(placeHolders);
            flyway.setValidateOnMigrate(false);
            flyway.migrate();
        }
        catch (final FlywayException e) {
            throw new StorageException("Failed to migrate tables", e);
        }
        finally {
            closer.closeQuietly();
        }
    }

    /**
     * Get the name of the folder in migrations/region containing the migration files.
     *
     * @return the migration folder name
     */
    public String getMigrationFolderName() {
        return "mysql";
    }

    /**
     * Try to execute a query and return true if it did not fail.
     *
     * @param conn the connection to run the query on
     * @param sql  the SQL query
     *
     * @return true if the query did not end in error
     */
    private boolean tryQuery(final Connection conn, final String sql) {
        final Closer closer = Closer.create();
        try {
            final Statement statement = closer.register(conn.createStatement());
            statement.executeQuery(sql);
            return true;
        }
        catch (final SQLException ex) {
            return false;
        }
        finally {
            closer.closeQuietly();
        }
    }

    /**
     * Get the database configuration.
     *
     * @return the database configuration
     */
    DataSourceConfig getConfig() {
        return config;
    }

    /**
     * Create a new connection.
     *
     * @return the connection
     * @throws SQLException raised if the connection cannot be instantiated
     */
    Connection getConnection() throws SQLException {
        final Future<Connection> future = EXECUTOR.submit(() -> config.getConnection());

        try {
            return future.get(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
        }
        catch (final InterruptedException e) {
            throw new SQLException("Failed to get a SQL connection because the operation was interrupted", e);
        }
        catch (final ExecutionException e) {
            throw new SQLException("Failed to get a SQL connection due to an error", e);
        }
        catch (final TimeoutException e) {
            future.cancel(true);
            throw new SQLException("Failed to get a SQL connection within the time limit");
        }
    }

}
