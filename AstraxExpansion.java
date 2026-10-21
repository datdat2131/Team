package com.astraxbox.team;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Placeholder:
 *  %astraxteam_name% %astraxteam_role% %astraxteam_leader% %astraxteam_members% %astraxteam_maxmembers%
 *  %astraxteam_online% %astraxteam_points% %astraxteam_kills% %astraxteam_deaths% %astraxteam_kdr%
 *  %astraxteam_wins% %astraxteam_losses% %astraxteam_rank% %astraxteam_pvp% %astraxteam_zones%
 *  %astraxteam_top_<n>_name%   %astraxteam_top_<n>_value%   (theo leaderboard.metric)
 */
public class AstraxExpansion extends PlaceholderExpansion {
    private final AstraxTeam plugin;

    public AstraxExpansion(AstraxTeam plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "astraxteam"; }
    @Override public String getAuthor() { return "AstraxBox"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        TeamManager tm = plugin.getTeams();

        if (key.startsWith("top_")) {
            String[] sp = key.split("_");
            if (sp.length < 3) return "";
            int n;
            try { n = Integer.parseInt(sp[1]); } catch (NumberFormatException e) { return ""; }
            if (n < 1) return "";
            List<Team> top = tm.top(tm.metric(), n);
            if (top.size() < n) return plugin.getConfig().getString("placeholders.empty-rank", "-");
            Team t = top.get(n - 1);
            return sp[2].equals("name") ? t.getName() : String.valueOf(t.value(tm.metric()));
        }

        if (player == null) return "";
        UUID id = player.getUniqueId();
        Team t = tm.getTeam(id);
        if (t == null) return plugin.getConfig().getString("placeholders.no-team", "");

        return switch (key) {
            case "name", "tag" -> t.getName();
            case "role" -> plugin.msg().role(t.getRole(id));
            case "leader" -> t.getLeader() == null ? "" : t.getMemberName(t.getLeader());
            case "members" -> String.valueOf(t.size());
            case "maxmembers" -> String.valueOf(tm.maxMembers());
            case "online" -> String.valueOf(t.getOnlineMembers().size());
            case "points" -> String.valueOf(t.getPoints());
            case "kills" -> String.valueOf(t.getKills());
            case "deaths" -> String.valueOf(t.getDeaths());
            case "kdr" -> String.format(Locale.US, "%.2f", t.kdr());
            case "wins" -> String.valueOf(t.getWins());
            case "losses" -> String.valueOf(t.getLosses());
            case "rank" -> String.valueOf(tm.rankOf(t));
            case "pvp" -> t.isPvp() ? "ON" : "OFF";
            case "zones" -> String.valueOf(plugin.getZones().ownedBy(t.getId()).size());
            default -> null;
        };
    }
}
