package com.astraxbox.team;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeamChatCommand implements CommandExecutor {
    private final AstraxTeam plugin;

    public TeamChatCommand(AstraxTeam plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { plugin.msg().send(s, "only-player"); return true; }
        TeamManager tm = plugin.getTeams();
        Team t = tm.getTeam(p.getUniqueId());
        if (t == null) { plugin.msg().send(p, "no-team"); return true; }
        if (a.length == 0) {
            boolean on = tm.toggleChat(p.getUniqueId());
            plugin.msg().send(p, on ? "chat-mode-on" : "chat-mode-off");
        } else {
            tm.sendTeamChat(p, t, String.join(" ", a));
        }
        return true;
    }
}
