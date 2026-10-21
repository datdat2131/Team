package com.astraxbox.team;

public enum Role {
    MEMBER(1), CO_LEADER(2), LEADER(3);

    private final int rank;

    Role(int rank) { this.rank = rank; }

    public int getRank() { return rank; }

    public boolean atLeast(Role other) { return rank >= other.rank; }

    public static Role parse(String s, Role def) {
        if (s == null) return def;
        try {
            return valueOf(s.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
