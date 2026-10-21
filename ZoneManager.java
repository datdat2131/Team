package com.astraxbox.team;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ZoneManager {

    public static class Zone {
        public final String name;
        public final String world;
        public int minX, maxX, minZ, maxZ;
        public UUID owner;

        public Zone(String name, String world, int x1, int z1, int x2, int z2) {
            this.name = name;
            this.world = world;
            this.minX = Math.min(x1, x2);
            this.maxX = Math.max(x1, x2);
            this.minZ = Math.min(z1, z2);
            this.maxZ = Math.max(z1, z2);
        }

        /** Zone tính theo X/Z (bao trùm toàn bộ chiều cao). */
        public boolean contains(Location l) {
            return l != null && l.getWorld() != null && l.getWorld().getName().equals(world)
                    && l.getBlockX() >= minX && l.getBlockX() <= maxX
                    && l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ;
        }
    }

    private final AstraxTeam plugin;
    private final Map<String, Zone> zones = new LinkedHashMap<>();
    private final Map<UUID, Location[]> selections = new HashMap<>();

    public ZoneManager(AstraxTeam plugin) { this.plugin = plugin; }

    public Collection<Zone> all() { return zones.values(); }
    public Zone get(String name) { return name == null ? null : zones.get(name.toLowerCase()); }

    public Zone getAt(Location l) {
        for (Zone z : zones.values()) if (z.contains(l)) return z;
        return null;
    }

    public List<Zone> ownedBy(UUID teamId) {
        List<Zone> list = new ArrayList<>();
        for (Zone z : zones.values()) if (teamId.equals(z.owner)) list.add(z);
        return list;
    }

    public void releaseAll(UUID teamId) {
        boolean changed = false;
        for (Zone z : zones.values()) {
            if (teamId.equals(z.owner)) { z.owner = null; changed = true; }
        }
        if (changed) save();
    }

    public void setPos(UUID admin, int n, Location l) {
        Location[] sel = selections.computeIfAbsent(admin, k -> new Location[2]);
        sel[n - 1] = l.clone();
    }

    /** @return zone mới hoặc null nếu chưa đủ pos / khác world. */
    public Zone create(UUID admin, String name) {
        Location[] sel = selections.get(admin);
        if (sel == null || sel[0] == null || sel[1] == null) return null;
        if (sel[0].getWorld() == null || !sel[0].getWorld().equals(sel[1].getWorld())) return null;
        Zone z = new Zone(name, sel[0].getWorld().getName(),
                sel[0].getBlockX(), sel[0].getBlockZ(), sel[1].getBlockX(), sel[1].getBlockZ());
        zones.put(name.toLowerCase(), z);
        save();
        return z;
    }

    public boolean delete(String name) {
        boolean r = zones.remove(name.toLowerCase()) != null;
        if (r) save();
        return r;
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Zone z : zones.values()) {
            String b = "zones." + z.name + ".";
            y.set(b + "world", z.world);
            y.set(b + "minX", z.minX);
            y.set(b + "maxX", z.maxX);
            y.set(b + "minZ", z.minZ);
            y.set(b + "maxZ", z.maxZ);
            y.set(b + "owner", z.owner == null ? null : z.owner.toString());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(new File(plugin.getDataFolder(), "zones.yml"));
        } catch (IOException ex) {
            plugin.getLogger().warning("Không lưu được zones.yml: " + ex.getMessage());
        }
    }

    public void load() {
        File f = new File(plugin.getDataFolder(), "zones.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection sec = y.getConfigurationSection("zones");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(name);
            if (s == null) continue;
            Zone z = new Zone(name, s.getString("world", "world"),
                    s.getInt("minX"), s.getInt("minZ"), s.getInt("maxX"), s.getInt("maxZ"));
            String o = s.getString("owner");
            if (o != null) {
                try { z.owner = UUID.fromString(o); } catch (IllegalArgumentException ignored) { }
            }
            zones.put(name.toLowerCase(), z);
        }
    }
}
