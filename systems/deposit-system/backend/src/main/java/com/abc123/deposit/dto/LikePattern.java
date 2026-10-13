package com.abc123.deposit.dto;

final class LikePattern {

    private LikePattern() {
    }

    static String escape(String value) {
        return value == null ? null : value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
