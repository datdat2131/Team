package com.astraxbox.team;

/** Tách riêng để lớp GlowManager (phụ thuộc ProtocolLib) chỉ được nạp khi ProtocolLib tồn tại. */
final class GlowLoader {
    private GlowLoader() { }

    static GlowHook create(AstraxTeam plugin) {
        return new GlowManager(plugin);
    }
}
