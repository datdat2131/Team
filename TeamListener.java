package com.astraxbox.team;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

public class TeamListener implements Listener {
    private final AstraxTeam plugin;

    public TeamListener(AstraxTeam plugin) { this.plugin = plugin; }

    /** Gõ /team ... sẽ tự chuyển thành /ateam ... (tránh đụng lệnh /team có sẵn của Minecraft). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("settings.redirect-team-command", true)) return;
        String msg = e.getMessage();
        String lower = msg.toLowerCase();
        if (lower.equals("/team") || lower.startsWith("/team ")) {
            e.setMessage("/ateam" + msg.substring(5));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Team t = plugin.getTeams().getTeam(p.getUniqueId());
        if (t != null) {
            if (!t.getMemberName(p.getUniqueId()).equals(p.getName())) {
                t.setLastName(p.getUniqueId(), p.getName());
                plugin.getTeams().markDirty();
            }
        }
        plugin.getDisplay().refresh(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.getWars().onQuit(p);
        plugin.getDisplay().clear(p);
    }

    // ----- Chặn đánh đồng đội -----
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player pl) attacker = pl;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl2) attacker = pl2;
        if (attacker == null || attacker.equals(victim)) return;
        TeamManager tm = plugin.getTeams();
        Team ta = tm.getTeam(attacker.getUniqueId());
        if (ta == null || ta != tm.getTeam(victim.getUniqueId())) return;
        // Trong war luôn cấm đánh đồng đội; ngoài war tuỳ cài đặt pvp của team
        if (plugin.getWars().isAlive(attacker.getUniqueId()) || !ta.isPvp()) {
            e.setCancelled(true);
            plugin.msg().send(attacker, "friendly-fire-off");
        }
    }

    // ----- Bất tử khi đang đếm ngược / kết thúc war -----
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        WarManager.State st = plugin.getWars().state();
        if (st == null || st == WarManager.State.RUNNING) return;
        if (plugin.getWars().isAlive(p.getUniqueId())) e.setCancelled(true);
    }

    // ----- Kill / điểm / war -----
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        TeamManager tm = plugin.getTeams();
        FileConfiguration c = plugin.getConfig();
        Team vt = tm.getTeam(victim.getUniqueId());
        Team kt = killer == null ? null : tm.getTeam(killer.getUniqueId());
        boolean teamKill = vt != null && vt == kt;
        if (!teamKill) {
            if (kt != null) {
                kt.addKills(1);
                kt.addPoints(c.getInt("points.kill", 2));
            }
            if (vt != null && killer != null) {
                vt.addDeaths(1);
                vt.addPoints(-c.getInt("points.death", 1));
            }
            tm.markDirty();
        }
        plugin.getWars().onDeath(victim, killer, e);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Location l = plugin.getWars().consumeRespawn(p.getUniqueId());
        if (l != null) e.setRespawnLocation(l);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getDisplay().refresh(p));
    }

    // ----- Đóng băng khi đếm ngược war -----
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        WarManager wm = plugin.getWars();
        if (wm.state() != WarManager.State.COUNTDOWN) return;
        UUID u = e.getPlayer().getUniqueId();
        if (!wm.isAlive(u)) return;
        Location to = e.getTo();
        if (to == null) return;
        Location from = e.getFrom();
        if (from.getX() != to.getX() || from.getZ() != to.getZ()) e.setCancelled(true);
    }

    // ----- Chat team -----
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (plugin.getGui().isPrompting(p.getUniqueId())) return;
        TeamManager tm = plugin.getTeams();
        if (!tm.isChatMode(p.getUniqueId())) return;
        Team t = tm.getTeam(p.getUniqueId());
        if (t == null) { tm.disableChat(p.getUniqueId()); return; }
        e.setCancelled(true);
        tm.sendTeamChat(p, t, e.getMessage());
    }
}
