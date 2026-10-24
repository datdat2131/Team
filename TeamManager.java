package com.astraxbox.team;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamManager {
    public record Invite(UUID teamId, UUID inviter, long expires) {}

    private final AstraxTeam plugin;
    private final Map<UUID, Team> teams = new LinkedHashMap<>();
    private final Map<String, Team> byName = new HashMap<>();
    private final Map<UUID, Team> byPlayer = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Set<UUID> chatMode = new HashSet<>();
    private boolean dirty;
    private BukkitTask autosave;

    public TeamManager(AstraxTeam plugin) { this.plugin = plugin; }

    // ---------- Truy vấn ----------
    public Team getTeam(UUID player) { return byPlayer.get(player); }
    public Team getTeamByName(String name) { return name == null ? null : byName.get(name.toLowerCase()); }
    public Team getTeamById(UUID id) { return teams.get(id); }
    public Collection<Team> all() { return teams.values(); }
    public void markDirty() { dirty = true; }
    public int maxMembers() { return plugin.getConfig().getInt("settings.max-members", 10); }

    public boolean sameTeam(UUID a, UUID b) {
        Team t = byPlayer.get(a);
        return t != null && t == byPlayer.get(b);
    }

    public String metric() {
        String m = plugin.getConfig().getString("leaderboard.metric", "POINTS").toUpperCase();
        return (m.equals("KILLS") || m.equals("WINS")) ? m : "POINTS";
    }

    public List<Team> top(String metric, int limit) {
        List<Team> list = new ArrayList<>(teams.values());
        list.sort((x, y) -> {
            int c = Integer.compare(y.value(metric), x.value(metric));
            if (c != 0) return c;
            c = Integer.compare(y.getPoints(), x.getPoints());
            return c != 0 ? c : x.getName().compareToIgnoreCase(y.getName());
        });
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }

    public int rankOf(Team t) {
        return top(metric(), Integer.MAX_VALUE).indexOf(t) + 1;
    }

    // ---------- Thao tác ----------
    public Team create(Player p, String name) {
        Team t = new Team(UUID.randomUUID(), name);
        t.setPvp(plugin.getConfig().getBoolean("settings.default-pvp", false));
        t.addMember(p.getUniqueId(), p.getName(), Role.LEADER);
        teams.put(t.getId(), t);
        byName.put(name.toLowerCase(), t);
        byPlayer.put(p.getUniqueId(), t);
        dirty = true;
        plugin.getDisplay().refresh(p);
        return t;
    }

    public void join(Team t, Player p) {
        t.addMember(p.getUniqueId(), p.getName(), Role.MEMBER);
        byPlayer.put(p.getUniqueId(), t);
        dirty = true;
        plugin.getDisplay().refresh(p);
    }

    public void leave(Team t, UUID u) {
        plugin.getChests().closeFor(u);
        t.removeMember(u);
        byPlayer.remove(u);
        chatMode.remove(u);
        dirty = true;
        Player p = Bukkit.getPlayer(u);
        if (p != null) plugin.getDisplay().refresh(p);
    }

    public void disband(Team t) {
        plugin.getWars().onTeamRemoved(t);
        plugin.getZones().releaseAll(t.getId());
        plugin.getChests().onDisband(t);
        List<UUID> ids = new ArrayList<>(t.getMembers().keySet());
        teams.remove(t.getId());
        byName.remove(t.getName().toLowerCase());
        for (UUID id : ids) {
            byPlayer.remove(id);
            chatMode.remove(id);
        }
        invites.values().removeIf(i -> i.teamId().equals(t.getId()));
        dirty = true;
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.getDisplay().refresh(p);
        }
    }

    public void rename(Team t, String newName) {
        byName.remove(t.getName().toLowerCase());
        t.setName(newName);
        byName.put(newName.toLowerCase(), t);
        dirty = true;
        for (Player p : t.getOnlineMembers()) plugin.getDisplay().refresh(p);
    }

    // ---------- Lời mời ----------
    public void addInvite(UUID target, Invite inv) { invites.put(target, inv); }

    public Invite peekInvite(UUID target) {
        Invite inv = invites.get(target);
        return (inv == null || inv.expires() < System.currentTimeMillis()) ? null : inv;
    }

    public Invite popInvite(UUID target) {
        Invite inv = invites.remove(target);
        if (inv == null || inv.expires() < System.currentTimeMillis()) return null;
        return inv;
    }

    // ---------- Chat & thông báo ----------
    public void broadcast(Team t, String key, String... kv) {
        String s = plugin.msg().prefixed(key, kv);
        if (s.isEmpty()) return;
        for (Player p : t.getOnlineMembers()) p.sendMessage(s);
    }

    public boolean toggleChat(UUID u) {
        if (chatMode.remove(u)) return false;
        chatMode.add(u);
        return true;
    }

    public boolean isChatMode(UUID u) { return chatMode.contains(u); }
    public void disableChat(UUID u) { chatMode.remove(u); }

    public void sendTeamChat(Player p, Team t, String message) {
        Role r = t.getRole(p.getUniqueId());
        String f = plugin.msg().raw("chat-format",
                "role", r == null ? "" : plugin.msg().role(r),
                "player", p.getName(), "team", t.getName());
        String out = f.replace("{message}", message);
        for (Player o : t.getOnlineMembers()) o.sendMessage(out);
        Bukkit.getConsoleSender().sendMessage("[TeamChat:" + t.getName() + "] " + p.getName() + ": " + message);
    }

    // ---------- Lưu / tải ----------
    public void startAutosave() {
        if (autosave != null) autosave.cancel();
        autosave = Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) save(); }, 6000L, 6000L);
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Team t : teams.values()) {
            String b = "teams." + t.getId() + ".";
            y.set(b + "name", t.getName());
            y.set(b + "pvp", t.isPvp());
            y.set(b + "points", t.getPoints());
            y.set(b + "kills", t.getKills());
            y.set(b + "deaths", t.getDeaths());
            y.set(b + "wins", t.getWins());
            y.set(b + "losses", t.getLosses());
            for (Map.Entry<UUID, Role> e : t.getMembers().entrySet()) {
                y.set(b + "members." + e.getKey() + ".role", e.getValue().name());
                y.set(b + "members." + e.getKey() + ".name", t.getMemberName(e.getKey()));
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(new File(plugin.getDataFolder(), "teams.yml"));
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Không lưu được teams.yml: " + ex.getMessage());
        }
    }

    public void load() {
        File f = new File(plugin.getDataFolder(), "teams.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection sec = y.getConfigurationSection("teams");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException ex) { continue; }
            Team t = new Team(id, s.getString("name", "Team"));
            t.setPvp(s.getBoolean("pvp"));
            t.setPoints(s.getInt("points"));
            t.setKills(s.getInt("kills"));
            t.setDeaths(s.getInt("deaths"));
            t.setWins(s.getInt("wins"));
            t.setLosses(s.getInt("losses"));
            ConfigurationSection ms = s.getConfigurationSection("members");
            if (ms != null) {
                for (String uk : ms.getKeys(false)) {
                    try {
                        UUID u = UUID.fromString(uk);
                        t.addMember(u, ms.getString(uk + ".name", "?"), Role.parse(ms.getString(uk + ".role"), Role.MEMBER));
                    } catch (IllegalArgumentException ignored) { }
                }
            }
            if (t.size() == 0) continue;
            teams.put(id, t);
            byName.put(t.getName().toLowerCase(), t);
            for (UUID u : t.getMembers().keySet()) byPlayer.put(u, t);
        }
    }
}
