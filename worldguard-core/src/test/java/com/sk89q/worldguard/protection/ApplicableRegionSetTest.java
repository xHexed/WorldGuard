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

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.util.command.CommandFilter;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class ApplicableRegionSetTest {

    @Test
    public void testWildernessBuild() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer player = mock.createPlayer();

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertThat(set.testState(player, Flags.BUILD), is(true));
    }

    @Test
    public void testWildernessBuildWithGlobalRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer player = mock.createPlayer();

        final ProtectedRegion global = mock.global();

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertThat(set.testState(player, Flags.BUILD), is(true));
    }

    @Test
    public void testWildernessBuildWithRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        final ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(member);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertThat(set.testState(member, Flags.BUILD), is(true));
        assertThat(set.testState(nonMember, Flags.BUILD), is(false));
    }

    @Test
    public void testWildernessFlags() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer player = mock.createPlayer();

        final ApplicableRegionSet set = mock.getApplicableSet();

        assertThat(set.testState(player, Flags.MOB_DAMAGE), is(true));
        assertThat(set.testState(player, Flags.ENTRY), is(true));
        assertThat(set.testState(player, Flags.EXIT), is(true));
        assertThat(set.testState(player, Flags.LEAF_DECAY), is(true));
        assertThat(set.testState(player, Flags.RECEIVE_CHAT), is(true));
        assertThat(set.testState(player, Flags.SEND_CHAT), is(true));
        assertThat(set.testState(player, Flags.INVINCIBILITY), is(false));

        assertThat(set.testState(player, Flags.BUILD), is(true));
    }

    @Test
    public void testWildernessFlagsWithGlobalRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer player = mock.createPlayer();

        final ProtectedRegion global = mock.global();

        final ApplicableRegionSet set = mock.getApplicableSet();

        assertThat(set.testState(player, Flags.MOB_DAMAGE), is(true));
        assertThat(set.testState(player, Flags.ENTRY), is(true));
        assertThat(set.testState(player, Flags.EXIT), is(true));
        assertThat(set.testState(player, Flags.LEAF_DECAY), is(true));
        assertThat(set.testState(player, Flags.RECEIVE_CHAT), is(true));
        assertThat(set.testState(player, Flags.SEND_CHAT), is(true));
        assertThat(set.testState(player, Flags.INVINCIBILITY), is(false));

        assertThat(set.testState(player, Flags.BUILD), is(true));
    }

    @Test
    public void testFlagsWithRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        final ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(member);

        final ApplicableRegionSet set = mock.getApplicableSet();

        assertThat(set.testState(member, Flags.MOB_DAMAGE), is(true));
        assertThat(set.testState(member, Flags.ENTRY), is(true));
        assertThat(set.testState(member, Flags.EXIT), is(true));
        assertThat(set.testState(member, Flags.LEAF_DECAY), is(true));
        assertThat(set.testState(member, Flags.RECEIVE_CHAT), is(true));
        assertThat(set.testState(member, Flags.SEND_CHAT), is(true));
        assertThat(set.testState(member, Flags.INVINCIBILITY), is(false));

        assertThat(set.testState(member, Flags.BUILD), is(true));

        assertThat(set.testState(nonMember, Flags.MOB_DAMAGE), is(true));
        assertThat(set.testState(nonMember, Flags.ENTRY), is(true));
        assertThat(set.testState(nonMember, Flags.EXIT), is(true));
        assertThat(set.testState(nonMember, Flags.LEAF_DECAY), is(true));
        assertThat(set.testState(nonMember, Flags.RECEIVE_CHAT), is(true));
        assertThat(set.testState(nonMember, Flags.SEND_CHAT), is(true));
        assertThat(set.testState(nonMember, Flags.INVINCIBILITY), is(false));

        assertThat(set.testState(nonMember, Flags.BUILD), is(false));
    }

    @Test
    public void testStateFlagPriorityFallThrough() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        ProtectedRegion region;

        final StateFlag state1 = new StateFlag(null, false);
        final StateFlag state2 = new StateFlag(null, false);
        final StateFlag state3 = new StateFlag(null, false);

        region = mock.add(0);
        region.setFlag(state1, StateFlag.State.ALLOW);
        region.setFlag(state2, StateFlag.State.DENY);

        region = mock.add(1);
        region.setFlag(state1, StateFlag.State.DENY);
        region.setFlag(state3, StateFlag.State.ALLOW);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertFalse(set.testState(null, state1));
        assertFalse(set.testState(null, state2));
        assertTrue(set.testState(null, state3));
    }

    @Test
    public void testNonStateFlagPriorityFallThrough() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        ProtectedRegion region;

        final StringFlag string1 = new StringFlag(null);
        final StringFlag string2 = new StringFlag(null);
        final StringFlag string3 = new StringFlag(null);
        final StringFlag string4 = new StringFlag(null);

        region = mock.add(0);
        region.setFlag(string1, "Beans");
        region.setFlag(string2, "Apples");

        region = mock.add(1);
        region.setFlag(string1, "Cats");
        region.setFlag(string3, "Bananas");

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertEquals(set.queryValue(null, string1), "Cats");
        assertEquals(set.queryValue(null, string2), "Apples");
        assertEquals(set.queryValue(null, string3), "Bananas");
        assertNull(set.queryValue(null, string4));
    }

    @Test
    public void testStateFlagMultiplePriorityFallThrough() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        ProtectedRegion region;

        final StringFlag string1 = new StringFlag(null);
        final StringFlag string2 = new StringFlag(null);
        final StringFlag string3 = new StringFlag(null);
        final StringFlag string4 = new StringFlag(null);

        region = mock.add(0);
        region.setFlag(string1, "Beans");
        region.setFlag(string2, "Apples");
        region.setFlag(string3, "Dogs");

        region = mock.add(1);
        region.setFlag(string1, "Cats");
        region.setFlag(string3, "Bananas");

        region = mock.add(10);
        region.setFlag(string3, "Strings");

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertEquals(set.queryValue(null, string1), "Cats");
        assertEquals(set.queryValue(null, string2), "Apples");
        assertEquals(set.queryValue(null, string3), "Strings");
        assertNull(set.queryValue(null, string4));
    }

    @Test
    public void testStateGlobalDefault() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        final ProtectedRegion region;

        final StateFlag state1 = new StateFlag(null, false);
        final StateFlag state2 = new StateFlag(null, false);
        final StateFlag state3 = new StateFlag(null, false);
        final StateFlag state4 = new StateFlag(null, true);
        final StateFlag state5 = new StateFlag(null, true);
        final StateFlag state6 = new StateFlag(null, true);

        region = mock.global();
        region.setFlag(state1, StateFlag.State.ALLOW);
        region.setFlag(state2, StateFlag.State.DENY);
        region.setFlag(state4, StateFlag.State.ALLOW);
        region.setFlag(state5, StateFlag.State.DENY);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertTrue(set.testState(null, state1));
        assertFalse(set.testState(null, state2));
        assertFalse(set.testState(null, state3));
        assertTrue(set.testState(null, state4));
        assertFalse(set.testState(null, state5));
        assertTrue(set.testState(null, state6));
    }

    @Test
    public void testStateGlobalWithRegionsDefault() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        ProtectedRegion region;

        final StateFlag state1 = new StateFlag(null, false);
        final StateFlag state2 = new StateFlag(null, false);
        final StateFlag state3 = new StateFlag(null, false);
        final StateFlag state4 = new StateFlag(null, true);
        final StateFlag state5 = new StateFlag(null, true);
        final StateFlag state6 = new StateFlag(null, true);

        region = mock.global();
        region.setFlag(state1, StateFlag.State.ALLOW);
        region.setFlag(state2, StateFlag.State.DENY);
        region.setFlag(state4, StateFlag.State.ALLOW);
        region.setFlag(state5, StateFlag.State.DENY);

        region = mock.add(0);
        region.setFlag(state1, StateFlag.State.DENY);
        region.setFlag(state2, StateFlag.State.DENY);
        region.setFlag(state4, StateFlag.State.DENY);
        region.setFlag(state5, StateFlag.State.DENY);

        region = mock.add(1);
        region.setFlag(state5, StateFlag.State.ALLOW);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertFalse(set.testState(null, state1));
        assertFalse(set.testState(null, state2));
        assertFalse(set.testState(null, state3));
        assertFalse(set.testState(null, state4));
        assertTrue(set.testState(null, state5));
        assertTrue(set.testState(null, state6));
    }

    @Test
    public void testBuildAccess() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        final ProtectedRegion region;

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        region = mock.add(0);
        region.getOwners().addPlayer(member);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertTrue(set.testState(member, Flags.BUILD));
        assertFalse(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testBuildRegionPriorities() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        ProtectedRegion region;

        final LocalPlayer upperMember = mock.createPlayer();
        final LocalPlayer lowerMember = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        region = mock.add(0);
        region.getOwners().addPlayer(lowerMember);

        region = mock.add(1);
        region.getOwners().addPlayer(upperMember);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertTrue(set.testState(upperMember, Flags.BUILD));
        assertFalse(set.testState(lowerMember, Flags.BUILD));
        assertFalse(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testBuildDenyFlag() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        final ProtectedRegion region;

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        region = mock.add(0);
        region.getOwners().addPlayer(member);
        region.setFlag(Flags.BUILD, StateFlag.State.DENY);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertFalse(set.testState(member, Flags.BUILD));
        assertFalse(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testBuildAllowFlag() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        final ProtectedRegion region;

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        region = mock.add(0);
        region.getOwners().addPlayer(member);
        region.setFlag(Flags.BUILD, StateFlag.State.ALLOW);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertTrue(set.testState(member, Flags.BUILD));
        assertTrue(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testHigherPriorityOverrideBuildDenyFlag() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        ProtectedRegion region;

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        region = mock.add(0);
        region.getOwners().addPlayer(member);
        region.setFlag(Flags.BUILD, StateFlag.State.DENY);

        region = mock.add(1);
        region.setFlag(Flags.BUILD, StateFlag.State.ALLOW);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertTrue(set.testState(member, Flags.BUILD));
        assertTrue(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testHigherPriorityUnsetBuildDenyFlag() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        ProtectedRegion region;

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        region = mock.add(0);
        region.getOwners().addPlayer(member);
        region.setFlag(Flags.BUILD, StateFlag.State.DENY);

        region = mock.add(1);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertFalse(set.testState(member, Flags.BUILD));
        assertFalse(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testPriorityDisjointBuildDenyFlagAndMembership() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        ProtectedRegion region;

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        region = mock.add(0);
        region.setFlag(Flags.BUILD, StateFlag.State.DENY);

        region = mock.add(1);
        region.getOwners().addPlayer(member);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertTrue(set.testState(member, Flags.BUILD));
        assertFalse(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testPriorityDisjointBuildDenyFlagAndRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        ProtectedRegion region;

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        region = mock.add(0);
        region.setFlag(Flags.BUILD, StateFlag.State.DENY);

        region = mock.add(1);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertFalse(set.testState(member, Flags.BUILD));
        assertFalse(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testPriorityDisjointMembershipAndBuildDenyFlag() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        ProtectedRegion region;

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        region = mock.add(0);
        region.getOwners().addPlayer(member);

        region = mock.add(1);
        region.setFlag(Flags.BUILD, StateFlag.State.DENY);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertFalse(set.testState(member, Flags.BUILD));
        assertFalse(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testNoGlobalRegionDefaultBuild() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertTrue(set.testState(member, Flags.BUILD));
        assertTrue(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testGlobalRegionDefaultBuild() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        @SuppressWarnings("unused") final ProtectedRegion region = mock.global();

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertTrue(set.testState(member, Flags.BUILD));
        assertTrue(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testGlobalRegionBuildFlagAllow() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        final ProtectedRegion region;

        region = mock.global();
        region.setFlag(Flags.BUILD, StateFlag.State.ALLOW);

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertTrue(set.testState(member, Flags.BUILD));
        assertTrue(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testGlobalRegionBuildFlagDeny() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        final ProtectedRegion region;

        region = mock.global();
        region.setFlag(Flags.BUILD, StateFlag.State.DENY);

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertFalse(set.testState(member, Flags.BUILD));
        assertFalse(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testGlobalRegionBuildFlagAllowWithRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        ProtectedRegion region;

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        region = mock.global();
        region.setFlag(Flags.BUILD, StateFlag.State.ALLOW);

        region = mock.add(0);
        region.getOwners().addPlayer(member);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertTrue(set.testState(member, Flags.BUILD));
        assertFalse(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testGlobalRegionBuildFlagDenyWithRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        ProtectedRegion region;

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        region = mock.global();
        region.setFlag(Flags.BUILD, StateFlag.State.DENY);

        region = mock.add(0);
        region.getOwners().addPlayer(member);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertTrue(set.testState(member, Flags.BUILD));
        assertFalse(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testGlobalRegionHavingOwnershipBuildFlagUnset() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        final ProtectedRegion region;

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        region = mock.global();
        region.getOwners().addPlayer(member);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertTrue(set.testState(member, Flags.BUILD));
        assertFalse(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testGlobalRegionHavingOwnershipBuildFlagAllow() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        final ProtectedRegion region;

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        region = mock.global();
        region.setFlag(Flags.BUILD, StateFlag.State.ALLOW);
        region.getOwners().addPlayer(member);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertTrue(set.testState(member, Flags.BUILD));
        assertFalse(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testGlobalRegionHavingOwnershipBuildFlagDeny() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        final ProtectedRegion region;

        final LocalPlayer member = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();

        region = mock.global();
        region.setFlag(Flags.BUILD, StateFlag.State.DENY);
        region.getOwners().addPlayer(member);

        final ApplicableRegionSet set = mock.getApplicableSet();
        assertFalse(set.testState(member, Flags.BUILD));
        assertFalse(set.testState(nonMember, Flags.BUILD));
    }

    @Test
    public void testGlobalRegionCommandBlacklistWithRegionWhitelist() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();
        ProtectedRegion region;

        final LocalPlayer nonMember = mock.createPlayer();

        region = mock.global();
        final Set<String> blocked = new HashSet<>();
        blocked.add("/deny");
        blocked.add("/strange");
        region.setFlag(Flags.BLOCKED_CMDS, blocked);

        region = mock.add(0);
        final Set<String> allowed = new HashSet<>();
        allowed.add("/permit");
        allowed.add("/strange");
        region.setFlag(Flags.ALLOWED_CMDS, allowed);

        ApplicableRegionSet set;
        CommandFilter test;

        set = mock.getApplicableSet();
        test = new CommandFilter(
                set.queryValue(nonMember, Flags.ALLOWED_CMDS),
                set.queryValue(nonMember, Flags.BLOCKED_CMDS));
        assertThat(test.apply("/permit"), is(true));
        assertThat(test.apply("/strange"), is(true));
        assertThat(test.apply("/other"), is(false));
        assertThat(test.apply("/deny"), is(false));

        set = mock.getApplicableSetInWilderness();
        test = new CommandFilter(
                set.queryValue(nonMember, Flags.ALLOWED_CMDS),
                set.queryValue(nonMember, Flags.BLOCKED_CMDS));
        assertThat(test.apply("/permit"), is(true));
        assertThat(test.apply("/strange"), is(false));
        assertThat(test.apply("/other"), is(true));
        assertThat(test.apply("/deny"), is(false));
    }

}
