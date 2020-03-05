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

package com.sk89q.worldguard.config;

import com.google.common.collect.ImmutableMap;
import com.sk89q.util.yaml.YAMLFormat;
import com.sk89q.util.yaml.YAMLProcessor;
import com.sk89q.worldedit.util.report.Unreported;
import com.sk89q.worldguard.protection.managers.storage.DriverType;
import com.sk89q.worldguard.protection.managers.storage.RegionDriver;
import com.sk89q.worldguard.protection.managers.storage.file.DirectoryYamlDriver;
import com.sk89q.worldguard.protection.managers.storage.sql.SQLDriver;
import com.sk89q.worldguard.util.sql.DataSourceConfig;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class YamlConfigurationManager extends ConfigurationManager {

    @Unreported private YAMLProcessor config;

    public abstract void copyDefaults();

    @Override
    public void load() {
        copyDefaults();

        config = new YAMLProcessor(new File(getDataFolder(), "config.yml"), true, YAMLFormat.EXTENDED);
        try {
            config.load();
        }
        catch (final IOException e) {
            log.severe("Error reading configuration for global config: ");
            e.printStackTrace();
        }

        config.removeProperty("suppress-tick-sync-warnings");
        migrateRegionsToUuid = config.getBoolean("regions.uuid-migration.perform-on-next-start", true);
        keepUnresolvedNames = config.getBoolean("regions.uuid-migration.keep-names-that-lack-uuids", true);
        useRegionsCreatureSpawnEvent = config.getBoolean("regions.use-creature-spawn-event", true);
        useGodPermission = config.getBoolean("auto-invincible", config.getBoolean("auto-invincible-permission", false));
        useGodGroup = config.getBoolean("auto-invincible-group", false);
        useAmphibiousGroup = config.getBoolean("auto-no-drowning-group", false);
        config.removeProperty("auto-invincible-permission");
        usePlayerMove = config.getBoolean("use-player-move-event", true);
        usePlayerTeleports = config.getBoolean("use-player-teleports", true);
        particleEffects = config.getBoolean("use-particle-effects", true);

        deopOnJoin = config.getBoolean("security.deop-everyone-on-join", false);
        blockInGameOp = config.getBoolean("security.block-in-game-op-command", false);

        hostKeys = new HashMap<>();
        final Object hostKeysRaw = config.getProperty("host-keys");
        if (!(hostKeysRaw instanceof Map)) {
            config.setProperty("host-keys", new HashMap<String, String>());
        } else {
            for (final Map.Entry<Object, Object> entry : ((Map<Object, Object>) hostKeysRaw).entrySet()) {
                final String key = String.valueOf(entry.getKey());
                final String value = String.valueOf(entry.getValue());
                hostKeys.put(key.toLowerCase(), value);
            }
        }
        hostKeysAllowFMLClients = config.getBoolean("security.host-keys-allow-forge-clients", false);

        // ====================================================================
        // Region store drivers
        // ====================================================================

        final boolean useSqlDatabase = config.getBoolean("regions.sql.use", false);
        final String sqlDsn = config.getString("regions.sql.dsn", "jdbc:mysql://localhost/worldguard");
        final String sqlUsername = config.getString("regions.sql.username", "worldguard");
        final String sqlPassword = config.getString("regions.sql.password", "worldguard");
        final String sqlTablePrefix = config.getString("regions.sql.table-prefix", "");

        final DataSourceConfig dataSourceConfig = new DataSourceConfig(sqlDsn, sqlUsername, sqlPassword, sqlTablePrefix);
        final SQLDriver sqlDriver = new SQLDriver(dataSourceConfig);
        final DirectoryYamlDriver yamlDriver = new DirectoryYamlDriver(getWorldsDataFolder(), "regions.yml");

        regionStoreDriverMap      = ImmutableMap.<DriverType, RegionDriver>builder()
                .put(DriverType.MYSQL, sqlDriver)
                .put(DriverType.YAML, yamlDriver)
                .build();
        selectedRegionStoreDriver = useSqlDatabase ? sqlDriver : yamlDriver;

        postLoad();

        config.setHeader(CONFIG_HEADER);
    }

    public void postLoad() {}

    public YAMLProcessor getConfig() {
        return config;
    }

    @Override
    public void disableUuidMigration() {
        config.setProperty("regions.uuid-migration.perform-on-next-start", false);
        if (!config.save()) {
            log.severe("Error saving configuration!");
        }
    }
}
