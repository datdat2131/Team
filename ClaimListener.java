package com.astraxbox.team;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimListener implements Listener {
    private final AstraxTeam plugin;
    private final Map<UUID, String> lastZone = new HashMap<>();

    public ClaimListener(AstraxTeam plugin) { this.plugin = plugin; }

    /** @return true nếu hành động bị chặn. */
    private boolean denied(Player p, Location loc, String key) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("claims.enabled", true) || !c.getBoolean("claims.protect." + key, true)) return false;
        if (p.hasPermission("astraxteam.bypass")) return false;
        ZoneManager.Zone z = plugin.getZones().getAt(loc);
        if (z == null || z.owner == null) return false;
        Team owner = plugin.getTeams().getTeamById(z.owner);
        if (owner == null) return false;
        Team mine = plugin.getTeams().getTeam(p.getUniqueId());
        if (mine != null && mine == owner) return false;
        plugin.msg().send(p, "zone-protected", "zone", z.name, "team", owner.getName());
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation(), "break")) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation(), "place")) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation(), "place")) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation(), "break")) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (!e.getClickedBlock().getType().isInteractable()) return;
        if (denied(e.getPlayer(), e.getClickedBlock().getLocation(), "interact")) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null) return;
        Location from = e.getFrom();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("claims.enabled", true) || !c.getBoolean("claims.show-enter-message", true)) return;

        Player p = e.getPlayer();
        ZoneManager.Zone z = plugin.getZones().getAt(to);
        String now = z == null ? "" : z.name;
        String before = lastZone.getOrDefault(p.getUniqueId(), "");
        if (now.equals(before)) return;
        lastZone.put(p.getUniqueId(), now);
        Messages m = plugin.msg();
        if (z != null) {
            Team owner = z.owner == null ? null : plugin.getTeams().getTeamById(z.owner);
            Messages.actionBar(p, m.raw("zone-enter", "zone", z.name,
                    "owner", owner == null ? m.raw("free") : "&e" + owner.getName()));
        } else {
            Messages.actionBar(p, m.raw("zone-leave"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastZone.remove(e.getPlayer().getUniqueId());
    }
}
