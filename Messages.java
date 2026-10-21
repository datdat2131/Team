package com.astraxbox.team;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Messages {
    private final AstraxTeam plugin;
    private FileConfiguration cfg;

    public Messages(AstraxTeam plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
        InputStream in = plugin.getResource("messages.yml");
        if (in != null) {
            cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public static void actionBar(Player p, String coloredText) {
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(coloredText));
    }

    private static String apply(String s, String... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        return color(s);
    }

    /** Lấy message đã đổi màu + thay placeholder (không kèm prefix). */
    public String raw(String key, String... kv) {
        String s = cfg.getString(key);
        if (s == null) s = "&cMissing message: " + key;
        return apply(s, kv);
    }

    public List<String> list(String key, String... kv) {
        List<String> out = new ArrayList<>();
        for (String s : cfg.getStringList(key)) out.add(apply(s, kv));
        return out;
    }

    /** Message kèm prefix, dùng để gửi cho người chơi. */
    public String prefixed(String key, String... kv) {
        String body = raw(key, kv);
        return body.isEmpty() ? "" : raw("prefix") + body;
    }

    public void send(CommandSender to, String key, String... kv) {
        String s = prefixed(key, kv);
        if (!s.isEmpty()) to.sendMessage(s);
    }

    public String role(Role r) {
        return raw("roles." + r.name().toLowerCase());
    }

    public static String fmtTime(int seconds) {
        seconds = Math.max(0, seconds);
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
