package com.astraxbox.team;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/** Buff định kỳ cho toàn bộ thành viên online của team top 1-5. */
public class BuffManager {
    private final AstraxTeam plugin;
    private BukkitTask task;

    public BuffManager(AstraxTeam plugin) { this.plugin = plugin; }

    public void start() {
        stop();
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("leaderboard.buffs.enabled", true)) return;
        long iv = Math.max(1, c.getInt("leaderboard.buffs.interval-seconds", 10)) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::apply, 40L, iv);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void apply() {
        FileConfiguration c = plugin.getConfig();
        String metric = plugin.getTeams().metric();
        int min = c.getInt("leaderboard.buffs.min-value", 1);
        int dur = Math.max(1, c.getInt("leaderboard.buffs.duration-seconds", 20)) * 20;
        List<Team> top = plugin.getTeams().top(metric, 5);
        for (int i = 0; i < top.size(); i++) {
            Team t = top.get(i);
            if (t.value(metric) < min) continue;
            List<String> effects = c.getStringList("leaderboard.buffs.ranks." + (i + 1));
            if (effects.isEmpty()) continue;
            for (Player p : t.getOnlineMembers()) {
                for (String s : effects) {
                    String[] sp = s.split(":");
                    PotionEffectType type = PotionEffectType.getByName(sp[0].trim().toUpperCase());
                    if (type == null) continue;
                    int amp = 0;
                    if (sp.length > 1) {
                        try { amp = Integer.parseInt(sp[1].trim()); } catch (NumberFormatException ignored) { }
                    }
                    p.addPotionEffect(new PotionEffect(type, dur, amp, true, false, true));
                }
            }
        }
    }
}
