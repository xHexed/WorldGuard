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
import com.sk89q.worldguard.protection.FlagValueCalculator.Result;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.hamcrest.Matchers;
import org.junit.Test;

import static com.google.common.collect.ImmutableSet.copyOf;
import static com.google.common.collect.ImmutableSet.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@SuppressWarnings({"UnusedAssignment", "UnusedDeclaration"})
public class FlagValueCalculatorTest {

    @Test
    public void testGetMembershipWilderness() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer player = mock.createPlayer();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getMembership(player), is(Result.NO_REGIONS));
    }

    @Test
    public void testGetMembershipWildernessWithGlobalRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer player = mock.createPlayer();

        final ProtectedRegion global = mock.global();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getMembership(player), is(Result.NO_REGIONS));
    }

    @Test
    public void testGetMembershipGlobalRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion global = mock.global();

        final LocalPlayer player = mock.createPlayer();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getMembership(player), is(Result.NO_REGIONS));
    }

    @Test
    public void testGetMembershipGlobalRegionAndRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion global = mock.global();

        final ProtectedRegion region = mock.add(0);

        final LocalPlayer player = mock.createPlayer();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getMembership(player), is(Result.FAIL));
    }

    @Test
    public void testGetMembershipPassthroughRegions() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        ProtectedRegion region = mock.add(0);
        region.setFlag(Flags.PASSTHROUGH, State.ALLOW);

        region = mock.add(0);
        region.setFlag(Flags.PASSTHROUGH, State.ALLOW);

        final LocalPlayer player = mock.createPlayer();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getMembership(player), is(Result.NO_REGIONS));
    }

    @Test
    public void testGetMembershipPassthroughAndRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        ProtectedRegion region = mock.add(0);
        region.setFlag(Flags.PASSTHROUGH, State.ALLOW);

        region = mock.add(0);

        final LocalPlayer player = mock.createPlayer();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getMembership(player), is(Result.FAIL));
    }

    @Test
    public void testGetMembershipPassthroughAndRegionMemberOf() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        ProtectedRegion region = mock.add(0);
        region.setFlag(Flags.PASSTHROUGH, State.ALLOW);

        region = mock.add(0);

        final LocalPlayer player = mock.createPlayer();
        region.getMembers().addPlayer(player);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getMembership(player), is(Result.SUCCESS));
    }

    @Test
    public void testGetMembershipPassthroughAndRegionMemberOfAndAnotherNot() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        ProtectedRegion region = mock.add(0);
        region.setFlag(Flags.PASSTHROUGH, State.ALLOW);

        region = mock.add(0);

        final LocalPlayer player = mock.createPlayer();
        region.getMembers().addPlayer(player);

        region = mock.add(0);

        FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getMembership(player), is(Result.FAIL));

        // Add another player (should still fail)
        region.getMembers().addPlayer(mock.createPlayer());

        result = mock.getFlagCalculator();
        assertThat(result.getMembership(player), is(Result.FAIL));
    }

    @Test
    public void testGetMembershipPassthroughAndRegionMemberOfAndAnotherNotWithHigherPriority() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        ProtectedRegion region = mock.add(0);
        region.setFlag(Flags.PASSTHROUGH, State.ALLOW);

        region = mock.add(0);

        final LocalPlayer player = mock.createPlayer();
        region.getMembers().addPlayer(player);

        region = mock.add(10);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getMembership(player), is(Result.FAIL));
    }

    @Test
    public void testGetMembershipPassthroughAndRegionMemberOfWithHigherPriorityAndAnotherNot() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        ProtectedRegion region = mock.add(0);
        region.setFlag(Flags.PASSTHROUGH, State.ALLOW);

        region = mock.add(10);

        final LocalPlayer player = mock.createPlayer();
        region.getMembers().addPlayer(player);

        region = mock.add(0);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getMembership(player), is(Result.SUCCESS));
    }

    @Test
    public void testGetMembershipPassthroughAndRegionMemberOfWithAnotherParent() throws Exception {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion passthrough = mock.add(0);
        passthrough.setFlag(Flags.PASSTHROUGH, State.ALLOW);

        final ProtectedRegion parent = mock.add(0);

        final ProtectedRegion region = mock.add(0);
        region.setParent(parent);

        final LocalPlayer player = mock.createPlayer();
        region.getMembers().addPlayer(player);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getMembership(player), is(Result.SUCCESS));
    }

    @Test
    public void testGetMembershipPassthroughAndRegionMemberOfWithAnotherChild() throws Exception {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion passthrough = mock.add(0);
        passthrough.setFlag(Flags.PASSTHROUGH, State.ALLOW);

        final ProtectedRegion parent = mock.add(0);

        final ProtectedRegion region = mock.add(0);
        region.setParent(parent);

        final LocalPlayer player = mock.createPlayer();
        parent.getMembers().addPlayer(player);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getMembership(player), is(Result.SUCCESS));
    }

    @Test
    public void testGetMembershipPassthroughAndRegionMemberOfWithAnotherChildAndAnother() throws Exception {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion passthrough = mock.add(0);
        passthrough.setFlag(Flags.PASSTHROUGH, State.ALLOW);

        final ProtectedRegion parent = mock.add(0);

        ProtectedRegion region = mock.add(0);
        region.setParent(parent);

        region = mock.add(0);

        final LocalPlayer player = mock.createPlayer();
        parent.getMembers().addPlayer(player);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getMembership(player), is(Result.FAIL));
    }

    @Test
    public void testGetMembershipThirdPriorityLower() throws Exception {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion passthrough = mock.add(0);
        passthrough.setFlag(Flags.PASSTHROUGH, State.ALLOW);

        final ProtectedRegion parent = mock.add(0);

        ProtectedRegion region = mock.add(0);
        region.setParent(parent);

        region = mock.add(0);
        region.setPriority(-5);

        final LocalPlayer player = mock.createPlayer();
        parent.getMembers().addPlayer(player);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getMembership(player), is(Result.SUCCESS));
    }

    // ========================================================================
    // ========================================================================

    @Test
    public void testQueryStateWilderness() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StateFlag flag1 = new StateFlag("test1", false);
        final StateFlag flag2 = new StateFlag("test2", true);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryState(null, flag1), is((State) null));
        assertThat(result.queryState(null, flag2), is(State.ALLOW));
    }

    // ========================================================================
    // ========================================================================

    @Test
    public void testQueryValueSingleRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StateFlag flag1 = new StateFlag("test1", false);
        final StateFlag flag2 = new StateFlag("test2", false);

        final ProtectedRegion region = mock.add(0);
        region.setFlag(flag2, State.DENY);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(null, flag1), is((State) null));
        assertThat(result.queryValue(null, flag2), is(State.DENY));
    }

    @Test
    public void testQueryValueDenyOverridesAllow() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StateFlag flag1 = new StateFlag("test1", false);
        final StateFlag flag2 = new StateFlag("test2", false);

        ProtectedRegion region = mock.add(0);
        region.setFlag(flag2, State.DENY);

        region = mock.add(0);
        region.setFlag(flag2, State.ALLOW);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(null, flag1), is((State) null));
        assertThat(result.queryValue(null, flag2), is(State.DENY));
    }

    @Test
    public void testQueryValueAllowOverridesNone() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        ProtectedRegion region = mock.add(0);

        final StateFlag flag1 = new StateFlag("test1", false);
        final StateFlag flag2 = new StateFlag("test2", false);

        region = mock.add(0);
        region.setFlag(flag2, State.ALLOW);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(null, flag1), is((State) null));
        assertThat(result.queryValue(null, flag2), is(State.ALLOW));
    }

    @Test
    public void testQueryValueMultipleFlags() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StateFlag flag1 = new StateFlag("test1", false);
        final StateFlag flag2 = new StateFlag("test2", false);
        final StateFlag flag3 = new StateFlag("test3", false);

        ProtectedRegion region = mock.add(0);
        region.setFlag(flag1, State.DENY);
        region.setFlag(flag2, State.ALLOW);

        region = mock.add(0);
        region.setFlag(flag2, State.DENY);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(null, flag1), is(State.DENY));
        assertThat(result.queryValue(null, flag2), is(State.DENY));
        assertThat(result.queryValue(null, flag3), is((State) null));
    }

    @Test
    public void testQueryValueFlagsWithRegionGroupsAndInheritance() throws Exception {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StateFlag flag1 = new StateFlag("test1", false);

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer member = mock.createPlayer();

        final ProtectedRegion parent = mock.add(0);
        parent.setFlag(flag1, State.DENY);
        parent.setFlag(flag1.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

        final ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(member);
        region.setParent(parent);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, flag1), is(State.DENY));
        assertThat(result.queryValue(member, flag1), is((State) null));
    }

    @Test
    public void testQueryValueFlagsWithRegionGroupsAndInheritanceAndParentMember() throws Exception {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StateFlag flag1 = new StateFlag("test1", false);

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer memberOne = mock.createPlayer();
        final LocalPlayer memberTwo = mock.createPlayer();

        final ProtectedRegion parent = mock.add(0);
        parent.getMembers().addPlayer(memberOne);
        parent.setFlag(flag1, State.DENY);
        parent.setFlag(flag1.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

        final ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(memberOne);
        region.setParent(parent);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, flag1), is(State.DENY));
        assertThat(result.queryValue(memberOne, flag1), is((State) null));
        assertThat(result.queryValue(memberTwo, flag1), is(State.DENY));
    }

    @Test
    public void testQueryValueFlagsWithRegionGroupsAndPriority() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StateFlag flag1 = new StateFlag("test1", false);

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer member = mock.createPlayer();

        final ProtectedRegion lower = mock.add(-1);
        lower.setFlag(flag1, State.DENY);
        lower.setFlag(flag1.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

        final ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(member);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, flag1), is(State.DENY));
        assertThat(result.queryValue(member, flag1), is(State.DENY));
    }

    @Test
    public void testQueryValueFlagsWithRegionGroupsAndPriorityAndOveride() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StateFlag flag1 = new StateFlag("test1", false);

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer member = mock.createPlayer();

        final ProtectedRegion lower = mock.add(-1);
        lower.setFlag(flag1, State.DENY);
        lower.setFlag(flag1.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

        final ProtectedRegion region = mock.add(0);
        region.setFlag(flag1, State.ALLOW);
        region.setFlag(flag1.getRegionGroupFlag(), RegionGroup.MEMBERS);
        region.getMembers().addPlayer(member);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, flag1), is(State.DENY));
        assertThat(result.queryValue(member, flag1), is(State.ALLOW));
    }

    @Test
    public void testQueryValueStringFlag() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");
        final StateFlag flag1 = new StateFlag("test1", false);

        ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");

        region = mock.add(0);
        region.setFlag(stringFlag1, "test2");

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(null, stringFlag1), isOneOf("test1", "test2"));
        assertThat(result.queryValue(null, stringFlag2), is((String) null));
        assertThat(result.queryValue(null, flag1), is((State) null));
    }

    @Test
    public void testQueryValueEmptyGlobalRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StateFlag flag = new StateFlag("test", true);

        final ProtectedRegion global = mock.global();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(null, flag), is(State.ALLOW));
    }

    @Test
    public void testQueryValueGlobalRegionAllow() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StateFlag flag = new StateFlag("test", true);

        final ProtectedRegion global = mock.global();
        global.setFlag(flag, State.ALLOW);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(null, flag), is(State.ALLOW));
    }

    @Test
    public void testQueryValueGlobalRegionDeny() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StateFlag flag = new StateFlag("test", true);

        final ProtectedRegion global = mock.global();
        global.setFlag(flag, State.DENY);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(null, flag), is(State.DENY));
    }

    @Test
    public void testQueryValueStringFlagWithGlobalRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag flag = new StringFlag("test");

        final ProtectedRegion global = mock.global();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(null, flag), is((String) null));
    }

    @Test
    public void testQueryValueStringFlagWithGlobalRegionValueSet() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag flag = new StringFlag("test");

        final ProtectedRegion global = mock.global();
        global.setFlag(flag, "hello");

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(null, flag), is("hello"));
    }

    @Test
    public void testQueryValueStringFlagWithGlobalRegionAndRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag flag = new StringFlag("test");

        final ProtectedRegion global = mock.global();
        global.setFlag(flag, "hello");

        final ProtectedRegion region = mock.add(0);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(null, flag), is("hello"));
    }

    @Test
    public void testQueryValueStringFlagWithGlobalRegionAndRegionOverride() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag flag = new StringFlag("test");

        final ProtectedRegion global = mock.global();
        global.setFlag(flag, "hello");

        final ProtectedRegion region = mock.add(0);
        region.setFlag(flag, "beep");

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(null, flag), is("beep"));
    }

    @Test
    public void testQueryValueStringFlagWithEverything() throws Exception {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag flag = new StringFlag("test", RegionGroup.ALL);

        final ProtectedRegion global = mock.global();
        global.setFlag(flag, "hello");

        final ProtectedRegion parent = mock.add(0);
        parent.setFlag(flag, "ello there");

        final ProtectedRegion region = mock.add(0);
        region.setFlag(flag, "beep beep");
        region.setFlag(flag.getRegionGroupFlag(), RegionGroup.MEMBERS);
        region.setParent(parent);

        final LocalPlayer nonMember = mock.createPlayer();

        final LocalPlayer member = mock.createPlayer();
        region.getMembers().addPlayer(member);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(null, flag), is("ello there"));
        assertThat(result.queryValue(nonMember, flag), is("ello there"));
        assertThat(result.queryValue(member, flag), is("beep beep"));
    }

    @Test
    public void testQueryValueBuildFlagWilderness() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, Flags.BUILD), is(State.ALLOW));
    }

    @Test
    public void testQueryValueBuildFlagWildernessAndGlobalRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();

        final ProtectedRegion global = mock.global();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, Flags.BUILD), is(State.ALLOW));
    }

    @Test
    public void testQueryValueBuildFlagWildernessAndGlobalRegionDeny() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();

        final ProtectedRegion global = mock.global();
        global.setFlag(Flags.BUILD, State.DENY);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, Flags.BUILD), is(State.DENY));
    }

    @Test
    public void testQueryValueBuildFlagWildernessAndGlobalRegionAllow() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();

        final ProtectedRegion global = mock.global();
        global.setFlag(Flags.BUILD, State.ALLOW);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, Flags.BUILD), is(State.ALLOW));
    }

    @Test
    public void testQueryValueBuildFlagWildernessAndGlobalRegionMembership() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer member = mock.createPlayer();

        final ProtectedRegion global = mock.global();
        global.getMembers().addPlayer(member);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(member, Flags.BUILD), is(State.ALLOW));
        assertThat(result.queryValue(nonMember, Flags.BUILD), is((State) null));
    }

    @Test
    public void testQueryValueBuildFlagWildernessAndGlobalRegionMembershipAndDeny() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer member = mock.createPlayer();

        final ProtectedRegion global = mock.global();
        global.getMembers().addPlayer(member);
        global.setFlag(Flags.BUILD, State.DENY);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(member, Flags.BUILD), is(State.DENY));
        assertThat(result.queryValue(nonMember, Flags.BUILD), is(State.DENY));
    }

    @Test
    public void testQueryValueBuildFlagWildernessAndGlobalRegionMembershipAndAllow() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer member = mock.createPlayer();

        final ProtectedRegion global = mock.global();
        global.getMembers().addPlayer(member);
        global.setFlag(Flags.BUILD, State.ALLOW);

        // Cannot set ALLOW on BUILD

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(member, Flags.BUILD), is(State.ALLOW));
        assertThat(result.queryValue(nonMember, Flags.BUILD), is((State) null));
    }

    @Test
    public void testQueryValueBuildFlagRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer member = mock.createPlayer();

        final ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(member);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(member, Flags.BUILD), is(State.ALLOW));
    }

    @Test
    public void testQueryValueBuildFlagRegionsOverlapping() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer memberOne = mock.createPlayer();
        final LocalPlayer memberBoth = mock.createPlayer();

        ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(memberOne);
        region.getMembers().addPlayer(memberBoth);

        region = mock.add(0);
        region.getMembers().addPlayer(memberBoth);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberOne, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberBoth, Flags.BUILD), is(State.ALLOW));
    }

    @Test
    public void testQueryValueBuildFlagRegionsOverlappingDifferingPriority() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer memberOne = mock.createPlayer();
        final LocalPlayer memberBoth = mock.createPlayer();

        ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(memberOne);
        region.getMembers().addPlayer(memberBoth);
        region.setPriority(10);

        region = mock.add(0);
        region.getMembers().addPlayer(memberBoth);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberOne, Flags.BUILD), is(State.ALLOW));
        assertThat(result.queryValue(memberBoth, Flags.BUILD), is(State.ALLOW));
    }

    @Test
    public void testQueryValueBuildFlagRegionsOverlappingInheritanceFromParent() throws Exception {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer memberOne = mock.createPlayer();
        final LocalPlayer memberBoth = mock.createPlayer();

        final ProtectedRegion parent = mock.add(0);
        parent.getMembers().addPlayer(memberOne);
        parent.getMembers().addPlayer(memberBoth);

        final ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(memberBoth);
        region.setParent(parent);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberOne, Flags.BUILD), is(State.ALLOW));
        assertThat(result.queryValue(memberBoth, Flags.BUILD), is(State.ALLOW));
    }

    @Test
    public void testQueryValueBuildFlagRegionsOverlappingInheritanceFromChild() throws Exception {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer memberOne = mock.createPlayer();
        final LocalPlayer memberBoth = mock.createPlayer();

        final ProtectedRegion parent = mock.add(0);
        parent.getMembers().addPlayer(memberBoth);

        final ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(memberBoth);
        region.getMembers().addPlayer(memberOne);
        region.setParent(parent);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberOne, Flags.BUILD), is(State.ALLOW));
        assertThat(result.queryValue(memberBoth, Flags.BUILD), is(State.ALLOW));
    }

    @Test
    public void testQueryValueBuildFlagRegionsOverlappingInheritanceFromChildAndPriority() throws Exception {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer memberOne = mock.createPlayer();
        final LocalPlayer memberBoth = mock.createPlayer();

        final ProtectedRegion parent = mock.add(0);
        parent.getMembers().addPlayer(memberBoth);

        final ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(memberBoth);
        region.getMembers().addPlayer(memberOne);
        region.setParent(parent);

        final ProtectedRegion priority = mock.add(10);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberOne, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberBoth, Flags.BUILD), is((State) null));
    }

    @Test
    public void testQueryValueBuildFlagRegionsOverlappingInheritanceFromChildAndPriorityPassthrough() throws Exception {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer memberOne = mock.createPlayer();
        final LocalPlayer memberBoth = mock.createPlayer();

        final ProtectedRegion parent = mock.add(0);
        parent.getMembers().addPlayer(memberBoth);

        final ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(memberBoth);
        region.getMembers().addPlayer(memberOne);
        region.setParent(parent);

        final ProtectedRegion priority = mock.add(10);
        priority.setFlag(Flags.PASSTHROUGH, State.ALLOW);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberOne, Flags.BUILD), is(State.ALLOW));
        assertThat(result.queryValue(memberBoth, Flags.BUILD), is(State.ALLOW));
    }

    @Test
    public void testQueryValueBuildFlagRegionsOverlappingAndGlobalRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer memberOne = mock.createPlayer();
        final LocalPlayer memberBoth = mock.createPlayer();

        final ProtectedRegion global = mock.global();

        ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(memberOne);
        region.getMembers().addPlayer(memberBoth);

        region = mock.add(0);
        region.getMembers().addPlayer(memberBoth);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberOne, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberBoth, Flags.BUILD), is(State.ALLOW));
    }

    @Test
    public void testQueryValueBuildFlagRegionsOverlappingAndGlobalRegionDenyRegionOverride() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer memberOne = mock.createPlayer();
        final LocalPlayer memberBoth = mock.createPlayer();

        final ProtectedRegion global = mock.global();
        global.setFlag(Flags.BUILD, State.DENY);

        ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(memberOne);
        region.getMembers().addPlayer(memberBoth);

        region = mock.add(0);
        region.getMembers().addPlayer(memberBoth);
        region.setFlag(Flags.BUILD, State.ALLOW);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, Flags.BUILD), is(State.ALLOW));
        assertThat(result.queryValue(memberOne, Flags.BUILD), is(State.ALLOW));
        assertThat(result.queryValue(memberBoth, Flags.BUILD), is(State.ALLOW));
    }

    @Test
    public void testQueryValueBuildFlagRegionsOverlappingAndGlobalRegionDenyRegionOverrideDenyAndAllow() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer memberOne = mock.createPlayer();
        final LocalPlayer memberBoth = mock.createPlayer();

        final ProtectedRegion global = mock.global();
        global.setFlag(Flags.BUILD, State.DENY);

        ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(memberOne);
        region.getMembers().addPlayer(memberBoth);
        region.setFlag(Flags.BUILD, State.DENY);

        region = mock.add(0);
        region.getMembers().addPlayer(memberBoth);
        region.setFlag(Flags.BUILD, State.ALLOW);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, Flags.BUILD), is(State.DENY));
        assertThat(result.queryValue(memberOne, Flags.BUILD), is(State.DENY));
        assertThat(result.queryValue(memberBoth, Flags.BUILD), is(State.DENY));
    }

    @Test
    public void testQueryValueBuildFlagRegionsOverlappingAndGlobalRegionAllow() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer memberOne = mock.createPlayer();
        final LocalPlayer memberBoth = mock.createPlayer();

        final ProtectedRegion global = mock.global();
        global.setFlag(Flags.BUILD, State.ALLOW);

        ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(memberOne);
        region.getMembers().addPlayer(memberBoth);

        region = mock.add(0);
        region.getMembers().addPlayer(memberBoth);

        // Disable setting ALLOW for safety reasons

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(nonMember, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberOne, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberBoth, Flags.BUILD), is(State.ALLOW));
    }

    @Test
    public void testQueryValueBuildFlagRegionsOverlappingAndGlobalRegionMembership() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer globalMember = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer memberOne = mock.createPlayer();
        final LocalPlayer memberBoth = mock.createPlayer();

        final ProtectedRegion global = mock.global();
        global.getMembers().addPlayer(globalMember);

        ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(memberOne);
        region.getMembers().addPlayer(memberBoth);

        region = mock.add(0);
        region.getMembers().addPlayer(memberBoth);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(globalMember, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(nonMember, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberOne, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberBoth, Flags.BUILD), is(State.ALLOW));
    }

    @Test
    public void testQueryValueBuildFlagRegionsOverlappingAndGlobalRegionMembershipAndGlobalDeny() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer globalMember = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer memberOne = mock.createPlayer();
        final LocalPlayer memberBoth = mock.createPlayer();

        final ProtectedRegion global = mock.global();
        global.getMembers().addPlayer(globalMember);
        global.setFlag(Flags.BUILD, State.DENY);

        ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(memberOne);
        region.getMembers().addPlayer(memberBoth);

        region = mock.add(0);
        region.getMembers().addPlayer(memberBoth);

        final FlagValueCalculator result = mock.getFlagCalculator();
        // Inconsistent due to legacy reasons
        assertThat(result.queryValue(globalMember, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(nonMember, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberOne, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberBoth, Flags.BUILD), is(State.ALLOW));
    }

    @Test
    public void testQueryValueBuildFlagRegionsOverlappingAndGlobalRegionMembershipAndGlobalAllow() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final LocalPlayer globalMember = mock.createPlayer();
        final LocalPlayer nonMember = mock.createPlayer();
        final LocalPlayer memberOne = mock.createPlayer();
        final LocalPlayer memberBoth = mock.createPlayer();

        final ProtectedRegion global = mock.global();
        global.getMembers().addPlayer(globalMember);
        global.setFlag(Flags.BUILD, State.ALLOW);

        ProtectedRegion region = mock.add(0);
        region.getMembers().addPlayer(memberOne);
        region.getMembers().addPlayer(memberBoth);

        region = mock.add(0);
        region.getMembers().addPlayer(memberBoth);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.queryValue(globalMember, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(nonMember, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberOne, Flags.BUILD), is((State) null));
        assertThat(result.queryValue(memberBoth, Flags.BUILD), is(State.ALLOW));
    }

    // ========================================================================
    // ========================================================================

    @Test
    public void testQueryAllValuesTwoWithSamePriority() {
        // ====================================================================
        // Two regions with the same priority
        // ====================================================================

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");

        region = mock.add(0);
        region.setFlag(stringFlag1, "test2");

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(copyOf(result.queryAllValues(null, stringFlag1)), equalTo(of("test1", "test2")));
        assertThat(result.queryAllValues(null, stringFlag2), is(Matchers.empty()));
    }

    @Test
    public void testQueryAllValuesTwoWithDuplicateFlagValues() {
        // ====================================================================
        // Two regions with duplicate values
        // ====================================================================

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test");

        region = mock.add(0);
        region.setFlag(stringFlag1, "test");

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(copyOf(result.queryAllValues(null, stringFlag1)), equalTo(of("test", "test")));
        assertThat(result.queryAllValues(null, stringFlag2), is(Matchers.empty()));
    }

    @Test
    public void testQueryAllValuesWithHigherPriority() {
        // ====================================================================
        // One of the regions has a higher priority (should override)
        // ====================================================================

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        ProtectedRegion region = mock.add(10);
        region.setFlag(stringFlag1, "test1");

        region = mock.add(0);
        region.setFlag(stringFlag1, "test2");

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(copyOf(result.queryAllValues(null, stringFlag1)), equalTo(of("test1")));
        assertThat(result.queryAllValues(null, stringFlag2), is(Matchers.empty()));
    }

    @Test
    public void testQueryAllValuesWithTwoElevatedPriorities() {
        // ====================================================================
        // Two regions with the same elevated priority
        // ====================================================================

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        ProtectedRegion region = mock.add(10);
        region.setFlag(stringFlag1, "test3");

        region = mock.add(10);
        region.setFlag(stringFlag1, "test1");

        region = mock.add(0);
        region.setFlag(stringFlag1, "test2");

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(copyOf(result.queryAllValues(null, stringFlag1)), equalTo(of("test1", "test3")));
        assertThat(result.queryAllValues(null, stringFlag2), is(Matchers.empty()));
    }

    @Test
    public void testQueryAllValuesParentChildWithSamePriority() throws Exception {
        // ====================================================================
        // Child region and parent region with the same priority
        // ====================================================================

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final ProtectedRegion parent1 = mock.add(10);
        parent1.setFlag(stringFlag1, "test3");

        ProtectedRegion region = mock.add(10);
        region.setFlag(stringFlag1, "test1");
        region.setParent(parent1);

        region = mock.add(0);
        region.setFlag(stringFlag1, "test2");

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(copyOf(result.queryAllValues(null, stringFlag1)), equalTo(of("test1")));
        assertThat(result.queryAllValues(null, stringFlag2), is(Matchers.empty()));
    }

    @Test
    public void testQueryAllValuesParentWithHigherPriority() throws Exception {
        // ====================================================================
        // Parent region with a higher priority than the child
        // ====================================================================

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final ProtectedRegion parent1 = mock.add(20);
        parent1.setFlag(stringFlag1, "test3");

        ProtectedRegion region = mock.add(10);
        region.setFlag(stringFlag1, "test1");
        region.setParent(parent1);

        region = mock.add(0);
        region.setFlag(stringFlag1, "test2");

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(copyOf(result.queryAllValues(null, stringFlag1)), equalTo(of("test3")));
        assertThat(result.queryAllValues(null, stringFlag2), is(Matchers.empty()));
    }

    @Test
    public void testQueryAllValuesParentWithLowerPriority() throws Exception {
        // ====================================================================
        // Parent region with a lower priority than the child
        // ====================================================================

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final ProtectedRegion parent1 = mock.add(5);
        parent1.setFlag(stringFlag1, "test3");

        ProtectedRegion region = mock.add(10);
        region.setFlag(stringFlag1, "test1");
        region.setParent(parent1);

        region = mock.add(0);
        region.setFlag(stringFlag1, "test2");

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(copyOf(result.queryAllValues(null, stringFlag1)), equalTo(of("test1")));
        assertThat(result.queryAllValues(null, stringFlag2), is(Matchers.empty()));
    }

    @Test
    public void testQueryAllValuesThirdRegionWithHigherPriorityThanParentChild() throws Exception {
        // ====================================================================
        // Third region with higher priority than parent and child
        // ====================================================================

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final ProtectedRegion parent1 = mock.add(5);
        parent1.setFlag(stringFlag1, "test3");

        ProtectedRegion region = mock.add(10);
        region.setFlag(stringFlag1, "test1");
        region.setParent(parent1);

        region = mock.add(20);
        region.setFlag(stringFlag1, "test2");

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(copyOf(result.queryAllValues(null, stringFlag1)), equalTo(of("test2")));
        assertThat(result.queryAllValues(null, stringFlag2), is(Matchers.empty()));
    }

    @Test
    public void testQueryAllValuesParentsAndInheritance() throws Exception {
        // ====================================================================
        // Multiple regions with parents, one region using flag from parent
        // ====================================================================

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final ProtectedRegion parent1 = mock.add(5);
        parent1.setFlag(stringFlag1, "test1");

        ProtectedRegion region = mock.add(20);
        region.setFlag(stringFlag1, "test2");
        region.setParent(parent1);

        final ProtectedRegion parent2 = mock.add(6);
        parent2.setFlag(stringFlag1, "test3");

        region = mock.add(20);
        region.setParent(parent2);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(copyOf(result.queryAllValues(null, stringFlag1)), equalTo(of("test2", "test3")));
        assertThat(result.queryAllValues(null, stringFlag2), is(Matchers.empty()));
    }

    @Test
    public void testQueryAllValuesParentsAndInheritanceHighPriorityAndNoFlag() throws Exception {
        // ====================================================================
        // Multiple regions with parents, one region with high priority but no flag
        // ====================================================================

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");
        final StateFlag flag1 = new StateFlag("test1", false);

        final ProtectedRegion parent1 = mock.add(5);
        parent1.setFlag(stringFlag1, "test1");

        ProtectedRegion region = mock.add(20);
        region.setFlag(stringFlag1, "test2");
        region.setParent(parent1);

        final ProtectedRegion parent2 = mock.add(6);
        parent2.setFlag(stringFlag1, "test3");

        region = mock.add(30);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(copyOf(result.queryAllValues(null, stringFlag1)), equalTo(of("test2")));
        assertThat(result.queryAllValues(null, stringFlag2), is(Matchers.empty()));
    }

    @Test
    public void testQueryAllValuesParentWithSamePriorityAsHighest() throws Exception {
        // ====================================================================
        // As before, except a parent region has the same priority as the previous highest
        // ====================================================================

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");
        final StateFlag flag1 = new StateFlag("test1", false);

        final ProtectedRegion parent1 = mock.add(30);
        parent1.setFlag(stringFlag1, "test1");

        ProtectedRegion region = mock.add(20);
        region.setFlag(stringFlag1, "test2");
        region.setParent(parent1);

        final ProtectedRegion parent2 = mock.add(6);
        parent2.setFlag(stringFlag1, "test3");

        region = mock.add(30);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(copyOf(result.queryAllValues(null, stringFlag1)), equalTo(of("test1")));
        assertThat(result.queryAllValues(null, stringFlag2), is(Matchers.empty()));
    }

    // ========================================================================
    // ========================================================================

    @Test
    public void testGetEffectivePriority() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion region = mock.add(30);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getPriority(region), is(30));
    }

    @Test
    public void testGetEffectivePriorityGlobalRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion region = mock.global();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getPriority(region), is(Integer.MIN_VALUE));
    }

    // ========================================================================
    // ========================================================================

    @Test
    public void testGetEffectiveFlagSingleRegion() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        // ====================================================================
        // Single region
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.ALL);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, null), equalTo("test1"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagWithALLGroupAndNonMember() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        // ====================================================================
        // Single region with group ALL and non-member player
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.ALL);

        final LocalPlayer player = mock.createPlayer();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player), equalTo("test1"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagWithALLGroupAndNull() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        // ====================================================================
        // Single region with group ALL and null player
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.ALL);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, null), equalTo("test1"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagNONMEMBERSGroupNonMember() {
        // ====================================================================
        // Single region with group NON-MEMBERS and non-member player
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

        final LocalPlayer player = mock.createPlayer();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player), equalTo("test1"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagNONMEMBERSGroupNull() {
        // ====================================================================
        // Single region with group NON-MEMBERS and null player
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, null), equalTo("test1"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagNONOWNERSGroupNonMember() {
        // ====================================================================
        // Single region with group NON-OWNERS and non-member player
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.NON_OWNERS);

        final LocalPlayer player = mock.createPlayer();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player), equalTo("test1"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagMEMBERSGroupNonMember() {
        // ====================================================================
        // Single region with group MEMBERS and non-member player
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.MEMBERS);

        final LocalPlayer player = mock.createPlayer();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player), equalTo(null));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagMEMBERSGroupNull() {
        // ====================================================================
        // Single region with group MEMBERS and null player
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.MEMBERS);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, null), equalTo(null));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagMEMBERSGroupMember() {
        // ====================================================================
        // Single region with group MEMBERS and member player
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.MEMBERS);

        final LocalPlayer player = mock.createPlayer();
        region.getMembers().addPlayer(player);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player), equalTo("test1"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagMEMBERSGroupOwner() {
        // ====================================================================
        // Single region with group MEMBERS and owner player
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.MEMBERS);

        final LocalPlayer player = mock.createPlayer();
        region.getOwners().addPlayer(player);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player), equalTo("test1"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagOWNERSGroupOwner() {
        // ====================================================================
        // Single region with group OWNERS and owner player
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.OWNERS);

        final LocalPlayer player = mock.createPlayer();
        region.getOwners().addPlayer(player);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player), equalTo("test1"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagOWNERSGroupMember() {
        // ====================================================================
        // Single region with group OWNERS and member player
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.OWNERS);

        final LocalPlayer player = mock.createPlayer();
        region.getMembers().addPlayer(player);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player), equalTo(null));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagNONOWNERSGroupOwner() {
        // ====================================================================
        // Single region with group NON-OWNERS and owner player
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.NON_OWNERS);

        final LocalPlayer player = mock.createPlayer();
        region.getOwners().addPlayer(player);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player), equalTo(null));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagNONMEMBERSGroupOwner() {
        // ====================================================================
        // Single region with group NON-MEMBERS and owner player
        // ====================================================================

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

        final LocalPlayer player = mock.createPlayer();
        region.getOwners().addPlayer(player);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player), equalTo(null));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagNONOWNERSGroupMember() {
        // ====================================================================
        // Single region with group NON-OWNERS and member player
        // ====================================================================

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.NON_OWNERS);

        final LocalPlayer player = mock.createPlayer();
        region.getMembers().addPlayer(player);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player), equalTo("test1"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagNONOWNERSNonMember() {
        // ====================================================================
        // Single region with group NON-OWNERS and non-member player
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "test1");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.NON_OWNERS);

        final LocalPlayer player = mock.createPlayer();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player), equalTo("test1"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagThreeInheritance() throws Exception {
        // ====================================================================
        // Three-level inheritance
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion parent1 = mock.add(0);
        parent1.setFlag(stringFlag1, "test1");
        parent1.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.ALL);

        final ProtectedRegion parent2 = mock.add(0);
        parent2.setParent(parent1);

        final ProtectedRegion region = mock.add(0);
        region.setParent(parent2);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, null), equalTo("test1"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagThreeInheritanceMiddleOverride() throws Exception {
        // ====================================================================
        // Three-level inheritance, overridden on middle level
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion parent1 = mock.add(0);
        parent1.setFlag(stringFlag1, "test1");
        parent1.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.ALL);

        final ProtectedRegion parent2 = mock.add(0);
        parent2.setParent(parent1);
        parent2.setFlag(stringFlag1, "test2");
        parent1.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.ALL);

        final ProtectedRegion region = mock.add(0);
        region.setParent(parent2);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, null), equalTo("test2"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagThreeInheritanceLastOverride() throws Exception {
        // ====================================================================
        // Three-level inheritance, overridden on last level
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion parent1 = mock.add(0);
        parent1.setFlag(stringFlag1, "test1");
        parent1.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.ALL);

        final ProtectedRegion parent2 = mock.add(0);
        parent2.setParent(parent1);

        final ProtectedRegion region = mock.add(0);
        region.setParent(parent2);
        region.setFlag(stringFlag1, "test3");
        parent1.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.ALL);

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, null), equalTo("test3"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagInheritanceAndDifferingGroups() throws Exception {
        // ====================================================================
        // Three-level inheritance, overridden on last level
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion parent1 = mock.add(0);
        parent1.setFlag(stringFlag1, "everyone");
        parent1.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.ALL);

        final ProtectedRegion parent2 = mock.add(0);
        parent2.setFlag(stringFlag1, "members");
        parent2.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.MEMBERS);
        parent2.setParent(parent1);

        final ProtectedRegion region = mock.add(0);
        region.setParent(parent2);

        final LocalPlayer player1 = mock.createPlayer();
        parent2.getMembers().addPlayer(player1);

        final LocalPlayer player2 = mock.createPlayer();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player1), equalTo("members"));
        assertThat(result.getEffectiveFlag(region, stringFlag1, player2), equalTo("everyone"));
        assertThat(result.getEffectiveFlag(region, stringFlag1, null), equalTo("everyone"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagInheritanceAndDifferingGroupsMemberOnChild() throws Exception {
        // ====================================================================
        // Three-level inheritance, overridden on last level
        // ====================================================================

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion parent1 = mock.add(0);
        parent1.setFlag(stringFlag1, "everyone");
        parent1.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.ALL);

        final ProtectedRegion parent2 = mock.add(0);
        parent2.setFlag(stringFlag1, "members");
        parent2.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.MEMBERS);
        parent2.setParent(parent1);

        final ProtectedRegion region = mock.add(0);
        region.setParent(parent2);

        final LocalPlayer player1 = mock.createPlayer();
        region.getMembers().addPlayer(player1);

        final LocalPlayer player2 = mock.createPlayer();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player1), equalTo("members"));
        assertThat(result.getEffectiveFlag(region, stringFlag1, player2), equalTo("everyone"));
        assertThat(result.getEffectiveFlag(region, stringFlag1, null), equalTo("everyone"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagInheritanceAndDifferingGroupsMemberOnParent() throws Exception {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final ProtectedRegion parent1 = mock.add(0);
        parent1.setFlag(stringFlag1, "everyone");
        parent1.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.ALL);

        final ProtectedRegion parent2 = mock.add(0);
        parent2.setFlag(stringFlag1, "members");
        parent2.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.MEMBERS);
        parent2.setParent(parent1);

        final ProtectedRegion region = mock.add(0);
        region.setParent(parent2);

        final LocalPlayer player1 = mock.createPlayer();
        parent1.getMembers().addPlayer(player1);

        final LocalPlayer player2 = mock.createPlayer();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player1), equalTo("members"));
        assertThat(result.getEffectiveFlag(region, stringFlag1, player2), equalTo("everyone"));
        assertThat(result.getEffectiveFlag(region, stringFlag1, null), equalTo("everyone"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagInheritanceAndDifferingGroupsMemberOnParentFlagOnBottom() throws Exception {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final ProtectedRegion parent1 = mock.add(0);
        parent1.setFlag(stringFlag1, "everyone");
        parent1.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.ALL);

        final ProtectedRegion parent2 = mock.add(0);
        parent2.setParent(parent1);

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "members");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.MEMBERS);
        region.setParent(parent2);

        final LocalPlayer player1 = mock.createPlayer();
        parent1.getMembers().addPlayer(player1);

        final LocalPlayer player2 = mock.createPlayer();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player1), equalTo("members"));
        assertThat(result.getEffectiveFlag(region, stringFlag1, player2), equalTo("everyone"));
        assertThat(result.getEffectiveFlag(region, stringFlag1, null), equalTo("everyone"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagInheritanceAndDifferingGroupsMemberOnParentFlagOnBottomGroupOutside() throws Exception {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final StringFlag stringFlag1 = new StringFlag("string1");
        final StringFlag stringFlag2 = new StringFlag("string2");

        final ProtectedRegion parent1 = mock.createOutside(0);
        parent1.setFlag(stringFlag1, "everyone");
        parent1.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.ALL);

        final ProtectedRegion parent2 = mock.add(0);
        parent2.setParent(parent1);

        final ProtectedRegion region = mock.add(0);
        region.setFlag(stringFlag1, "members");
        region.setFlag(stringFlag1.getRegionGroupFlag(), RegionGroup.MEMBERS);
        region.setParent(parent2);

        final LocalPlayer player1 = mock.createPlayer();
        parent1.getMembers().addPlayer(player1);

        final LocalPlayer player2 = mock.createPlayer();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(region, stringFlag1, player1), equalTo("members"));
        assertThat(result.getEffectiveFlag(region, stringFlag1, player2), equalTo("everyone"));
        assertThat(result.getEffectiveFlag(region, stringFlag1, null), equalTo("everyone"));
        assertThat(result.getEffectiveFlag(region, stringFlag2, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagGlobalRegionBuild() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion global = mock.global();

        final FlagValueCalculator result = mock.getFlagCalculator();
        assertThat(result.getEffectiveFlag(global, Flags.BUILD, null), equalTo(null));
    }

    @Test
    public void testGetEffectiveFlagGlobalRegionBuildDeny() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion global = mock.global();
        global.setFlag(Flags.BUILD, State.DENY);

        final FlagValueCalculator result = mock.getFlagCalculator();
        // Cannot let users override BUILD on GLOBAL
        assertThat(result.getEffectiveFlag(global, Flags.BUILD, null), equalTo(State.DENY));
    }

    @Test
    public void testGetEffectiveFlagGlobalRegionBuildAllow() {
        final MockApplicableRegionSet mock = new MockApplicableRegionSet();

        final ProtectedRegion global = mock.global();
        global.setFlag(Flags.BUILD, State.ALLOW);

        final FlagValueCalculator result = mock.getFlagCalculator();
        // Cannot let users override BUILD on GLOBAL
        assertThat(result.getEffectiveFlag(global, Flags.BUILD, null), equalTo(null));
    }
}