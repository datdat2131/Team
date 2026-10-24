package com.astraxbox.team;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class TeamCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("create", "disband", "rename", "invite", "accept", "deny",
            "kick", "leave", "promote", "demote", "transfer", "info", "list", "pvp", "chat", "claim", "unclaim",
            "zones", "war", "top", "gui", "chest", "echest", "help");
    private static final List<String> METRICS = List.of("points", "kills", "wins");

    private final AstraxTeam plugin;

    public TeamCommand(AstraxTeam plugin) { this.plugin = plugin; }

    private Messages m() { return plugin.msg(); }
    private TeamManager tm() { return plugin.getTeams(); }
    private FileConfiguration cfg() { return plugin.getConfig(); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { m().send(s, "only-player"); return true; }
        String sub = a.length == 0 ? (cfg().getBoolean("gui.enabled", true) ? "gui" : "help") : a[0].toLowerCase();
        Team mine = tm().getTeam(p.getUniqueId());
        switch (sub) {
            case "create" -> create(p, mine, a);
            case "disband" -> disband(p, mine);
            case "rename" -> rename(p, mine, a);
            case "invite" -> invite(p, mine, a);
            case "accept" -> accept(p, mine);
            case "deny" -> deny(p);
            case "kick" -> kick(p, mine, a);
            case "leave" -> leave(p, mine);
            case "promote" -> setRank(p, mine, a, true);
            case "demote" -> setRank(p, mine, a, false);
            case "transfer" -> transfer(p, mine, a);
            case "info" -> info(p, mine, a);
            case "list" -> list(p);
            case "pvp" -> pvp(p, mine, a);
            case "chat" -> chat(p, mine, a);
            case "claim" -> claim(p, mine, a);
            case "unclaim" -> unclaim(p, mine, a);
            case "zones" -> zones(p);
            case "war" -> war(p, mine, a);
            case "top" -> top(p, a);
            case "gui", "menu" -> plugin.getGui().open(p);
            case "chest", "echest", "ec" -> plugin.getChests().open(p);
            default -> help(p);
        }
        return true;
    }

    // ---------- Tiện ích ----------
    private boolean needTeam(Player p, Team mine) {
        if (mine == null) { m().send(p, "no-team"); return false; }
        return true;
    }

    private boolean can(Player p, Team mine, String action, Role def) {
        Role min = Role.parse(cfg().getString("permissions." + action), def);
        Role r = mine.getRole(p.getUniqueId());
        if (r == null || !r.atLeast(min)) {
            m().send(p, "no-permission-role", "role", m().role(min));
            return false;
        }
        return true;
    }

    private boolean usage(Player p, String u) {
        m().send(p, "usage", "usage", u);
        return false;
    }

    private boolean validName(Player p, String name, Team self) {
        int min = cfg().getInt("settings.min-name-length", 3);
        int max = cfg().getInt("settings.max-name-length", 16);
        if (name.length() < min || name.length() > max
                || !name.matches(cfg().getString("settings.name-regex", "[A-Za-z0-9_]+"))) {
            m().send(p, "invalid-name", "min", String.valueOf(min), "max", String.valueOf(max));
            return false;
        }
        for (String b : cfg().getStringList("settings.blocked-names")) {
            if (b.equalsIgnoreCase(name)) { m().send(p, "name-blocked"); return false; }
        }
        Team ex = tm().getTeamByName(name);
        if (ex != null && ex != self) { m().send(p, "name-taken"); return false; }
        return true;
    }

    // ---------- Team ----------
    private void create(Player p, Team mine, String[] a) {
        if (mine != null) { m().send(p, "already-in-team"); return; }
        if (a.length < 2) { usage(p, "/team create <tên>"); return; }
        if (!validName(p, a[1], null)) return;
        int max = cfg().getInt("settings.max-teams", 0);
        if (max > 0 && tm().all().size() >= max) { m().send(p, "max-teams"); return; }
        Team t = tm().create(p, a[1]);
        m().send(p, "team-created", "team", t.getName());
    }

    private void disband(Player p, Team mine) {
        if (!needTeam(p, mine) || !can(p, mine, "disband", Role.LEADER)) return;
        if (plugin.getWars().isLocked(p.getUniqueId())) { m().send(p, "war-locked"); return; }
        if (cfg().getBoolean("chest.block-disband-if-not-empty", true) && plugin.getChests().isNotEmpty(mine)) {
            m().send(p, "chest-not-empty");
            return;
        }
        tm().broadcast(mine, "team-disbanded", "team", mine.getName());
        tm().disband(mine);
    }

    private void rename(Player p, Team mine, String[] a) {
        if (!needTeam(p, mine) || !can(p, mine, "rename", Role.LEADER)) return;
        if (a.length < 2) { usage(p, "/team rename <tên>"); return; }
        if (!validName(p, a[1], mine)) return;
        tm().rename(mine, a[1]);
        tm().broadcast(mine, "team-renamed", "team", mine.getName());
    }

    // ---------- Thành viên ----------
    private void invite(Player p, Team mine, String[] a) {
        if (!needTeam(p, mine) || !can(p, mine, "invite", Role.CO_LEADER)) return;
        if (a.length < 2) { usage(p, "/team invite <người chơi>"); return; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { m().send(p, "player-offline", "player", a[1]); return; }
        if (tm().getTeam(t.getUniqueId()) != null) { m().send(p, "target-has-team"); return; }
        if (mine.size() >= tm().maxMembers()) { m().send(p, "team-full", "max", String.valueOf(tm().maxMembers())); return; }
        long exp = System.currentTimeMillis() + cfg().getInt("settings.invite-expire-seconds", 60) * 1000L;
        tm().addInvite(t.getUniqueId(), new TeamManager.Invite(mine.getId(), p.getUniqueId(), exp));
        m().send(p, "invite-sent", "player", t.getName());
        m().send(t, "invite-received", "player", p.getName(), "team", mine.getName());
    }

    private void accept(Player p, Team mine) {
        TeamManager.Invite inv = tm().popInvite(p.getUniqueId());
        Team t = inv == null ? null : tm().getTeamById(inv.teamId());
        if (t == null) { m().send(p, "invite-none"); return; }
        if (mine != null) { m().send(p, "already-in-team"); return; }
        if (t.size() >= tm().maxMembers()) { m().send(p, "team-full", "max", String.valueOf(tm().maxMembers())); return; }
        tm().join(t, p);
        tm().broadcast(t, "joined", "player", p.getName());
    }

    private void deny(Player p) {
        TeamManager.Invite inv = tm().popInvite(p.getUniqueId());
        if (inv == null) { m().send(p, "invite-none"); return; }
        m().send(p, "invite-denied-self");
        Player inviter = Bukkit.getPlayer(inv.inviter());
        if (inviter != null) m().send(inviter, "invite-denied", "player", p.getName());
    }

    private void kick(Player p, Team mine, String[] a) {
        if (!needTeam(p, mine) || !can(p, mine, "kick", Role.CO_LEADER)) return;
        if (a.length < 2) { usage(p, "/team kick <tên>"); return; }
        UUID target = mine.findMember(a[1]);
        if (target == null) { m().send(p, "member-not-found", "player", a[1]); return; }
        if (target.equals(p.getUniqueId())) { m().send(p, "cannot-target-self"); return; }
        if (plugin.getWars().isLocked(target)) { m().send(p, "war-locked"); return; }
        if (mine.getRole(target).getRank() >= mine.getRole(p.getUniqueId()).getRank()) {
            m().send(p, "cannot-kick-higher");
            return;
        }
        String name = mine.getMemberName(target);
        tm().leave(mine, target);
        Player tp = Bukkit.getPlayer(target);
        if (tp != null) m().send(tp, "kicked-self", "team", mine.getName());
        tm().broadcast(mine, "kicked-broadcast", "player", name, "by", p.getName());
    }

    private void leave(Player p, Team mine) {
        if (!needTeam(p, mine)) return;
        if (mine.getRole(p.getUniqueId()) == Role.LEADER) { m().send(p, "leader-cannot-leave"); return; }
        if (plugin.getWars().isLocked(p.getUniqueId())) { m().send(p, "war-locked"); return; }
        tm().leave(mine, p.getUniqueId());
        m().send(p, "left-self", "team", mine.getName());
        tm().broadcast(mine, "left-broadcast", "player", p.getName());
    }

    private void setRank(Player p, Team mine, String[] a, boolean promote) {
        if (!needTeam(p, mine) || !can(p, mine, promote ? "promote" : "demote", Role.LEADER)) return;
        if (a.length < 2) { usage(p, "/team " + (promote ? "promote" : "demote") + " <tên>"); return; }
        UUID target = mine.findMember(a[1]);
        if (target == null) { m().send(p, "member-not-found", "player", a[1]); return; }
        Role cur = mine.getRole(target);
        if (promote) {
            if (cur != Role.MEMBER) { m().send(p, "cannot-promote"); return; }
            mine.setRole(target, Role.CO_LEADER);
            tm().broadcast(mine, "promoted", "player", mine.getMemberName(target), "role", m().role(Role.CO_LEADER));
        } else {
            if (cur != Role.CO_LEADER) { m().send(p, "cannot-demote"); return; }
            mine.setRole(target, Role.MEMBER);
            tm().broadcast(mine, "demoted", "player", mine.getMemberName(target), "role", m().role(Role.MEMBER));
        }
        tm().markDirty();
    }

    private void transfer(Player p, Team mine, String[] a) {
        if (!needTeam(p, mine) || !can(p, mine, "transfer", Role.LEADER)) return;
        if (a.length < 2) { usage(p, "/team transfer <tên>"); return; }
        UUID target = mine.findMember(a[1]);
        if (target == null) { m().send(p, "member-not-found", "player", a[1]); return; }
        if (target.equals(p.getUniqueId())) { m().send(p, "cannot-target-self"); return; }
        mine.setRole(target, Role.LEADER);
        mine.setRole(p.getUniqueId(), Role.CO_LEADER);
        tm().markDirty();
        tm().broadcast(mine, "transferred", "player", mine.getMemberName(target));
    }

    // ---------- Thông tin ----------
    private void info(Player p, Team mine, String[] a) {
        Team t;
        if (a.length >= 2) {
            t = tm().getTeamByName(a[1]);
            if (t == null) {
                Player op = Bukkit.getPlayerExact(a[1]);
                if (op != null) t = tm().getTeam(op.getUniqueId());
            }
            if (t == null) { m().send(p, "team-not-found"); return; }
        } else {
            if (!needTeam(p, mine)) return;
            t = mine;
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<UUID, Role> e : t.getMembers().entrySet()) {
            boolean online = Bukkit.getPlayer(e.getKey()) != null;
            String tag = switch (e.getValue()) {
                case LEADER -> "&6★";
                case CO_LEADER -> "&e☆";
                default -> "";
            };
            parts.add(tag + (online ? "&a" : "&7") + t.getMemberName(e.getKey()));
        }
        String zones = plugin.getZones().ownedBy(t.getId()).stream().map(z -> z.name).collect(Collectors.joining(", "));
        if (zones.isEmpty()) zones = "-";
        UUID leader = t.getLeader();
        for (String line : m().list("info",
                "team", t.getName(),
                "leader", leader == null ? "?" : t.getMemberName(leader),
                "rank", String.valueOf(tm().rankOf(t)),
                "count", String.valueOf(t.size()),
                "max", String.valueOf(tm().maxMembers()),
                "online", String.valueOf(t.getOnlineMembers().size()),
                "members", Messages.color(String.join("&7, ", parts)),
                "points", String.valueOf(t.getPoints()),
                "kills", String.valueOf(t.getKills()),
                "deaths", String.valueOf(t.getDeaths()),
                "kdr", String.format(Locale.US, "%.2f", t.kdr()),
                "wins", String.valueOf(t.getWins()),
                "losses", String.valueOf(t.getLosses()),
                "pvp", m().raw(t.isPvp() ? "state-on" : "state-off"),
                "zones", zones)) {
            p.sendMessage(line);
        }
    }

    private void list(Player p) {
        List<Team> teams = tm().top("POINTS", 20);
        if (teams.isEmpty()) { m().send(p, "top-empty"); return; }
        p.sendMessage(m().raw("list-header"));
        for (Team t : teams) {
            p.sendMessage(m().raw("list-line", "team", t.getName(),
                    "count", String.valueOf(t.size()), "points", String.valueOf(t.getPoints())));
        }
    }

    private void top(Player p, String[] a) {
        String metric = a.length > 1 ? a[1].toUpperCase() : tm().metric();
        if (!metric.equals("POINTS") && !metric.equals("KILLS") && !metric.equals("WINS")) metric = tm().metric();
        List<Team> top = tm().top(metric, cfg().getInt("leaderboard.top-size", 10));
        if (top.isEmpty()) { m().send(p, "top-empty"); return; }
        p.sendMessage(m().raw("top-header", "metric", m().raw("metric-" + metric.toLowerCase())));
        for (int i = 0; i < top.size(); i++) {
            Team t = top.get(i);
            p.sendMessage(m().raw("top-line", "rank", String.valueOf(i + 1), "team", t.getName(),
                    "value", String.valueOf(t.value(metric))));
        }
    }

    private void help(Player p) {
        for (String line : m().list("help")) p.sendMessage(line);
    }

    // ---------- PvP / chat ----------
    private void pvp(Player p, Team mine, String[] a) {
        if (!needTeam(p, mine) || !can(p, mine, "pvp", Role.CO_LEADER)) return;
        boolean val;
        if (a.length >= 2) {
            if (a[1].equalsIgnoreCase("on")) val = true;
            else if (a[1].equalsIgnoreCase("off")) val = false;
            else { usage(p, "/team pvp [on|off]"); return; }
        } else {
            val = !mine.isPvp();
        }
        mine.setPvp(val);
        tm().markDirty();
        tm().broadcast(mine, "pvp-changed", "state", m().raw(val ? "state-on" : "state-off"), "player", p.getName());
    }

    private void chat(Player p, Team mine, String[] a) {
        if (!needTeam(p, mine)) return;
        if (a.length == 1) {
            boolean on = tm().toggleChat(p.getUniqueId());
            m().send(p, on ? "chat-mode-on" : "chat-mode-off");
        } else {
            tm().sendTeamChat(p, mine, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
        }
    }

    // ---------- Claim ----------
    private void claim(Player p, Team mine, String[] a) {
        if (!cfg().getBoolean("claims.enabled", true)) { m().send(p, "claims-disabled"); return; }
        if (!needTeam(p, mine) || !can(p, mine, "claim", Role.CO_LEADER)) return;
        if (a.length < 2) { usage(p, "/team claim <zone>"); return; }
        ZoneManager.Zone z = plugin.getZones().get(a[1]);
        if (z == null) { m().send(p, "zone-not-found", "zone", a[1]); return; }
        if (z.owner != null) { m().send(p, "claim-fail-owned"); return; }
        int max = cfg().getInt("claims.max-zones-per-team", 1);
        if (max > 0 && plugin.getZones().ownedBy(mine.getId()).size() >= max) {
            m().send(p, "claim-fail-limit", "max", String.valueOf(max));
            return;
        }
        if (cfg().getBoolean("claims.require-standing-inside", false) && !z.contains(p.getLocation())) {
            m().send(p, "claim-not-inside");
            return;
        }
        z.owner = mine.getId();
        plugin.getZones().save();
        tm().broadcast(mine, "claim-success", "player", p.getName(), "zone", z.name);
    }

    private void unclaim(Player p, Team mine, String[] a) {
        if (!needTeam(p, mine) || !can(p, mine, "unclaim", Role.CO_LEADER)) return;
        if (a.length < 2) { usage(p, "/team unclaim <zone>"); return; }
        ZoneManager.Zone z = plugin.getZones().get(a[1]);
        if (z == null) { m().send(p, "zone-not-found", "zone", a[1]); return; }
        if (!mine.getId().equals(z.owner)) { m().send(p, "not-your-zone"); return; }
        z.owner = null;
        plugin.getZones().save();
        tm().broadcast(mine, "unclaim-success", "zone", z.name);
    }

    private void zones(Player p) {
        if (plugin.getZones().all().isEmpty()) { m().send(p, "zones-empty"); return; }
        p.sendMessage(m().raw("zones-header"));
        for (ZoneManager.Zone z : plugin.getZones().all()) {
            Team o = z.owner == null ? null : tm().getTeamById(z.owner);
            p.sendMessage(m().raw("zones-line", "zone", z.name,
                    "owner", o == null ? m().raw("free") : "&e" + o.getName()));
        }
    }

    // ---------- War ----------
    private void war(Player p, Team mine, String[] a) {
        if (!needTeam(p, mine)) return;
        String sub = a.length > 1 ? a[1].toLowerCase() : "status";
        WarManager wm = plugin.getWars();
        switch (sub) {
            case "declare" -> {
                if (!can(p, mine, "war", Role.CO_LEADER)) return;
                if (a.length < 3) { usage(p, "/team war declare <team>"); return; }
                Team target = tm().getTeamByName(a[2]);
                if (target == null) { m().send(p, "team-not-found"); return; }
                wm.declare(p, mine, target);
            }
            case "accept" -> { if (can(p, mine, "war", Role.CO_LEADER)) wm.accept(p, mine); }
            case "deny" -> { if (can(p, mine, "war", Role.CO_LEADER)) wm.deny(p, mine); }
            default -> wm.status(p);
        }
    }

    // ---------- Tab ----------
    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return List.of();
        Team mine = tm().getTeam(p.getUniqueId());
        if (a.length == 1) return filter(SUBS, a[0]);
        String sub = a[0].toLowerCase();
        if (a.length == 2) {
            switch (sub) {
                case "invite":
                    return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), a[1]);
                case "kick": case "promote": case "demote": case "transfer":
                    if (mine == null) return List.of();
                    return filter(mine.getMembers().keySet().stream().map(mine::getMemberName).toList(), a[1]);
                case "info":
                    return filter(tm().all().stream().map(Team::getName).toList(), a[1]);
                case "pvp":
                    return filter(List.of("on", "off"), a[1]);
                case "top":
                    return filter(METRICS, a[1]);
                case "claim": case "unclaim":
                    return filter(plugin.getZones().all().stream().map(z -> z.name).toList(), a[1]);
                case "war":
                    return filter(List.of("declare", "accept", "deny", "status"), a[1]);
                default:
                    return List.of();
            }
        }
        if (a.length == 3 && sub.equals("war") && a[1].equalsIgnoreCase("declare"))
            return filter(tm().all().stream().map(Team::getName).toList(), a[2]);
        return List.of();
    }

    private List<String> filter(List<String> src, String prefix) {
        String pre = prefix.toLowerCase();
        return src.stream().filter(x -> x.toLowerCase().startsWith(pre)).collect(Collectors.toList());
    }
}
