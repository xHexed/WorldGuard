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

package com.sk89q.worldguard.internal.permission;

import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import javax.annotation.Nullable;

/**
 * Used for querying region-related permissions.
 */
public class RegionPermissionModel extends AbstractPermissionModel {
    
    public RegionPermissionModel(final Actor sender) {
        super(sender);
    }

    public boolean mayIgnoreRegionProtection(final World world) {
        return hasPluginPermission("region.bypass." + world.getName());
    }
    
    public boolean mayForceLoadRegions() {
        return hasPluginPermission("region.load");
    }
    
    public boolean mayForceSaveRegions() {
        return hasPluginPermission("region.save");
    }

    public boolean mayMigrateRegionStore() {
        return hasPluginPermission("region.migratedb");
    }

    public boolean mayMigrateRegionNames() {
        return hasPluginPermission("region.migrateuuid");
    }
    
    public boolean mayDefine() {
        return hasPluginPermission("region.define");
    }

    public boolean mayRedefine(final ProtectedRegion region) {
        return hasPatternPermission("redefine", region);
    }
    
    public boolean mayClaim() {
        return hasPluginPermission("region.claim");
    }
    
    public boolean mayClaimRegionsUnbounded() {
        return hasPluginPermission("region.unlimited");
    }

    public boolean mayDelete(final ProtectedRegion region) {
        return hasPatternPermission("remove", region);
    }

    public boolean maySetPriority(final ProtectedRegion region) {
        return hasPatternPermission("setpriority", region);
    }

    public boolean maySetParent(final ProtectedRegion child, final ProtectedRegion parent) {
        return hasPatternPermission("setparent", child) &&
                (parent == null ||
                        hasPatternPermission("setparent", parent));
    }

    public boolean maySelect(final ProtectedRegion region) {
        return hasPatternPermission("select", region);
    }

    public boolean mayLookup(final ProtectedRegion region) {
        return hasPatternPermission("info", region);
    }

    public boolean mayTeleportTo(final ProtectedRegion region) {
        return hasPatternPermission("teleport", region);
    }

    public boolean mayOverrideLocationFlagBounds(final ProtectedRegion region) {
        return hasPatternPermission("locationoverride", region);
    }
    
    public boolean mayList() {
        return hasPluginPermission("region.list");
    }

    public boolean mayList(final String targetPlayer) {
        if (targetPlayer == null) {
            return mayList();
        }

        if (targetPlayer.equalsIgnoreCase(getSender().getName())) {
            return hasPluginPermission("region.list.own");
        }
        else {
            return mayList();
        }
    }

    public boolean maySetFlag(final ProtectedRegion region) {
        return hasPatternPermission("flag.regions", region);
    }

    public boolean maySetFlag(final ProtectedRegion region, final Flag<?> flag) {
        // This is a WTF permission
        return hasPatternPermission(
                "flag.flags." + flag.getName().toLowerCase(), region);
    }

    public boolean maySetFlag(final ProtectedRegion region, final Flag<?> flag, @Nullable final String value) {
        String sanitizedValue;

        if (value != null) {
            sanitizedValue = value.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
            if (sanitizedValue.length() > 20) {
                sanitizedValue = sanitizedValue.substring(0, 20);
            }
        }
        else {
            sanitizedValue = "unset";
        }

        // This is a WTF permission
        return hasPatternPermission(
                "flag.flags." + flag.getName().toLowerCase() + "." + sanitizedValue, region);
    }

    public boolean mayAddMembers(final ProtectedRegion region) {
        return hasPatternPermission("addmember", region);
    }

    public boolean mayAddOwners(final ProtectedRegion region) {
        return hasPatternPermission("addowner", region);
    }

    public boolean mayRemoveMembers(final ProtectedRegion region) {
        return hasPatternPermission("removemember", region);
    }

    public boolean mayRemoveOwners(final ProtectedRegion region) {
        return hasPatternPermission("removeowner", region);
    }

    /**
     * Checks to see if the given sender has permission to modify the given region
     * using the region permission pattern.
     *
     * @param perm   the name of the node
     * @param region the region
     */
    private boolean hasPatternPermission(final String perm, final ProtectedRegion region) {
        if (!(getSender() instanceof Player)) {
            return true; // Non-players (i.e. console, command blocks, etc.) have full power
        }

        final String idLower = region.getId().toLowerCase();
        final String effectivePerm;

        if (region.isOwner((LocalPlayer) getSender())) {
            return hasPluginPermission("region." + perm + ".own." + idLower) ||
                    hasPluginPermission("region." + perm + ".member." + idLower);
        }
        else if (region.isMember((LocalPlayer) getSender())) {
            return hasPluginPermission("region." + perm + ".member." + idLower);
        }
        else {
            effectivePerm = "region." + perm + "." + idLower;
        }

        return hasPluginPermission(effectivePerm);
    }

}
