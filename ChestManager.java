package com.astraxbox.team;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Rương chung của team (lưu vào chests.yml). Mở bằng /team chest hoặc /teamchest. */
public class ChestManager implements Listener, CommandExecutor {

    public static class Holder implements InventoryHolder {
        final UUID teamId;
        Inventory inv;

        Holder(UUID teamId) { this.teamId = teamId; }

        @Override
        public Inventory getInventory() { return inv; }
    }

    private final AstraxTeam plugin;
    private final Map<UUID, Inventory> chests = new HashMap<>();

    public ChestManager(AstraxTeam plugin) { this.plugin = plugin; }

    private int defaultRows() {
        return Math.max(1, Math.min(6, plugin.getConfig().getInt("chest.rows", 3)));
    }

    @SuppressWarnings("deprecation")
    private Inventory create(Team t, int size) {
        Holder h = new Holder(t.getId());
        Inventory inv = Bukkit.createInventory(h, size, plugin.msg().raw("chest-title", "team", t.getName()));
        h.inv = inv;
        return inv;
    }

    private Inventory get(Team t) {
        return chests.computeIfAbsent(t.getId(), k -> create(t, defaultRows() * 9));
    }

    public boolean isNotEmpty(Team t) {
        Inventory inv = chests.get(t.getId());
        if (inv == null) return false;
        for (ItemStack is : inv.getContents()) {
            if (is != null && !is.getType().isAir()) return true;
        }
        return false;
    }

    // ---------- Mở rương ----------
    public void open(Player p) {
        Messages m = plugin.msg();
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("chest.enabled", true)) { m.send(p, "chest-disabled"); return; }
        Team t = plugin.getTeams().getTeam(p.getUniqueId());
        if (t == null) { m.send(p, "no-team"); return; }
        Role min = Role.parse(c.getString("permissions.chest"), Role.MEMBER);
        Role r = t.getRole(p.getUniqueId());
        if (r == null || !r.atLeast(min)) { m.send(p, "no-permission-role", "role", m.role(min)); return; }
        if (c.getBoolean("chest.block-in-war", true) && plugin.getWars().isLocked(p.getUniqueId())) {
            m.send(p, "war-locked");
            return;
        }
        p.openInventory(get(t));
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { plugin.msg().send(s, "only-player"); return true; }
        open(p);
        return true;
    }

    /** Đóng rương của người chơi nếu họ đang xem (khi bị kick / rời / team giải tán). */
    public void closeFor(UUID u) {
        Player p = Bukkit.getPlayer(u);
        if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
            p.closeInventory();
        }
    }

    /** Gọi khi team bị giải tán: trả đồ cho Leader (nếu online) rồi xoá rương. */
    public void onDisband(Team t) {
        for (UUID u : new ArrayList<>(t.getMembers().keySet())) closeFor(u);
        Inventory inv = chests.remove(t.getId());
        if (inv == null) return;
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack is : inv.getContents()) {
            if (is != null && !is.getType().isAir()) items.add(is);
        }
        if (!items.isEmpty()) {
            UUID lid = t.getLeader();
            Player leader = lid == null ? null : Bukkit.getPlayer(lid);
            if (leader != null) {
                for (ItemStack is : items) {
                    for (ItemStack left : leader.getInventory().addItem(is).values()) {
                        leader.getWorld().dropItemNaturally(leader.getLocation(), left);
                    }
                }
                plugin.msg().send(leader, "chest-returned");
            } else {
                plugin.getLogger().warning("Team " + t.getName() + " giải tán khi Leader offline, "
                        + items.size() + " stack trong rương team bị mất.");
            }
        }
        inv.clear();
        save();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) save();
    }

    // ---------- Lưu / tải ----------
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::save, 1200L, 1200L);
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Inventory> e : chests.entrySet()) {
            if (plugin.getTeams().getTeamById(e.getKey()) == null) continue;
            String b = "chests." + e.getKey() + ".";
            y.set(b + "size", e.getValue().getSize());
            y.set(b + "items", Arrays.asList(e.getValue().getContents()));
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(new File(plugin.getDataFolder(), "chests.yml"));
        } catch (IOException ex) {
            plugin.getLogger().warning("Không lưu được chests.yml: " + ex.getMessage());
        }
    }

    public void load() {
        File f = new File(plugin.getDataFolder(), "chests.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection sec = y.getConfigurationSection("chests");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException ex) { continue; }
            Team t = plugin.getTeams().getTeamById(id);
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (t == null || s == null) continue;
            int size = Math.max(9, Math.min(54, s.getInt("size", defaultRows() * 9)));
            size = Math.max(size, defaultRows() * 9);
            Inventory inv = create(t, size);
            List<?> items = s.getList("items");
            if (items != null) {
                for (int i = 0; i < items.size() && i < inv.getSize(); i++) {
                    if (items.get(i) instanceof ItemStack is) inv.setItem(i, is);
                }
            }
            chests.put(id, inv);
        }
    }
}
