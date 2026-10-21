package com.astraxbox.team;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class TeamAdminCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("reload", "arena", "zone", "disband", "setpoints", "war", "help");

    private final AstraxTeam plugin;

    public TeamAdminCommand(AstraxTeam plugin) { this.plugin = plugin; }

    private Messages m() { return plugin.msg(); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 0) { help(s); return true; }
        switch (a[0].toLowerCase()) {
            case "reload" -> { plugin.reloadAll(); m().send(s, "reloaded"); }
            case "arena" -> arena(s, a);
            case "zone" -> zone(s, a);
            case "disband" -> disband(s, a);
            case "setpoints" -> setPoints(s, a);
            case "war" -> {
                if (a.length > 1 && a[1].equalsIgnoreCase("stop")) {
                    m().send(s, plugin.getWars().stop() ? "admin-war-stopped" : "admin-war-none");
                } else {
                    m().send(s, "usage", "usage", "/teamadmin war stop");
                }
            }
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        for (String line : m().list("admin-help")) s.sendMessage(line);
    }

    private String fmt(Location l) {
        if (l == null || l.getWorld() == null) return "-";
        return l.getWorld().getName() + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private void arena(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase() : "info";
        switch (sub) {
            case "set1", "set2" -> {
                if (!(s instanceof Player p)) { m().send(s, "only-player"); return; }
                int n = sub.equals("set1") ? 1 : 2;
                plugin.getWars().setSpawn(n, p.getLocation());
                m().send(s, "admin-spawn-set", "n", String.valueOf(n));
            }
            default -> m().send(s, "admin-arena-info",
                    "s1", fmt(plugin.getWars().getSpawn(1)), "s2", fmt(plugin.getWars().getSpawn(2)));
        }
    }

    private void zone(CommandSender s, String[] a) {
        ZoneManager zm = plugin.getZones();
        String sub = a.length > 1 ? a[1].toLowerCase() : "list";
        switch (sub) {
            case "pos1", "pos2" -> {
                if (!(s instanceof Player p)) { m().send(s, "only-player"); return; }
                int n = sub.equals("pos1") ? 1 : 2;
                zm.setPos(p.getUniqueId(), n, p.getLocation());
                m().send(s, "admin-pos-set", "n", String.valueOf(n),
                        "x", String.valueOf(p.getLocation().getBlockX()), "z", String.valueOf(p.getLocation().getBlockZ()));
            }
            case "create" -> {
                if (!(s instanceof Player p)) { m().send(s, "only-player"); return; }
                if (a.length < 3) { m().send(s, "usage", "usage", "/teamadmin zone create <tên>"); return; }
                if (zm.get(a[2]) != null) { m().send(s, "admin-zone-exists"); return; }
                ZoneManager.Zone z = zm.create(p.getUniqueId(), a[2]);
                if (z == null) { m().send(s, "admin-sel-missing"); return; }
                m().send(s, "admin-zone-created", "zone", z.name);
            }
            case "delete" -> {
                if (a.length < 3) { m().send(s, "usage", "usage", "/teamadmin zone delete <tên>"); return; }
                if (!zm.delete(a[2])) { m().send(s, "zone-not-found", "zone", a[2]); return; }
                m().send(s, "admin-zone-deleted", "zone", a[2]);
            }
            case "release" -> {
                if (a.length < 3) { m().send(s, "usage", "usage", "/teamadmin zone release <tên>"); return; }
                ZoneManager.Zone z = zm.get(a[2]);
                if (z == null) { m().send(s, "zone-not-found", "zone", a[2]); return; }
                z.owner = null;
                zm.save();
                m().send(s, "admin-zone-released", "zone", z.name);
            }
            default -> {
                if (zm.all().isEmpty()) { m().send(s, "zones-empty"); return; }
                s.sendMessage(m().raw("zones-header"));
                for (ZoneManager.Zone z : zm.all()) {
                    Team o = z.owner == null ? null : plugin.getTeams().getTeamById(z.owner);
                    s.sendMessage(m().raw("zones-line", "zone", z.name,
                            "owner", o == null ? m().raw("free") : "&e" + o.getName()));
                }
            }
        }
    }

    private void disband(CommandSender s, String[] a) {
        if (a.length < 2) { m().send(s, "usage", "usage", "/teamadmin disband <team>"); return; }
        Team t = plugin.getTeams().getTeamByName(a[1]);
        if (t == null) { m().send(s, "team-not-found"); return; }
        plugin.getTeams().broadcast(t, "team-disbanded", "team", t.getName());
        plugin.getTeams().disband(t);
        m().send(s, "admin-disbanded", "team", t.getName());
    }

    private void setPoints(CommandSender s, String[] a) {
        if (a.length < 3) { m().send(s, "usage", "usage", "/teamadmin setpoints <team> <số>"); return; }
        Team t = plugin.getTeams().getTeamByName(a[1]);
        if (t == null) { m().send(s, "team-not-found"); return; }
        int v;
        try { v = Integer.parseInt(a[2]); } catch (NumberFormatException e) {
            m().send(s, "usage", "usage", "/teamadmin setpoints <team> <số>");
            return;
        }
        t.setPoints(v);
        plugin.getTeams().markDirty();
        m().send(s, "admin-points-set", "team", t.getName(), "points", String.valueOf(t.getPoints()));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return filter(SUBS, a[0]);
        String sub = a[0].toLowerCase();
        if (a.length == 2) {
            switch (sub) {
                case "arena": return filter(List.of("set1", "set2", "info"), a[1]);
                case "zone": return filter(List.of("pos1", "pos2", "create", "delete", "release", "list"), a[1]);
                case "war": return filter(List.of("stop"), a[1]);
                case "disband": case "setpoints":
                    return filter(plugin.getTeams().all().stream().map(Team::getName).toList(), a[1]);
                default: return List.of();
            }
        }
        if (a.length == 3 && sub.equals("zone") && (a[1].equalsIgnoreCase("delete") || a[1].equalsIgnoreCase("release")))
            return filter(plugin.getZones().all().stream().map(z -> z.name).toList(), a[2]);
        return List.of();
    }

    private List<String> filter(List<String> src, String prefix) {
        String pre = prefix.toLowerCase();
        return src.stream().filter(x -> x.toLowerCase().startsWith(pre)).collect(Collectors.toList());
    }
}
