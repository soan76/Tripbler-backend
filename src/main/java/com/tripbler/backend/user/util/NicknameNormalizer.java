package com.tripbler.backend.user.util;

public final class NicknameNormalizer {

    private NicknameNormalizer() {
    }

    public static String normalize(String nickname) {
        if (nickname == null) {
            return null;
        }

        String normalized = nickname.trim();

        return normalized.isEmpty()
            ? null
            : normalized;
    }
}