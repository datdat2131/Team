package com.astraxbox.team;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class Team {
    private final UUID id;
    private String name;
    private boolean pvp;
    private int points, kills, deaths, wins, losses;
    private final Map<UUID, Role> members = new LinkedHashMap<>();
    private final Map<UUID, String> names = new HashMap<>();

    public Team(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isPvp() { return pvp; }
    public void setPvp(boolean pvp) { this.pvp = pvp; }

    public int getPoints() { return points; }
    public void setPoints(int v) { points = Math.max(0, v); }
    public void addPoints(int v) { points = Math.max(0, points + v); }
    public int getKills() { return kills; }
    public void setKills(int v) { kills = v; }
    public void addKills(int v) { kills += v; }
    public int getDeaths() { return deaths; }
    public void setDeaths(int v) { deaths = v; }
    public void addDeaths(int v) { deaths += v; }
    public int getWins() { return wins; }
    public void setWins(int v) { wins = v; }
    public void addWins(int v) { wins += v; }
    public int getLosses() { return losses; }
    public void setLosses(int v) { losses = v; }
    public void addLosses(int v) { losses += v; }

    public double kdr() { return deaths == 0 ? kills : (double) kills / deaths; }

    public int value(String metric) {
        return switch (metric.toUpperCase()) {
            case "KILLS" -> kills;
            case "WINS" -> wins;
            default -> points;
        };
    }

    public Map<UUID, Role> getMembers() { return members; }
    public int size() { return members.size(); }
    public boolean isMember(UUID u) { return members.containsKey(u); }
    public Role getRole(UUID u) { return members.get(u); }
    public void setRole(UUID u, Role r) { if (members.containsKey(u)) members.put(u, r); }

    public void addMember(UUID u, String playerName, Role r) {
        members.put(u, r);
        names.put(u, playerName);
    }

    public void removeMember(UUID u) { members.remove(u); }

    public String getMemberName(UUID u) {
        String n = names.get(u);
        return n == null ? "?" : n;
    }

    public void setLastName(UUID u, String n) { names.put(u, n); }

    public UUID getLeader() {
        for (Map.Entry<UUID, Role> e : members.entrySet())
            if (e.getValue() == Role.LEADER) return e.getKey();
        return null;
    }

    public UUID findMember(String name) {
        for (UUID u : members.keySet())
            if (getMemberName(u).equalsIgnoreCase(name)) return u;
        return null;
    }

    public List<Player> getOnlineMembers() {
        List<Player> list = new ArrayList<>();
        for (UUID u : members.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) list.add(p);
        }
        return list;
    }
}
