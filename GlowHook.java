package com.astraxbox.team;

import org.bukkit.entity.Player;

/** Cầu nối tới GlowManager (chỉ nạp khi có ProtocolLib). */
public interface GlowHook {
    void refresh(Player p);
    void clear(Player p);
    void shutdown();
}
