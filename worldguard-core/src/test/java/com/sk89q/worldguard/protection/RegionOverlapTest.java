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

package com.sk89q.worldguard.protection;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.TestPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public abstract class RegionOverlapTest {
    static final String COURTYARD_ID = "courtyard";
    static final String FOUNTAIN_ID = "fountain";
    static final String NO_FIRE_ID = "nofire";
    static final String MEMBER_GROUP = "member";
    static final String COURTYARD_GROUP = "courtyard";

    final BlockVector3 inFountain = BlockVector3.at(2, 2, 2);
    final BlockVector3 inCourtyard = BlockVector3.at(7, 7, 7);
    final BlockVector3 outside = BlockVector3.at(15, 15, 15);
    final BlockVector3 inNoFire = BlockVector3.at(150, 150, 150);
    RegionManager manager;
    ProtectedRegion globalRegion;
    ProtectedRegion courtyard;
    ProtectedRegion fountain;
    TestPlayer player1;
    TestPlayer player2;

    protected FlagRegistry getFlagRegistry() {
        return WorldGuard.getInstance().getFlagRegistry();
    }

    protected abstract RegionManager createRegionManager();

    @Before
    public void setUp() throws Exception {
        setUpGlobalRegion();
        
        manager = createRegionManager();

        setUpPlayers();
        setUpCourtyardRegion();
        setUpFountainRegion();
        setUpNoFireRegion();
    }

    void setUpPlayers() {
        player1 = new TestPlayer("tetsu");
        player1.addGroup(MEMBER_GROUP);
        player1.addGroup(COURTYARD_GROUP);

        player2 = new TestPlayer("alex");
        player2.addGroup(MEMBER_GROUP);
    }

    void setUpGlobalRegion() {
        globalRegion = new GlobalProtectedRegion("__global__");
    }

    void setUpCourtyardRegion() {
        final DefaultDomain domain = new DefaultDomain();
        domain.addGroup(COURTYARD_GROUP);

        final ArrayList<BlockVector2> points = new ArrayList<>();
        points.add(BlockVector2.ZERO);
        points.add(BlockVector2.at(10, 0));
        points.add(BlockVector2.at(10, 10));
        points.add(BlockVector2.at(0, 10));

        //ProtectedRegion region = new ProtectedCuboidRegion(COURTYARD_ID, new BlockVector(0, 0, 0), new BlockVector(10, 10, 10));
        final ProtectedRegion region = new ProtectedPolygonalRegion(COURTYARD_ID, points, 0, 10);

        region.setOwners(domain);
        manager.addRegion(region);

        courtyard = region;
    }

    void setUpFountainRegion() throws Exception {
        final DefaultDomain domain = new DefaultDomain();
        domain.addGroup(MEMBER_GROUP);

        final ProtectedRegion region = new ProtectedCuboidRegion(FOUNTAIN_ID,
                                                                 BlockVector3.ZERO, BlockVector3.at(5, 5, 5));
        region.setMembers(domain);
        manager.addRegion(region);

        fountain = region;
        fountain.setParent(courtyard);
        fountain.setFlag(Flags.FIRE_SPREAD, StateFlag.State.DENY);
    }

    void setUpNoFireRegion() {
        final ProtectedRegion region = new ProtectedCuboidRegion(NO_FIRE_ID,
                                                                 BlockVector3.at(100, 100, 100), BlockVector3.at(200, 200, 200));
        manager.addRegion(region);
        region.setFlag(Flags.FIRE_SPREAD, StateFlag.State.DENY);
    }

    @Test
    public void testNonBuildFlag() {
        ApplicableRegionSet appl;

        // Outside
        appl = manager.getApplicableRegions(outside);
        assertTrue(appl.testState(null, Flags.FIRE_SPREAD));
        // Inside courtyard
        appl = manager.getApplicableRegions(inCourtyard);
        assertTrue(appl.testState(null, Flags.FIRE_SPREAD));
        // Inside fountain
        appl = manager.getApplicableRegions(inFountain);
        assertFalse(appl.testState(null, Flags.FIRE_SPREAD));

        // Inside no fire zone
        appl = manager.getApplicableRegions(inNoFire);
        assertFalse(appl.testState(null, Flags.FIRE_SPREAD));
    }

    @Test
    public void testPlayer1BuildAccess() {
        ApplicableRegionSet appl;

        // Outside
        appl = manager.getApplicableRegions(outside);
        assertTrue(appl.testState(player1, Flags.BUILD));
        // Inside courtyard
        appl = manager.getApplicableRegions(inCourtyard);
        assertTrue(appl.testState(player1, Flags.BUILD));
        // Inside fountain
        appl = manager.getApplicableRegions(inFountain);
        assertTrue(appl.testState(player1, Flags.BUILD));
    }

    @Test
    public void testPlayer2BuildAccess() {
        ApplicableRegionSet appl;

        final HashSet<ProtectedRegion> test = new HashSet<>();
        test.add(courtyard);
        test.add(fountain);

        // Outside
        appl = manager.getApplicableRegions(outside);
        assertTrue(appl.testState(player2, Flags.BUILD));
        // Inside courtyard
        appl = manager.getApplicableRegions(inCourtyard);
        assertFalse(appl.testState(player2, Flags.BUILD));
        // Inside fountain
        appl = manager.getApplicableRegions(inFountain);
        assertTrue(appl.testState(player2, Flags.BUILD));
    }
}
