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

package com.sk89q.worldguard.bukkit.listener;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.BukkitWorldConfiguration;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.event.entity.DamageEntityEvent;
import com.sk89q.worldguard.bukkit.event.inventory.UseItemEvent;
import com.sk89q.worldguard.bukkit.util.Entities;
import com.sk89q.worldguard.config.ConfigurationManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.TippedArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Handles blocked potions.
 */
public class BlockedPotionsListener extends AbstractListener {

    /**
     * Construct the listener.
     *
     * @param plugin an instance of WorldGuardPlugin
     */
    public BlockedPotionsListener(final WorldGuardPlugin plugin) {
        super(plugin);
    }

    @EventHandler
    public void onProjectile(final DamageEntityEvent event) {
        if (event.getOriginalEvent() instanceof EntityDamageByEntityEvent) {
            final EntityDamageByEntityEvent originalEvent = (EntityDamageByEntityEvent) event.getOriginalEvent();
            if (Entities.isPotionArrow(originalEvent.getDamager())) { // should take care of backcompat
                final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
                final BukkitWorldConfiguration wcfg = (BukkitWorldConfiguration) cfg.get(BukkitAdapter.adapt(event.getWorld()));
                PotionEffectType blockedEffect = null;
                if (originalEvent.getDamager() instanceof SpectralArrow) {
                    if (wcfg.blockPotions.contains(PotionEffectType.GLOWING)) {
                        blockedEffect = PotionEffectType.GLOWING;
                    }
                }
                else if (originalEvent.getDamager() instanceof TippedArrow) {
                    final TippedArrow tippedArrow = (TippedArrow) originalEvent.getDamager();
                    final PotionEffectType baseEffect = tippedArrow.getBasePotionData().getType().getEffectType();
                    if (wcfg.blockPotions.contains(baseEffect)) {
                        blockedEffect = baseEffect;
                    }
                    else {
                        for (final PotionEffect potionEffect : tippedArrow.getCustomEffects()) {
                            if (wcfg.blockPotions.contains(potionEffect.getType())) {
                                blockedEffect = potionEffect.getType();
                                break;
                            }
                        }
                    }
                }
                if (blockedEffect != null) {
                    final Player player = event.getCause().getFirstPlayer();
                    if (player != null) {
                        if (getPlugin().hasPermission(player, "worldguard.override.potions")) {
                            return;
                        }
                        player.sendMessage(ChatColor.RED + "Sorry, arrows with "
                                + blockedEffect.getName() + " are presently disabled.");
                    }
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onItemInteract(final UseItemEvent event) {
        final ConfigurationManager cfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        final BukkitWorldConfiguration wcfg = (BukkitWorldConfiguration) cfg.get(BukkitAdapter.adapt(event.getWorld()));
        final ItemStack item = event.getItemStack();

        // We only care about potions
        boolean oldPotions = false;
        try {
            if (item.getType() != Material.POTION
                    && item.getType() != Material.SPLASH_POTION
                    && item.getType() != Material.LINGERING_POTION) {
                return;
            }
        }
        catch (final NoSuchFieldError ignored) {
            // PotionMeta technically has been around since 1.7, so the code below
            // *should* work still. we just have different materials now.
            if (item.getType() != Material.POTION) {
                return;
            }
            oldPotions = true;
        }


        if (!wcfg.blockPotions.isEmpty()) {
            PotionEffectType blockedEffect = null;

            final PotionMeta meta;
            if (item.getItemMeta() instanceof PotionMeta) {
                meta = ((PotionMeta) item.getItemMeta());
            } else {
                return; // ok...?
            }

            // Find the first blocked effect
            final PotionEffectType baseEffect = meta.getBasePotionData().getType().getEffectType();
            if (wcfg.blockPotions.contains(baseEffect)) {
                blockedEffect = baseEffect;
            }

            if (blockedEffect == null && meta.hasCustomEffects()) {
                for (final PotionEffect effect : meta.getCustomEffects()) {
                    if (wcfg.blockPotions.contains(effect.getType())) {
                        blockedEffect = effect.getType();
                        break;
                    }
                }
            }

            if (blockedEffect != null) {
                final Player player = event.getCause().getFirstPlayer();

                if (player != null) {
                    if (getPlugin().hasPermission(player, "worldguard.override.potions")) {
                        boolean isSplash = false;
                        try {
                            isSplash = (!oldPotions && (item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION));
                        }
                        catch (final NoSuchFieldError ignored) {
                        }
                        isSplash |= (oldPotions && (Potion.fromItemStack(item).isSplash()));
                        if (isSplash && wcfg.blockPotionsAlways) {
                            player.sendMessage(ChatColor.RED + "Sorry, potions with " +
                                    blockedEffect.getName() + " can't be thrown, " +
                                    "even if you have a permission to bypass it, " +
                                    "due to limitations (and because overly-reliable potion blocking is on).");
                            event.setCancelled(true);
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "Sorry, potions with "
                                + blockedEffect.getName() + " are presently disabled.");
                        event.setCancelled(true);
                    }
                } else {
                    event.setCancelled(true);
                }
            }
        }
    }

}
