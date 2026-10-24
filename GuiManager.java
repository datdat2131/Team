package com.astraxbox.team;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Toàn bộ menu GUI của AstraxTeam. Các hành động gọi lại lệnh /team nên dùng chung kiểm tra quyền. */
public class GuiManager implements Listener {

    private record Entry(ItemStack item, Consumer<InventoryClickEvent> action) {}

    private final AstraxTeam plugin;
    private final Map<UUID, Consumer<String>> prompts = new ConcurrentHashMap<>();

    public GuiManager(AstraxTeam plugin) { this.plugin = plugin; }

    // ---------- Tiện ích ----------
    private Messages m() { return plugin.msg(); }
    private TeamManager tm() { return plugin.getTeams(); }
    private Team team(Player p) { return tm().getTeam(p.getUniqueId()); }

    private void go(Runnable r) { Bukkit.getScheduler().runTask(plugin, r); }

    private void cmd(Player p, String command, Runnable after) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.performCommand(command);
            if (after != null) after.run();
        });
    }

    private void closeCmd(Player p, String command) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.closeInventory();
            p.performCommand(command);
        });
    }

    private ItemStack filler() {
        Material mat = Material.matchMaterial(plugin.getConfig().getString("gui.filler-material", "GRAY_STAINED_GLASS_PANE"));
        if (mat == null) mat = Material.GRAY_STAINED_GLASS_PANE;
        return Gui.item(mat, m().raw("gui.filler"), List.of());
    }

    private ItemStack btn(Material mat, String nameKey, String loreKey, String... kv) {
        List<String> lore = loreKey == null ? List.of() : m().list(loreKey, kv);
        return Gui.item(mat, m().raw(nameKey, kv), lore);
    }

    private String first(String s) {
        String t = s.trim();
        int i = t.indexOf(' ');
        return i < 0 ? t : t.substring(0, i);
    }

    private String[] infoKv(Team t) {
        UUID leader = t.getLeader();
        return new String[]{
                "team", t.getName(),
                "leader", leader == null ? "?" : t.getMemberName(leader),
                "rank", String.valueOf(tm().rankOf(t)),
                "count", String.valueOf(t.size()),
                "max", String.valueOf(tm().maxMembers()),
                "online", String.valueOf(t.getOnlineMembers().size()),
                "points", String.valueOf(t.getPoints()),
                "kills", String.valueOf(t.getKills()),
                "deaths", String.valueOf(t.getDeaths()),
                "kdr", String.format(Locale.US, "%.2f", t.kdr()),
                "wins", String.valueOf(t.getWins()),
                "losses", String.valueOf(t.getLosses()),
                "pvp", m().raw(t.isPvp() ? "state-on" : "state-off")};
    }

    // ---------- Chat prompt (nhập tên) ----------
    public boolean isPrompting(UUID u) { return prompts.containsKey(u); }

    private void prompt(Player p, String messageKey, Consumer<String> onInput) {
        prompts.put(p.getUniqueId(), onInput);
        go(p::closeInventory);
        m().send(p, messageKey);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Consumer<String> c = prompts.remove(e.getPlayer().getUniqueId());
        if (c == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        String msg = e.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (msg.equalsIgnoreCase("cancel") || msg.equalsIgnoreCase("huy") || msg.isEmpty()) {
                m().send(p, "gui-prompt-cancelled");
            } else {
                c.accept(msg);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { prompts.remove(e.getPlayer().getUniqueId()); }

    // ---------- Xử lý click ----------
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Gui g)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getRawSlot() < 0 || e.getRawSlot() >= e.getInventory().getSize()) return;
        if (g.handle(e) && plugin.getConfig().getBoolean("gui.click-sound", true)) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Gui) e.setCancelled(true);
    }

    // ---------- Menu chính ----------
    public void open(Player p) {
        Team t = team(p);
        if (t == null) openNoTeam(p); else openMain(p, t);
    }

    private void openNoTeam(Player p) {
        Gui g = new Gui(3, m().raw("gui.title-noteam"));
        g.fill(filler());

        g.set(11, btn(Material.EMERALD, "gui.create-name", "gui.create-lore"),
                e -> prompt(p, "gui-prompt-create", in -> {
                    p.performCommand("ateam create " + first(in));
                    open(p);
                }));

        TeamManager.Invite inv = tm().peekInvite(p.getUniqueId());
        Team from = inv == null ? null : tm().getTeamById(inv.teamId());
        if (from != null) {
            g.set(13, btn(Material.PAPER, "gui.invite-name", "gui.invite-lore", "team", from.getName()),
                    e -> cmd(p, e.isRightClick() ? "ateam deny" : "ateam accept", () -> open(p)));
        } else {
            g.set(13, btn(Material.PAPER, "gui.invite-none", null), null);
        }

        g.set(15, btn(Material.GOLD_INGOT, "gui.top-button", "gui.top-button-lore"),
                e -> go(() -> openTop(p, tm().metric())));
        g.set(22, btn(Material.BARRIER, "gui.close", null), e -> go(p::closeInventory));
        g.open(p);
    }

    private void openMain(Player p, Team t) {
        UUID u = p.getUniqueId();
        boolean leader = t.getRole(u) == Role.LEADER;
        String[] kv = infoKv(t);
        Gui g = new Gui(4, m().raw("gui.title-main", "team", t.getName()));
        g.fill(filler());

        g.set(10, Gui.item(Material.WHITE_BANNER, m().raw("gui.info-name", kv), m().list("gui.info-lore", kv)),
                e -> closeCmd(p, "ateam info"));

        g.set(11, Gui.head(p, m().raw("gui.members-name"), m().list("gui.members-lore", kv)),
                e -> go(() -> openMembers(p, 0)));
        g.set(12, btn(Material.PAPER, "gui.invite-btn-name", "gui.invite-btn-lore"),
                e -> go(() -> openInvite(p, 0)));
        g.set(13, btn(Material.GOLD_INGOT, "gui.top-button", "gui.top-button-lore"),
                e -> go(() -> openTop(p, tm().metric())));
        g.set(14, btn(Material.GRASS_BLOCK, "gui.zones-name", "gui.zones-lore"),
                e -> go(() -> openZones(p, 0)));
        g.set(15, btn(Material.IRON_SWORD, "gui.war-name", "gui.war-lore"),
                e -> go(() -> openWar(p)));

        String chatState = m().raw(tm().isChatMode(u) ? "state-on" : "state-off");
        g.set(16, btn(Material.WRITABLE_BOOK, "gui.chat-name", "gui.chat-lore", "state", chatState),
                e -> cmd(p, "ateam chat", () -> open(p)));

        g.set(19, btn(t.isPvp() ? Material.LIME_DYE : Material.GRAY_DYE, "gui.pvp-name", "gui.pvp-lore",
                        "state", m().raw(t.isPvp() ? "state-on" : "state-off")),
                e -> cmd(p, "ateam pvp", () -> open(p)));

        g.set(20, btn(Material.NAME_TAG, "gui.rename-name", "gui.rename-lore"),
                e -> prompt(p, "gui-prompt-rename", in -> {
                    p.performCommand("ateam rename " + first(in));
                    open(p);
                }));

        g.set(21, btn(Material.CHEST, "gui.chest-name", "gui.chest-lore"),
                e -> go(() -> plugin.getChests().open(p)));

        if (leader) {
            g.set(24, btn(Material.TNT, "gui.disband-name", "gui.disband-lore"),
                    e -> go(() -> confirm(p, "ateam disband", () -> open(p))));
        } else {
            g.set(24, btn(Material.DARK_OAK_DOOR, "gui.leave-name", "gui.leave-lore"),
                    e -> go(() -> confirm(p, "ateam leave", () -> open(p))));
        }

        g.set(31, btn(Material.BARRIER, "gui.close", null), e -> go(p::closeInventory));
        g.open(p);
    }

    // ---------- Xác nhận ----------
    private void confirm(Player p, String command, Runnable back) {
        Gui g = new Gui(3, m().raw("gui.title-confirm"));
        g.fill(filler());
        g.set(11, btn(Material.LIME_CONCRETE, "gui.confirm-yes", null), e -> cmd(p, command, () -> open(p)));
        g.set(13, btn(Material.PAPER, "gui.confirm-cmd", null, "command", command), null);
        g.set(15, btn(Material.RED_CONCRETE, "gui.confirm-no", null), e -> go(back));
        g.open(p);
    }

    // ---------- Danh sách có phân trang ----------
    private void openPaged(Player p, String title, List<Entry> entries, int page,
                           Consumer<Integer> reopen, Runnable back) {
        int per = 45;
        int pages = Math.max(1, (entries.size() + per - 1) / per);
        final int pg = Math.max(0, Math.min(page, pages - 1));
        Gui g = new Gui(6, title);
        for (int i = 0; i < per; i++) {
            int idx = pg * per + i;
            if (idx >= entries.size()) break;
            Entry en = entries.get(idx);
            g.set(i, en.item(), en.action());
        }
        for (int i = 45; i < 54; i++) g.set(i, filler(), null);
        g.set(49, btn(Material.ARROW, "gui.back", null), e -> go(back));
        if (pg > 0) g.set(45, btn(Material.ARROW, "gui.prev", null), e -> go(() -> reopen.accept(pg - 1)));
        if (pg < pages - 1) g.set(53, btn(Material.ARROW, "gui.next", null), e -> go(() -> reopen.accept(pg + 1)));
        g.open(p);
    }

    // ---------- Thành viên ----------
    private void openMembers(Player p, int page) {
        final Team t = team(p);
        if (t == null) { open(p); return; }
        List<UUID> ids = new ArrayList<>(t.getMembers().keySet());
        ids.sort(Comparator.comparingInt((UUID id) -> -t.getRole(id).getRank())
                .thenComparing(id -> t.getMemberName(id).toLowerCase()));
        List<Entry> entries = new ArrayList<>();
        for (UUID id : ids) {
            boolean online = Bukkit.getPlayer(id) != null;
            String roleName = m().role(t.getRole(id));
            String name = t.getMemberName(id);
            ItemStack it = Gui.head(Bukkit.getOfflinePlayer(id),
                    m().raw("gui.member-name", "player", name, "role", roleName),
                    m().list("gui.member-lore", "role", roleName,
                            "status", m().raw(online ? "gui.online" : "gui.offline")));
            Consumer<InventoryClickEvent> action = id.equals(p.getUniqueId())
                    ? null : e -> go(() -> openMemberActions(p, id));
            entries.add(new Entry(it, action));
        }
        openPaged(p, m().raw("gui.title-members"), entries, page, pg -> openMembers(p, pg), () -> open(p));
    }

    private void openMemberActions(Player p, UUID target) {
        Team t = team(p);
        if (t == null || !t.isMember(target)) { open(p); return; }
        String name = t.getMemberName(target);
        String roleName = m().role(t.getRole(target));
        boolean viewerLeader = t.getRole(p.getUniqueId()) == Role.LEADER;
        Gui g = new Gui(3, m().raw("gui.title-member", "player", name));
        g.fill(filler());

        g.set(4, Gui.head(Bukkit.getOfflinePlayer(target), m().raw("gui.member-name", "player", name, "role", roleName),
                List.of()), null);
        g.set(11, btn(Material.LIME_DYE, "gui.promote", "gui.promote-lore"),
                e -> cmd(p, "ateam promote " + name, () -> openMemberActions(p, target)));
        g.set(15, btn(Material.ORANGE_DYE, "gui.demote", "gui.demote-lore"),
                e -> cmd(p, "ateam demote " + name, () -> openMemberActions(p, target)));
        if (viewerLeader) {
            g.set(21, btn(Material.NETHER_STAR, "gui.transfer", "gui.transfer-lore"),
                    e -> go(() -> confirm(p, "ateam transfer " + name, () -> openMemberActions(p, target))));
        }
        g.set(23, btn(Material.LEATHER_BOOTS, "gui.kick", "gui.kick-lore"),
                e -> go(() -> confirm(p, "ateam kick " + name, () -> openMemberActions(p, target))));
        g.set(18, btn(Material.ARROW, "gui.back", null), e -> go(() -> openMembers(p, 0)));
        g.open(p);
    }

    // ---------- Mời người chơi ----------
    private void openInvite(Player p, int page) {
        List<Player> online = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(p) && tm().getTeam(o.getUniqueId()) == null) online.add(o);
        }
        online.sort(Comparator.comparing(o -> o.getName().toLowerCase()));
        List<Entry> entries = new ArrayList<>();
        for (Player o : online) {
            entries.add(new Entry(
                    Gui.head(o, m().raw("gui.invite-player", "player", o.getName()), m().list("gui.invite-player-lore")),
                    e -> cmd(p, "ateam invite " + o.getName(), () -> open(p))));
        }
        if (entries.isEmpty()) entries.add(new Entry(btn(Material.BARRIER, "gui.invite-empty", null), null));
        openPaged(p, m().raw("gui.title-invite"), entries, page, pg -> openInvite(p, pg), () -> open(p));
    }

    // ---------- Bảng xếp hạng ----------
    private String buffText(List<String> effects) {
        List<String> out = new ArrayList<>();
        for (String s : effects) {
            String[] sp = s.split(":");
            int lvl = 1;
            if (sp.length > 1) {
                try { lvl = Integer.parseInt(sp[1].trim()) + 1; } catch (NumberFormatException ignored) { }
            }
            out.add(sp[0].trim().toLowerCase().replace('_', ' ') + " " + lvl);
        }
        return String.join(", ", out);
    }

    private void openTop(Player p, String metric) {
        int size = Math.max(1, Math.min(18, plugin.getConfig().getInt("leaderboard.top-size", 10)));
        List<Team> top = tm().top(metric, size);
        boolean buffs = plugin.getConfig().getBoolean("leaderboard.buffs.enabled", true);
        Gui g = new Gui(4, m().raw("gui.title-top", "metric", m().raw("metric-" + metric.toLowerCase())));
        g.fill(filler());

        for (int i = 0; i < top.size(); i++) {
            Team t = top.get(i);
            Material mat = i == 0 ? Material.GOLD_BLOCK : i == 1 ? Material.IRON_BLOCK
                    : i == 2 ? Material.COPPER_BLOCK : Material.PAPER;
            List<String> lore = new ArrayList<>(m().list("gui.top-lore", infoKv(t)));
            if (buffs && i < 5) {
                List<String> eff = plugin.getConfig().getStringList("leaderboard.buffs.ranks." + (i + 1));
                if (!eff.isEmpty()) lore.add(m().raw("gui.top-buff", "effects", buffText(eff)));
            }
            g.set(i, Gui.item(mat, m().raw("gui.top-name", "rank", String.valueOf(i + 1), "team", t.getName()), lore), null);
        }
        if (top.isEmpty()) g.set(13, btn(Material.BARRIER, "gui.top-empty", null), null);

        String[] metrics = {"POINTS", "KILLS", "WINS"};
        int[] slots = {29, 31, 33};
        for (int i = 0; i < 3; i++) {
            String mt = metrics[i];
            g.set(slots[i], btn(mt.equals(metric) ? Material.LIME_DYE : Material.GRAY_DYE, "gui.metric-btn", null,
                    "metric", m().raw("metric-" + mt.toLowerCase())), e -> go(() -> openTop(p, mt)));
        }
        g.set(27, btn(Material.ARROW, "gui.back", null), e -> go(() -> open(p)));
        g.open(p);
    }

    // ---------- Khu vực ----------
    private void openZones(Player p, int page) {
        Team mine = team(p);
        List<Entry> entries = new ArrayList<>();
        for (ZoneManager.Zone z : plugin.getZones().all()) {
            Team owner = z.owner == null ? null : tm().getTeamById(z.owner);
            boolean own = mine != null && owner == mine;
            Material mat = owner == null ? Material.GRASS_BLOCK : own ? Material.LIME_BANNER : Material.RED_BANNER;
            String action = own ? m().raw("gui.zone-unclaim") : owner == null && mine != null ? m().raw("gui.zone-claim") : "";
            ItemStack it = Gui.item(mat, m().raw("gui.zone-name", "zone", z.name),
                    m().list("gui.zone-lore", "world", z.world,
                            "x", z.minX + " → " + z.maxX, "z", z.minZ + " → " + z.maxZ,
                            "owner", owner == null ? m().raw("free") : "&e" + owner.getName(),
                            "action", action));
            Consumer<InventoryClickEvent> act = null;
            if (mine != null && owner == null) act = e -> cmd(p, "ateam claim " + z.name, () -> openZones(p, page));
            else if (own) act = e -> cmd(p, "ateam unclaim " + z.name, () -> openZones(p, page));
            entries.add(new Entry(it, act));
        }
        if (entries.isEmpty()) entries.add(new Entry(btn(Material.BARRIER, "gui.zones-empty", null), null));
        openPaged(p, m().raw("gui.title-zones"), entries, page, pg -> openZones(p, pg), () -> open(p));
    }

    // ---------- Team war ----------
    private void openWar(Player p) {
        Team t = team(p);
        if (t == null) { open(p); return; }
        WarManager wm = plugin.getWars();
        WarManager.War w = wm.getCurrent();
        Gui g = new Gui(4, m().raw("gui.title-war"));
        g.fill(filler());

        List<String> statusLore = w == null
                ? m().list("gui.war-status-none")
                : m().list("gui.war-status-lore", "a", w.a.getName(), "b", w.b.getName(),
                        "ka", String.valueOf(w.killsA), "kb", String.valueOf(w.killsB),
                        "time", Messages.fmtTime(w.timeLeft));
        g.set(11, Gui.item(Material.CLOCK, m().raw("gui.war-status"), statusLore), e -> closeCmd(p, "ateam war status"));

        g.set(13, btn(Material.IRON_SWORD, "gui.war-declare", "gui.war-declare-lore"),
                e -> go(() -> openWarPick(p, 0)));

        WarManager.Request rq = wm.peekRequest(t.getId());
        Team from = rq == null ? null : tm().getTeamById(rq.fromTeam());
        if (from != null) {
            g.set(15, btn(Material.CROSSBOW, "gui.war-request", "gui.war-request-lore", "team", from.getName()),
                    e -> {
                        if (e.isRightClick()) cmd(p, "ateam war deny", () -> openWar(p));
                        else cmd(p, "ateam war accept", () -> open(p));
                    });
        } else {
            g.set(15, btn(Material.GRAY_DYE, "gui.war-request-none", null), null);
        }
        g.set(31, btn(Material.ARROW, "gui.back", null), e -> go(() -> open(p)));
        g.open(p);
    }

    private void openWarPick(Player p, int page) {
        Team mine = team(p);
        if (mine == null) { open(p); return; }
        List<Entry> entries = new ArrayList<>();
        for (Team t : tm().top("POINTS", Integer.MAX_VALUE)) {
            if (t == mine) continue;
            entries.add(new Entry(
                    Gui.item(Material.WHITE_BANNER, m().raw("gui.war-team-name", "team", t.getName()),
                            m().list("gui.war-team-lore", infoKv(t))),
                    e -> cmd(p, "ateam war declare " + t.getName(), () -> openWar(p))));
        }
        if (entries.isEmpty()) entries.add(new Entry(btn(Material.BARRIER, "gui.top-empty", null), null));
        openPaged(p, m().raw("gui.title-war-pick"), entries, page, pg -> openWarPick(p, pg), () -> openWar(p));
    }
}
