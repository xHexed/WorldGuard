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

package com.sk89q.worldguard.protection.regions;

import com.google.common.collect.Lists;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.util.ChangeTracked;
import com.sk89q.worldguard.util.Normal;

import javax.annotation.Nullable;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents a region that can be indexed and have spatial queries performed
 * against it.
 *
 * <p>Instances can be modified and access from several threads at a time.</p>
 */
public abstract class ProtectedRegion implements ChangeTracked, Comparable<ProtectedRegion> {

    public static final String GLOBAL_REGION = "__global__";
    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_,'\\-+/]+$");

    protected BlockVector3 min;
    protected BlockVector3 max;

    private final String id;
    private final boolean transientRegion;
    private int priority;
    private ProtectedRegion parent;
    private DefaultDomain owners = new DefaultDomain();
    private DefaultDomain members = new DefaultDomain();
    private ConcurrentMap<Flag<?>, Object> flags = new ConcurrentHashMap<>();
    private boolean dirty = true;

    /**
     * Construct a new instance of this region.
     *
     * @param id              the name of this region
     * @param transientRegion whether this region should only be kept in memory and not be saved
     *
     * @throws IllegalArgumentException thrown if the ID is invalid (see {@link #isValidId(String)}
     */
    ProtectedRegion(final String id, final boolean transientRegion) { // Package private because we can't have people creating their own region types
        checkNotNull(id);

        if (!isValidId(id)) {
            throw new IllegalArgumentException("Invalid region ID: " + id);
        }

        this.id              = Normal.normalize(id);
        this.transientRegion = transientRegion;
    }

    /**
     * Checks to see if the given ID is a valid ID.
     *
     * @param id the id to check
     *
     * @return whether the region id given is valid
     */
    public static boolean isValidId(final String id) {
        checkNotNull(id);
        return VALID_ID_PATTERN.matcher(id).matches();
    }

    /**
     * Gets the name of this region
     *
     * @return the name
     */
    public String getId() {
        return id;
    }

    /**
     * Return whether this type of region encompasses physical area.
     *
     * @return Whether physical area is encompassed
     */
    public abstract boolean isPhysicalArea();

    /**
     * Get a vector containing the smallest X, Y, and Z components for the
     * corner of the axis-aligned bounding box that contains this region.
     *
     * @return the minimum point
     */
    public BlockVector3 getMinimumPoint() {
        return min;
    }

    /**
     * Get a vector containing the highest X, Y, and Z components for the
     * corner of the axis-aligned bounding box that contains this region.
     *
     * @return the maximum point
     */
    public BlockVector3 getMaximumPoint() {
        return max;
    }

    /**
     * Get the priority of the region, where higher numbers indicate a higher
     * priority.
     *
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Set the minimum and maximum points of the bounding box for a region
     *
     * @param points the points to set with at least one entry
     */
    protected void setMinMaxPoints(final List<BlockVector3> points) {
        int minX = points.get(0).getBlockX();
        int minY = points.get(0).getBlockY();
        int minZ = points.get(0).getBlockZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;

        for (final BlockVector3 v : points) {
            final int x = v.getBlockX();
            final int y = v.getBlockY();
            final int z = v.getBlockZ();

            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;

            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        setDirty(true);
        min = BlockVector3.at(minX, minY, minZ);
        max = BlockVector3.at(maxX, maxY, maxZ);
    }

    /**
     * Get the parent of the region, if one exists.
     *
     * @return the parent, or {@code null}
     */
    @Nullable
    public ProtectedRegion getParent() {
        return parent;
    }

    /**
     * Set the priority of the region, where higher numbers indicate a higher
     * priority.
     *
     * @param priority the priority to set
     */
    public void setPriority(final int priority) {
        setDirty(true);
        this.priority = priority;
    }

    /**
     * Set the parent of this region. This checks to make sure that it will
     * not result in circular inheritance.
     *
     * @param parent the new parent
     *
     * @throws CircularInheritanceException when circular inheritance is detected
     */
    public void setParent(@Nullable final ProtectedRegion parent) throws CircularInheritanceException {
        setDirty(true);

        if (parent == null) {
            this.parent = null;
            return;
        }

        if (parent == this) {
            throw new CircularInheritanceException();
        }

        ProtectedRegion p = parent.parent;
        while (p != null) {
            if (p == this) {
                throw new CircularInheritanceException();
            }
            p = p.parent;
        }

        this.parent = parent;
    }

    /**
     * Get the domain that contains the owners of this region.
     *
     * @return the domain
     */
    public DefaultDomain getOwners() {
        return owners;
    }

    /**
     * Clear the parent (set the parent to {@code null}).
     */
    public void clearParent() {
        setDirty(true);
        parent = null;
    }

    /**
     * Get the domain that contains the members of this region, which does
     * not automatically include the owners.
     *
     * @return the members
     */
    public DefaultDomain getMembers() {
        return members;
    }

    /**
     * Set the owner domain.
     *
     * @param owners the new domain
     */
    public void setOwners(final DefaultDomain owners) {
        checkNotNull(owners);
        setDirty(true);
        this.owners = new DefaultDomain(owners);
    }

    /**
     * Checks whether a region has members or owners.
     *
     * @return whether there are members or owners
     */
    public boolean hasMembersOrOwners() {
        return owners.size() > 0 || members.size() > 0;
    }

    /**
     * Set the members domain.
     *
     * @param members the new domain
     */
    public void setMembers(final DefaultDomain members) {
        checkNotNull(members);
        setDirty(true);
        this.members = new DefaultDomain(members);
    }

    /**
     * Checks whether a player is an owner of region or any of its parents.
     *
     * @param player player to check
     *
     * @return whether an owner
     */
    public boolean isOwner(final LocalPlayer player) {
        checkNotNull(player);

        if (owners.contains(player)) {
            return true;
        }

        ProtectedRegion curParent = parent;
        while (curParent != null) {
            if (curParent.owners.contains(player)) {
                return true;
            }

            curParent = curParent.parent;
        }

        return false;
    }

    /**
     * Checks whether a player is an owner of region or any of its parents.
     *
     * @param playerName player name to check
     *
     * @return whether an owner
     * @deprecated Names are deprecated, this will not return owners added by UUID (LocalPlayer)
     */
    @Deprecated
    public boolean isOwner(final String playerName) {
        checkNotNull(playerName);

        if (owners.contains(playerName)) {
            return true;
        }

        ProtectedRegion curParent = parent;
        while (curParent != null) {
            if (curParent.owners.contains(playerName)) {
                return true;
            }

            curParent = curParent.parent;
        }

        return false;
    }

    /**
     * Checks whether a player is a member OR OWNER of the region
     * or any of its parents.
     *
     * @param player player to check
     *
     * @return whether an owner or member
     */
    public boolean isMember(final LocalPlayer player) {
        checkNotNull(player);

        if (isOwner(player)) {
            return true;
        }

        return isMemberOnly(player);
    }

    /**
     * Checks whether a player is a member OR OWNER of the region
     * or any of its parents.
     *
     * @param playerName player name to check
     *
     * @return whether an owner or member
     * @deprecated Names are deprecated, this will not return players added by UUID (LocalPlayer)
     */
    @Deprecated
    public boolean isMember(final String playerName) {
        checkNotNull(playerName);

        if (isOwner(playerName)) {
            return true;
        }

        if (members.contains(playerName)) {
            return true;
        }

        ProtectedRegion curParent = parent;
        while (curParent != null) {
            if (curParent.members.contains(playerName)) {
                return true;
            }

            curParent = curParent.parent;
        }

        return false;
    }

    /**
     * Checks whether a player is a member of the region or any of its parents.
     *
     * @param player player to check
     *
     * @return whether an member
     */
    public boolean isMemberOnly(final LocalPlayer player) {
        checkNotNull(player);

        if (members.contains(player)) {
            return true;
        }

        ProtectedRegion curParent = parent;
        while (curParent != null) {
            if (curParent.members.contains(player)) {
                return true;
            }

            curParent = curParent.parent;
        }

        return false;
    }

    /**
     * Get a flag's value.
     *
     * @param flag the flag to check
     * @param <T>  the flag type
     * @param <V>  the type of the flag's value
     *
     * @return the value or null if isn't defined
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T extends Flag<V>, V> V getFlag(final T flag) {
        checkNotNull(flag);

        final Object obj = flags.get(flag);
        final V val;

        if (obj != null) {
            val = (V) obj;
        }
        else {
            return null;
        }

        return val;
    }

    /**
     * Get the map of flags.
     *
     * @return the map of flags currently used for this region
     */
    public Map<Flag<?>, Object> getFlags() {
        return flags;
    }

    /**
     * Set a flag's value.
     *
     * @param flag the flag to check
     * @param val  the value to set
     * @param <T>  the flag type
     * @param <V>  the type of the flag's value
     */
    public <T extends Flag<V>, V> void setFlag(final T flag, @Nullable final V val) {
        checkNotNull(flag);
        setDirty(true);

        if (val == null) {
            flags.remove(flag);
        }
        else {
            flags.put(flag, val);
        }
    }

    /**
     * Set the map of flags.
     *
     * <p>A copy of the map will be used.</p>
     *
     * @param flags the flags to set
     */
    public void setFlags(final Map<Flag<?>, Object> flags) {
        checkNotNull(flags);

        setDirty(true);
        this.flags = new ConcurrentHashMap<>(flags);
    }

    /**
     * Get points of the region projected onto the X-Z plane.
     *
     * @return the points
     */
    public abstract List<BlockVector2> getPoints();

    /**
     * Get the number of blocks in this region.
     *
     * @return the volume of this region in blocks
     */
    public abstract int volume();

    /**
     * Check to see if a point is inside this region.
     *
     * @param pt The point to check
     *
     * @return Whether {@code pt} is in this region
     */
    public abstract boolean contains(BlockVector3 pt);

    /**
     * Copy attributes from another region.
     *
     * @param other the other region
     */
    public void copyFrom(final ProtectedRegion other) {
        checkNotNull(other);
        setMembers(other.members);
        setOwners(other.owners);
        setFlags(other.getFlags());
        setPriority(other.priority);
        try {
            setParent(other.parent);
        }
        catch (final CircularInheritanceException ignore) {
            // This should not be thrown
        }
    }

    /**
     * Check to see if a position is contained within this region.
     *
     * @param position the position to check
     *
     * @return whether {@code position} is in this region
     */
    public boolean contains(final BlockVector2 position) {
        checkNotNull(position);
        return contains(BlockVector3.at(position.getBlockX(), min.getBlockY(), position.getBlockZ()));
    }

    /**
     * Check to see if a point is inside this region.
     *
     * @param x the x coordinate to check
     * @param y the y coordinate to check
     * @param z the z coordinate to check
     *
     * @return whether this region contains the point
     */
    public boolean contains(final int x, final int y, final int z) {
        return contains(BlockVector3.at(x, y, z));
    }

    /**
     * Get the type of region.
     *
     * @return the type
     */
    public abstract RegionType getType();

    /**
     * Check to see if any of the points are inside this region projected
     * onto the X-Z plane.
     *
     * @param positions a list of positions
     *
     * @return true if contained
     */
    public boolean containsAny(final List<BlockVector2> positions) {
        checkNotNull(positions);

        for (final BlockVector2 pt : positions) {
            if (contains(pt)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Return a list of regions from the given list of regions that intersect
     * with this region.
     *
     * @param regions a list of regions to source from
     *
     * @return the elements of {@code regions} that intersect with this region
     */
    public List<ProtectedRegion> getIntersectingRegions(final Collection<ProtectedRegion> regions) {
        checkNotNull(regions, "regions");

        final List<ProtectedRegion> intersecting = Lists.newArrayList();
        final Area thisArea = toArea();

        for (final ProtectedRegion region : regions) {
            if (!region.isPhysicalArea()) continue;

            if (intersects(region, thisArea)) {
                intersecting.add(region);
            }
        }

        return intersecting;
    }

    /**
     * Test whether the given region intersects with this area.
     *
     * @param region   the region to test
     * @param thisArea an area object for this region
     *
     * @return true if the two regions intersect
     */
    protected boolean intersects(final ProtectedRegion region, final Area thisArea) {
        if (intersectsBoundingBox(region)) {
            final Area testArea = region.toArea();
            testArea.intersect(thisArea);
            return !testArea.isEmpty();
        }
        else {
            return false;
        }
    }

    /**
     * Checks if the bounding box of a region intersects with with the bounding
     * box of this region.
     *
     * @param region the region to check
     *
     * @return whether the given region intersects
     */
    protected boolean intersectsBoundingBox(final ProtectedRegion region) {
        final BlockVector3 rMaxPoint = region.max;
        final BlockVector3 min = this.min;

        if (rMaxPoint.getBlockX() < min.getBlockX()) return false;
        if (rMaxPoint.getBlockY() < min.getBlockY()) return false;
        if (rMaxPoint.getBlockZ() < min.getBlockZ()) return false;

        final BlockVector3 rMinPoint = region.min;
        final BlockVector3 max = this.max;

        if (rMinPoint.getBlockX() > max.getBlockX()) return false;
        if (rMinPoint.getBlockY() > max.getBlockY()) return false;
        return rMinPoint.getBlockZ() <= max.getBlockZ();
    }

    /**
     * Return the AWT area, otherwise null if
     * {@link #isPhysicalArea()} if false.
     *
     * @return The shape version
     */
    abstract Area toArea();

    /**
     * @return <code>true</code> if this region should only be kept in memory and not be saved
     */
    public boolean isTransient() {
        return transientRegion;
    }

    /**
     * Compares all edges of two regions to see if any of them intersect.
     *
     * @param region the region to check
     *
     * @return whether any edges of a region intersect
     */
    protected boolean intersectsEdges(final ProtectedRegion region) {
        final List<BlockVector2> pts1 = getPoints();
        final List<BlockVector2> pts2 = region.getPoints();
        BlockVector2 lastPt1 = pts1.get(pts1.size() - 1);
        BlockVector2 lastPt2 = pts2.get(pts2.size() - 1);
        for (final BlockVector2 aPts1 : pts1) {
            for (final BlockVector2 aPts2 : pts2) {

                final Line2D line1 = new Line2D.Double(
                        lastPt1.getBlockX(),
                        lastPt1.getBlockZ(),
                        aPts1.getBlockX(),
                        aPts1.getBlockZ());

                if (line1.intersectsLine(
                        lastPt2.getBlockX(),
                        lastPt2.getBlockZ(),
                        aPts2.getBlockX(),
                        aPts2.getBlockZ())) {
                    return true;
                }
                lastPt2 = aPts2;
            }
            lastPt1 = aPts1;
        }
        return false;
    }

    /**
     * @return <code>true</code> if this region is not transient and changes have been made.
     */
    @Override
    public boolean isDirty() {
        if (transientRegion) {
            return false;
        }
        return dirty || owners.isDirty() || members.isDirty();
    }

    @Override
    public void setDirty(final boolean dirty) {
        this.dirty = dirty;
        owners.setDirty(dirty);
        members.setDirty(dirty);
    }

    @Override
    public int hashCode(){
        return id.hashCode();
    }

    //  ** This equals doesn't take the region manager into account (since it's not available anyway)
    //  ** and thus will return equality for two regions with the same name in different worlds (or even
    //  ** no world at all, such as when testing intersection with a dummy region). Thus, we keep the
    //  ** hashCode method to improve hashset operation (which is fine within one manager - ids are unique)
    //  ** and will only leave a collision when regions in different managers with the same id are tested.
    //  ** In that case, they are checked for reference equality. Note that is it possible to programmatically
    //  ** add the same region object to two different managers. If someone uses the API in this way, it is expected
    //  ** that the regions be the same reference in both worlds. (Though that might lead to other odd behavior)
    /*
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ProtectedRegion)) {
            return false;
        }

        ProtectedRegion other = (ProtectedRegion) obj;
        return other.getId().equals(getId());
    }
    */

    @Override
    public String toString() {
        return "ProtectedRegion{" +
                "id='" + id + "', " +
                "type='" + getType() + '\'' +
                '}';
    }

    @Override
    public int compareTo(final ProtectedRegion other) {
        if (priority > other.priority) {
            return -1;
        }
        else if (priority < other.priority) {
            return 1;
        }

        return id.compareTo(other.id);
    }

    /**
     * Thrown when setting a parent would create a circular inheritance
     * situation.
     */
    public static class CircularInheritanceException extends Exception {
    }

}
