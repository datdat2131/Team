package com.astraxbox.team;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class AstraxTeam extends JavaPlugin {
    private static AstraxTeam instance;
    private Messages messages;
    private TeamManager teams;
    private ZoneManager zones;
    private WarManager wars;
    private DisplayManager display;
    private BuffManager buffs;
    private GuiManager gui;
    private ChestManager chests;
    private AstraxTeamAPI api;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        messages = new Messages(this);
        teams = new TeamManager(this);
        zones = new ZoneManager(this);
        wars = new WarManager(this);
        display = new DisplayManager(this);
        buffs = new BuffManager(this);
        gui = new GuiManager(this);
        chests = new ChestManager(this);
        api = new AstraxTeamAPI(this);

        teams.load();
        zones.load();
        wars.loadArena();
        chests.load();

        TeamCommand teamCmd = new TeamCommand(this);
        getCommand("team").setExecutor(teamCmd);
        getCommand("team").setTabCompleter(teamCmd);
        getCommand("teamchest").setExecutor(chests);
        getCommand("teamchat").setExecutor(new TeamChatCommand(this));
        TeamAdminCommand adminCmd = new TeamAdminCommand(this);
        getCommand("teamadmin").setExecutor(adminCmd);
        getCommand("teamadmin").setTabCompleter(adminCmd);

        Bukkit.getPluginManager().registerEvents(gui, this);
        Bukkit.getPluginManager().registerEvents(chests, this);
        Bukkit.getPluginManager().registerEvents(new TeamListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ClaimListener(this), this);

        teams.startAutosave();
        chests.start();
        display.start();
        buffs.start();

        getServer().getServicesManager().register(AstraxTeamAPI.class, api, this, ServicePriority.Normal);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AstraxExpansion(this).register();
            getLogger().info("Đã đăng ký PlaceholderAPI: %astraxteam_...%");
        }
        if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            try {
                display.setGlowHook(GlowLoader.create(this));
                getLogger().info("Đã bật glow riêng cho team (ProtocolLib).");
            } catch (Throwable t) {
                getLogger().warning("Không khởi tạo được glow riêng: " + t);
            }
        } else if (getConfig().getBoolean("display.glow.enabled", true)) {
            getLogger().warning("Chưa cài ProtocolLib -> glow team đang TẮT. Cài ProtocolLib 5.x để đồng đội thấy glow của nhau.");
        }
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) display.refresh(p);
        getLogger().info("AstraxTeam đã bật - " + teams.all().size() + " team.");
    }

    @Override
    public void onDisable() {
        if (wars != null) wars.shutdown();
        if (display != null) display.shutdown();
        if (buffs != null) buffs.stop();
        if (chests != null) chests.save();
        if (teams != null) teams.save();
        if (zones != null) zones.save();
        Bukkit.getScheduler().cancelTasks(this);
    }

    public void reloadAll() {
        reloadConfig();
        messages.load();
        display.start();
        buffs.start();
        display.refreshAll();
    }

    public static AstraxTeam getInstance() { return instance; }
    public Messages msg() { return messages; }
    public TeamManager getTeams() { return teams; }
    public ZoneManager getZones() { return zones; }
    public WarManager getWars() { return wars; }
    public DisplayManager getDisplay() { return display; }
    public GuiManager getGui() { return gui; }
    public ChestManager getChests() { return chests; }
    public AstraxTeamAPI getAPI() { return api; }
}
