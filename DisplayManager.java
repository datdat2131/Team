package com.astraxbox.team;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;

public class DisplayManager {
    private static final String SB_PREFIX = "axt";

    private final AstraxTeam plugin;
    private final Set<UUID> glowing = new HashSet<>();
    private BukkitTask task;

    public DisplayManager(AstraxTeam plugin) { this.plugin = plugin; }

    public void start() {
        stop();
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("display.hud.enabled", true)) return;
        long iv = Math.max(5, c.getInt("display.hud.interval-ticks", 20));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::hud, 20L, iv);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    public void shutdown() {
        stop();
        for (Player p : Bukkit.getOnlinePlayers()) clear(p);
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) refresh(p);
    }

    private void removeEntries(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (org.bukkit.scoreboard.Team st : new ArrayList<>(sb.getTeams())) {
            if (st.getName().startsWith(SB_PREFIX) && st.hasEntry(p.getName())) {
                st.removeEntry(p.getName());
                if (st.getEntries().isEmpty()) st.unregister();
            }
        }
    }

    public void clear(Player p) {
        removeEntries(p);
        if (glowing.remove(p.getUniqueId())) p.setGlowing(false);
    }

    private ChatColor parseColor(String s) {
        try {
            return ChatColor.valueOf(s.toUpperCase());
        } catch (Exception e) {
            return ChatColor.GREEN;
        }
    }

    /** Cập nhật glow + tiền tố tên cho 1 người chơi theo team hiện tại. */
    public void refresh(Player p) {
        FileConfiguration c = plugin.getConfig();
        removeEntries(p);
        Team t = plugin.getTeams().getTeam(p.getUniqueId());
        boolean glow = false;
        if (t != null) {
            boolean glowCfg = c.getBoolean("display.glow.enabled", true)
                    && (!c.getBoolean("display.glow.only-in-war", false) || plugin.getWars().isAlive(p.getUniqueId()));
            boolean prefix = c.getBoolean("display.nametag-prefix.enabled", true);
            if (glowCfg || prefix) {
                Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
                String sn = SB_PREFIX + Integer.toHexString(t.getId().hashCode());
                org.bukkit.scoreboard.Team st = sb.getTeam(sn);
                if (st == null) st = sb.registerNewTeam(sn);
                st.setColor(parseColor(c.getString("display.glow.color", "GREEN")));
                st.setPrefix(prefix
                        ? Messages.color(c.getString("display.nametag-prefix.format", "&a[{team}] ").replace("{team}", t.getName()))
                        : "");
                st.addEntry(p.getName());
            }
            glow = glowCfg;
        }
        if (glow) {
            p.setGlowing(true);
            glowing.add(p.getUniqueId());
        } else if (glowing.remove(p.getUniqueId())) {
            p.setGlowing(false);
        }
    }

    // ---------- HUD ----------
    private void hud() {
        FileConfiguration c = plugin.getConfig();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String text = plugin.getWars().hudFor(p);
            if (text == null) text = mates(p, c);
            if (text != null) Messages.actionBar(p, text);
        }
    }

    private String mates(Player p, FileConfiguration c) {
        Team t = plugin.getTeams().getTeam(p.getUniqueId());
        if (t == null) return null;
        double maxD = c.getDouble("display.hud.max-distance", 100);
        int shown = c.getInt("display.hud.max-shown", 3);
        List<Player> list = new ArrayList<>();
        for (Player o : t.getOnlineMembers()) {
            if (o.equals(p) || !o.getWorld().equals(p.getWorld())) continue;
            if (maxD > 0 && o.getLocation().distance(p.getLocation()) > maxD) continue;
            list.add(o);
        }
        if (list.isEmpty()) return null;
        list.sort(Comparator.comparingDouble(o -> o.getLocation().distanceSquared(p.getLocation())));
        String fmt = c.getString("display.hud.format", "&a{player} &c❤{health} &7({distance}m)");
        String sep = c.getString("display.hud.separator", " &8| ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(shown, list.size()); i++) {
            Player o = list.get(i);
            if (i > 0) sb.append(sep);
            sb.append(fmt
                    .replace("{player}", o.getName())
                    .replace("{health}", String.format(Locale.US, "%.1f", o.getHealth() / 2.0))
                    .replace("{distance}", String.valueOf((int) o.getLocation().distance(p.getLocation()))));
        }
        return Messages.color(sb.toString());
    }
}
