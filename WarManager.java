package com.astraxbox.team;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class WarManager {
    public enum State { COUNTDOWN, RUNNING, ENDED }

    public static class War {
        final Team a, b;
        int killsA, killsB, countdown, timeLeft;
        State state = State.COUNTDOWN;
        final Set<UUID> partsA = new HashSet<>(), partsB = new HashSet<>();
        final Set<UUID> aliveA = new HashSet<>(), aliveB = new HashSet<>();
        final Map<UUID, Location> back = new HashMap<>();
        BukkitTask task;

        War(Team a, Team b) { this.a = a; this.b = b; }

        boolean isAlive(UUID u) { return aliveA.contains(u) || aliveB.contains(u); }
        boolean isPart(UUID u) { return partsA.contains(u) || partsB.contains(u); }
        Set<UUID> allParts() {
            Set<UUID> s = new HashSet<>(partsA);
            s.addAll(partsB);
            return s;
        }
    }

    public record Request(UUID fromTeam, long expires) {}

    private final AstraxTeam plugin;
    private War current;
    private final Map<UUID, Request> requests = new HashMap<>();   // key = team được thách
    private final Map<UUID, Location> respawn = new HashMap<>();
    private Location spawn1, spawn2;

    public WarManager(AstraxTeam plugin) { this.plugin = plugin; }

    // ---------- Arena ----------
    private File arenaFile() { return new File(plugin.getDataFolder(), "arena.yml"); }

    public void loadArena() {
        File f = arenaFile();
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        spawn1 = y.getLocation("spawn1");
        spawn2 = y.getLocation("spawn2");
    }

    public void setSpawn(int n, Location l) {
        if (n == 1) spawn1 = l.clone(); else spawn2 = l.clone();
        YamlConfiguration y = new YamlConfiguration();
        y.set("spawn1", spawn1);
        y.set("spawn2", spawn2);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(arenaFile());
        } catch (IOException ex) {
            plugin.getLogger().warning("Không lưu được arena.yml: " + ex.getMessage());
        }
    }

    public Location getSpawn(int n) { return n == 1 ? spawn1 : spawn2; }

    public boolean arenaReady() {
        return spawn1 != null && spawn1.getWorld() != null && spawn2 != null && spawn2.getWorld() != null;
    }

    // ---------- Truy vấn ----------
    public War getCurrent() { return current; }
    public State state() { return current == null ? null : current.state; }
    public boolean isParticipant(UUID u) { return current != null && current.isPart(u); }
    public boolean isAlive(UUID u) { return current != null && current.isAlive(u); }

    /** Đang bị khoá (không được leave/kick) vì trong war chưa kết thúc. */
    public boolean isLocked(UUID u) {
        return current != null && current.state != State.ENDED && current.isPart(u);
    }

    public Location consumeRespawn(UUID u) { return respawn.remove(u); }

    // ---------- Tuyên chiến ----------
    public void declare(Player p, Team mine, Team target) {
        Messages m = plugin.msg();
        FileConfiguration c = plugin.getConfig();
        if (mine == target) { m.send(p, "war-self"); return; }
        if (!arenaReady()) { m.send(p, "war-no-arena"); return; }
        if (current != null) { m.send(p, "war-busy"); return; }
        int min = c.getInt("war.min-players-per-team", 1);
        if (mine.getOnlineMembers().size() < min || target.getOnlineMembers().size() < min) {
            m.send(p, "war-no-players", "min", String.valueOf(min));
            return;
        }
        long exp = System.currentTimeMillis() + c.getInt("war.request-timeout-seconds", 60) * 1000L;
        requests.put(target.getId(), new Request(mine.getId(), exp));
        m.send(p, "war-request-sent", "team", target.getName());
        plugin.getTeams().broadcast(target, "war-request-received", "team", mine.getName());
    }

    public void accept(Player p, Team mine) {
        Messages m = plugin.msg();
        Request r = requests.remove(mine.getId());
        if (r == null || r.expires() < System.currentTimeMillis()) { m.send(p, "war-request-none"); return; }
        Team from = plugin.getTeams().getTeamById(r.fromTeam());
        if (from == null) { m.send(p, "war-request-none"); return; }
        if (!arenaReady()) { m.send(p, "war-no-arena"); return; }
        if (current != null) { m.send(p, "war-busy"); return; }
        int min = plugin.getConfig().getInt("war.min-players-per-team", 1);
        if (from.getOnlineMembers().size() < min || mine.getOnlineMembers().size() < min) {
            m.send(p, "war-no-players", "min", String.valueOf(min));
            return;
        }
        start(from, mine);
    }

    public void deny(Player p, Team mine) {
        Messages m = plugin.msg();
        Request r = requests.remove(mine.getId());
        if (r == null || r.expires() < System.currentTimeMillis()) { m.send(p, "war-request-none"); return; }
        Team from = plugin.getTeams().getTeamById(r.fromTeam());
        m.send(p, "war-denied-self");
        if (from != null) plugin.getTeams().broadcast(from, "war-denied", "team", mine.getName());
    }

    public void status(CommandSender s) {
        Messages m = plugin.msg();
        War w = current;
        if (w == null) { m.send(s, "war-none"); return; }
        m.send(s, "war-status", "a", w.a.getName(), "b", w.b.getName(),
                "ka", String.valueOf(w.killsA), "kb", String.valueOf(w.killsB),
                "time", Messages.fmtTime(w.timeLeft));
    }

    // ---------- Vòng đời trận ----------
    private void start(Team a, Team b) {
        FileConfiguration c = plugin.getConfig();
        int max = c.getInt("war.max-players-per-team", 0);
        War w = new War(a, b);
        w.countdown = c.getInt("war.countdown-seconds", 10);
        w.timeLeft = c.getInt("war.duration-seconds", 600);
        current = w;
        enter(w, a, w.partsA, w.aliveA, spawn1, max);
        enter(w, b, w.partsB, w.aliveB, spawn2, max);
        w.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void enter(War w, Team t, Set<UUID> parts, Set<UUID> alive, Location spawn, int max) {
        boolean heal = plugin.getConfig().getBoolean("war.heal-on-start", true);
        int n = 0;
        for (Player p : t.getOnlineMembers()) {
            if (max > 0 && n >= max) break;
            n++;
            UUID u = p.getUniqueId();
            w.back.put(u, p.getLocation());
            parts.add(u);
            alive.add(u);
            if (heal) {
                AttributeInstance hp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (hp != null) p.setHealth(hp.getValue());
                p.setFoodLevel(20);
                p.setFireTicks(0);
            }
            p.teleport(spawn);
            plugin.getDisplay().refresh(p);
        }
    }

    private void tick() {
        War w = current;
        if (w == null || w.state == State.ENDED) return;
        if (w.state == State.COUNTDOWN) {
            if (w.countdown <= 0) {
                w.state = State.RUNNING;
                broadcastWar(w, "war-started");
            } else {
                if (w.countdown <= 5 || w.countdown % 5 == 0)
                    broadcastWar(w, "war-countdown", "seconds", String.valueOf(w.countdown));
                w.countdown--;
            }
            return;
        }
        w.timeLeft--;
        if (w.aliveA.isEmpty() || w.aliveB.isEmpty() || w.timeLeft <= 0) finish(w, decide(w));
    }

    private Team decide(War w) {
        if (w.aliveA.isEmpty() && !w.aliveB.isEmpty()) return w.b;
        if (w.aliveB.isEmpty() && !w.aliveA.isEmpty()) return w.a;
        if (w.killsA > w.killsB) return w.a;
        if (w.killsB > w.killsA) return w.b;
        return null;
    }

    private void finish(War w, Team winner) {
        if (w.state == State.ENDED) return;
        w.state = State.ENDED;
        FileConfiguration c = plugin.getConfig();
        TeamManager tm = plugin.getTeams();
        String[] kv = {"a", w.a.getName(), "b", w.b.getName(),
                "ka", String.valueOf(w.killsA), "kb", String.valueOf(w.killsB)};

        if (winner == null) {
            w.a.addPoints(c.getInt("points.war-draw", 5));
            w.b.addPoints(c.getInt("points.war-draw", 5));
            for (Team t : List.of(w.a, w.b)) tm.broadcast(t, "war-ended-draw", kv);
            runCommands("war.rewards.draw-commands", w, w.a);
            runCommands("war.rewards.draw-commands", w, w.b);
        } else {
            Team loser = winner == w.a ? w.b : w.a;
            winner.addWins(1);
            winner.addPoints(c.getInt("points.war-win", 25));
            loser.addLosses(1);
            loser.addPoints(c.getInt("points.war-lose", 0));
            String[] kv2 = Arrays.copyOf(kv, kv.length + 2);
            kv2[kv.length] = "winner";
            kv2[kv.length + 1] = winner.getName();
            for (Team t : List.of(w.a, w.b)) tm.broadcast(t, "war-ended-win", kv2);
            runCommands("war.rewards.winner-commands", w, winner);
            runCommands("war.rewards.loser-commands", w, loser);
        }
        tm.markDirty();
        long delay = Math.max(0, c.getInt("war.end-teleport-delay-seconds", 5)) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> cleanup(w), delay);
    }

    private void runCommands(String path, War w, Team team) {
        List<String> cmds = plugin.getConfig().getStringList(path);
        if (cmds.isEmpty()) return;
        Set<UUID> parts = team == w.a ? w.partsA : w.partsB;
        for (UUID u : parts) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            for (String cmd : cmds) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        cmd.replace("%player%", p.getName()).replace("%team%", team.getName()));
            }
        }
    }

    private void cleanup(War w) {
        if (current != w) return;
        if (w.task != null) w.task.cancel();
        Set<UUID> still = new HashSet<>(w.aliveA);
        still.addAll(w.aliveB);
        for (UUID u : still) {
            Player p = Bukkit.getPlayer(u);
            Location b = w.back.get(u);
            if (p != null && b != null) p.teleport(b);
        }
        current = null;
        for (UUID u : w.allParts()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) plugin.getDisplay().refresh(p);
        }
    }

    // ---------- Sự kiện ----------
    public void onDeath(Player victim, Player killer, PlayerDeathEvent e) {
        War w = current;
        UUID v = victim.getUniqueId();
        if (w == null || w.state != State.RUNNING || !w.isAlive(v)) return;
        boolean victimInA = w.partsA.contains(v);
        if (killer != null) {
            UUID k = killer.getUniqueId();
            if (victimInA && w.partsB.contains(k)) w.killsB++;
            else if (!victimInA && w.partsA.contains(k)) w.killsA++;
        }
        w.aliveA.remove(v);
        w.aliveB.remove(v);
        respawn.put(v, w.back.get(v));
        if (plugin.getConfig().getBoolean("war.keep-inventory", true)) {
            e.setKeepInventory(true);
            e.getDrops().clear();
            e.setKeepLevel(true);
            e.setDroppedExp(0);
        }
        String[] kv = {"victim", victim.getName(), "killer", killer == null ? "-" : killer.getName(),
                "a", w.a.getName(), "b", w.b.getName(),
                "ka", String.valueOf(w.killsA), "kb", String.valueOf(w.killsB)};
        broadcastWar(w, "war-kill", kv);
        if (w.aliveA.isEmpty() || w.aliveB.isEmpty()) finish(w, decide(w));
    }

    public void onQuit(Player p) {
        War w = current;
        UUID u = p.getUniqueId();
        if (w == null || !w.isAlive(u)) return;
        w.aliveA.remove(u);
        w.aliveB.remove(u);
        Location b = w.back.get(u);
        if (b != null) p.teleport(b);
        if (w.state != State.ENDED && (w.aliveA.isEmpty() || w.aliveB.isEmpty())) finish(w, decide(w));
    }

    /** Gọi khi team bị giải tán: huỷ lời thách và xử thua nếu đang đánh. */
    public void onTeamRemoved(Team t) {
        requests.remove(t.getId());
        requests.values().removeIf(r -> r.fromTeam().equals(t.getId()));
        War w = current;
        if (w != null && w.state != State.ENDED && (w.a == t || w.b == t)) {
            finish(w, w.a == t ? w.b : w.a);
        }
    }

    /** Admin huỷ trận ngay lập tức. */
    public boolean stop() {
        War w = current;
        if (w == null) return false;
        w.state = State.ENDED;
        broadcastBoth(w, "war-cancelled");
        cleanup(w);
        return true;
    }

    public void shutdown() {
        War w = current;
        if (w == null) return;
        w.state = State.ENDED;
        cleanup(w);
    }

    // ---------- HUD ----------
    public String hudFor(Player p) {
        War w = current;
        if (w == null) return null;
        UUID u = p.getUniqueId();
        boolean inA = w.partsA.contains(u);
        if (!inA && !w.partsB.contains(u)) return null;
        Messages m = plugin.msg();
        if (w.state == State.COUNTDOWN) return m.raw("war-hud-countdown", "count", String.valueOf(w.countdown));
        return m.raw("war-hud",
                "mine", String.valueOf(inA ? w.killsA : w.killsB),
                "theirs", String.valueOf(inA ? w.killsB : w.killsA),
                "time", Messages.fmtTime(w.timeLeft),
                "alive_mine", String.valueOf(inA ? w.aliveA.size() : w.aliveB.size()),
                "alive_theirs", String.valueOf(inA ? w.aliveB.size() : w.aliveA.size()));
    }

    // ---------- Broadcast ----------
    private void broadcastWar(War w, String key, String... kv) {
        String s = plugin.msg().prefixed(key, kv);
        if (s.isEmpty()) return;
        for (UUID u : w.allParts()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendMessage(s);
        }
    }

    private void broadcastBoth(War w, String key, String... kv) {
        for (Team t : List.of(w.a, w.b)) plugin.getTeams().broadcast(t, key, kv);
    }
}
