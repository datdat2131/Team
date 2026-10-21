package com.astraxbox.team;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Một menu inventory: mỗi ô có thể gắn một hành động khi click. */
public class Gui implements InventoryHolder {
    private final Inventory inv;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    @SuppressWarnings("deprecation")
    public Gui(int rows, String title) {
        this.inv = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() { return inv; }

    public void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inv.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
    }

    public void fill(ItemStack item) {
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, item);
        }
    }

    /** @return true nếu ô đó có hành động. */
    public boolean handle(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot());
        if (a == null) return false;
        a.accept(e);
        return true;
    }

    public void open(Player p) { p.openInventory(inv); }

    private static List<String> resetLore(List<String> lore) {
        List<String> out = new ArrayList<>();
        if (lore != null) for (String s : lore) out.add("§r" + s);
        return out;
    }

    @SuppressWarnings("deprecation")
    public static ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§r" + name);
            if (lore != null && !lore.isEmpty()) meta.setLore(resetLore(lore));
            meta.addItemFlags(ItemFlag.values());
            it.setItemMeta(meta);
        }
        return it;
    }

    @SuppressWarnings("deprecation")
    public static ItemStack head(OfflinePlayer owner, String name, List<String> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta base = it.getItemMeta();
        if (base instanceof SkullMeta meta) {
            meta.setOwningPlayer(owner);
            meta.setDisplayName("§r" + name);
            if (lore != null && !lore.isEmpty()) meta.setLore(resetLore(lore));
            meta.addItemFlags(ItemFlag.values());
            it.setItemMeta(meta);
        }
        return it;
    }
}
