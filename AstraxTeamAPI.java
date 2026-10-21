package com.astraxbox.team;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.*;

/**
 * API cho plugin khác.
 *
 * Cách lấy:
 *   AstraxTeamAPI api = AstraxTeamAPI.get();                       // hoặc
 *   AstraxTeamAPI api = Bukkit.getServicesManager().load(AstraxTeamAPI.class);
 */
public class AstraxTeamAPI {
    private final AstraxTeam plugin;

    public AstraxTeamAPI(AstraxTeam plugin) { this.plugin = plugin; }

    /** @return API hoặc null nếu AstraxTeam chưa bật. */
    public static AstraxTeamAPI get() {
        RegisteredServiceProvider<AstraxTeamAPI> rsp = Bukkit.getServicesManager().getRegistration(AstraxTeamAPI.class);
        return rsp == null ? null : rsp.getProvider();
    }

    public Team getTeam(UUID player) { return plugin.getTeams().getTeam(player); }
    public Team getTeamByName(String name) { return plugin.getTeams().getTeamByName(name); }
    public Collection<Team> getAllTeams() { return Collections.unmodifiableCollection(plugin.getTeams().all()); }

    public boolean hasTeam(UUID player) { return getTeam(player) != null; }

    public String getTeamName(UUID player) {
        Team t = getTeam(player);
        return t == null ? null : t.getName();
    }

    public Role getRole(UUID player) {
        Team t = getTeam(player);
        return t == null ? null : t.getRole(player);
    }

    public boolean areTeammates(UUID a, UUID b) { return plugin.getTeams().sameTeam(a, b); }

    public List<UUID> getTeammates(UUID player) {
        Team t = getTeam(player);
        return t == null ? List.of() : new ArrayList<>(t.getMembers().keySet());
    }

    public int getPoints(UUID player) {
        Team t = getTeam(player);
        return t == null ? 0 : t.getPoints();
    }

    /** Cộng/trừ điểm cho team của người chơi. */
    public boolean addPoints(UUID player, int amount) {
        Team t = getTeam(player);
        if (t == null) return false;
        t.addPoints(amount);
        plugin.getTeams().markDirty();
        return true;
    }

    /** Hạng của team (theo metric trong config), 0 nếu không có team. */
    public int getRank(UUID player) {
        Team t = getTeam(player);
        return t == null ? 0 : plugin.getTeams().rankOf(t);
    }

    public List<Team> getTop(String metric, int limit) { return plugin.getTeams().top(metric, limit); }

    public boolean isInWar(UUID player) { return plugin.getWars().isParticipant(player); }

    /** Team đang sở hữu vị trí (zone claim) hoặc null. */
    public Team getZoneOwnerAt(org.bukkit.Location loc) {
        ZoneManager.Zone z = plugin.getZones().getAt(loc);
        return z == null || z.owner == null ? null : plugin.getTeams().getTeamById(z.owner);
    }
}
