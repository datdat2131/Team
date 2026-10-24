package com.astraxbox.team;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Glow riêng theo người nhìn (per-viewer) bằng packet metadata:
 *  - Chỉ đồng đội của nhau mới nhận cờ "glowing" (bit 0x40).
 *  - Bản thân không bao giờ nhận cờ glow của chính mình.
 *  - Người ngoài team không thấy gì (server KHÔNG bật glow toàn cục).
 * Màu viền lấy từ scoreboard team do DisplayManager tạo (display.glow.color).
 */
public class GlowManager extends PacketAdapter implements GlowHook {
    private static final byte GLOW_BIT = 0x40;

    private final AstraxTeam plugin;

    // Các map dưới đây được đọc từ luồng packet nên phải là concurrent
    private final Map<Integer, UUID> entityToPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> teamOf = new ConcurrentHashMap<>();
    private final Set<UUID> glowing = ConcurrentHashMap.newKeySet();

    // Chỉ dùng ở luồng chính: target -> những người đã được gửi glow
    private final Map<UUID, Set<UUID>> sent = new HashMap<>();
    private boolean warned;

    public GlowManager(AstraxTeam plugin) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_METADATA);
        this.plugin = plugin;
        ProtocolLibrary.getProtocolManager().addPacketListener(this);
    }

    private boolean shouldGlow(Player p) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("display.glow.enabled", true)) return false;
        return !c.getBoolean("display.glow.only-in-war", false) || plugin.getWars().isAlive(p.getUniqueId());
    }

    // ---------- Chặn packet metadata gửi đi ----------
    @Override
    public void onPacketSending(PacketEvent e) {
        if (e.isCancelled()) return;
        Player viewer = e.getPlayer();
        UUID viewerTeam = teamOf.get(viewer.getUniqueId());
        if (viewerTeam == null) return;

        PacketContainer pk = e.getPacket();
        int id = pk.getIntegers().read(0);
        UUID target = entityToPlayer.get(id);
        if (target == null || target.equals(viewer.getUniqueId())) return;          // bản thân không glow
        if (!viewerTeam.equals(teamOf.get(target)) || !glowing.contains(target)) return;

        List<WrappedDataValue> vals = pk.getDataValueCollectionModifier().read(0);
        if (vals == null) return;
        boolean has = false;
        List<WrappedDataValue> updated = new ArrayList<>();
        for (WrappedDataValue v : vals) {
            if (v.getIndex() == 0 && v.getValue() instanceof Byte b) {
                updated.add(new WrappedDataValue(0, v.getSerializer(), (byte) (b | GLOW_BIT)));
                has = true;
            } else {
                updated.add(v);
            }
        }
        if (!has) return;
        PacketContainer copy = pk.deepClone();   // không làm ảnh hưởng người nhận khác
        copy.getDataValueCollectionModifier().write(0, updated);
        e.setPacket(copy);
    }

    // ---------- Gửi cờ glow chủ động ----------
    private void send(Player viewer, Player target, boolean glow) {
        byte flags = 0;
        try {
            WrappedDataWatcher w = WrappedDataWatcher.getEntityWatcher(target);
            Byte b = w.getByte(0);
            if (b != null) flags = b;
        } catch (Exception ignored) { }
        if (glow) flags |= GLOW_BIT; else flags &= ~GLOW_BIT;

        PacketContainer pk = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        pk.getIntegers().write(0, target.getEntityId());
        List<WrappedDataValue> values = new ArrayList<>();
        values.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), flags));
        pk.getDataValueCollectionModifier().write(0, values);
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, pk, false);
        } catch (Exception ex) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Không gửi được packet glow (kiểm tra phiên bản ProtocolLib): " + ex.getMessage());
            }
        }
    }

    /** Tính lại ai được thấy glow của target và gửi cập nhật. */
    private void push(Player target) {
        UUID tu = target.getUniqueId();
        Set<UUID> prev = sent.getOrDefault(tu, Collections.emptySet());
        Set<UUID> now = new HashSet<>();
        Team t = plugin.getTeams().getTeam(tu);
        if (t != null && glowing.contains(tu)) {
            for (Player m : t.getOnlineMembers()) {
                if (!m.equals(target)) now.add(m.getUniqueId());
            }
        }
        for (UUID v : now) {
            Player vp = Bukkit.getPlayer(v);
            if (vp != null) send(vp, target, true);
        }
        for (UUID v : prev) {
            if (!now.contains(v)) {
                Player vp = Bukkit.getPlayer(v);
                if (vp != null) send(vp, target, false);
            }
        }
        if (now.isEmpty()) sent.remove(tu); else sent.put(tu, now);
    }

    // ---------- GlowHook ----------
    @Override
    public void refresh(Player p) {
        UUID u = p.getUniqueId();
        entityToPlayer.values().removeIf(v -> v.equals(u));      // entity id đổi sau khi respawn
        entityToPlayer.put(p.getEntityId(), u);

        Team t = plugin.getTeams().getTeam(u);
        if (t == null) teamOf.remove(u); else teamOf.put(u, t.getId());
        if (t != null && shouldGlow(p)) glowing.add(u); else glowing.remove(u);

        push(p);
        if (t != null) {
            for (Player m : t.getOnlineMembers()) {
                if (!m.equals(p)) push(m);                       // p có thể là người xem mới của họ
            }
        }
        for (Map.Entry<UUID, Set<UUID>> en : new ArrayList<>(sent.entrySet())) {
            if (en.getValue().contains(u)) {                     // p vừa rời team: gỡ glow của người cũ
                Player tp = Bukkit.getPlayer(en.getKey());
                if (tp != null && !tp.equals(p)) push(tp);
            }
        }
    }

    @Override
    public void clear(Player p) {
        UUID u = p.getUniqueId();
        entityToPlayer.values().removeIf(v -> v.equals(u));
        teamOf.remove(u);
        glowing.remove(u);
        sent.remove(u);
    }

    @Override
    public void shutdown() {
        glowing.clear();
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) push(p);
        ProtocolLibrary.getProtocolManager().removePacketListeners(plugin);
    }
}
