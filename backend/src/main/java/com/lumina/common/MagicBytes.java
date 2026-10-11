package com.lumina.common;

public final class MagicBytes {
    private MagicBytes() {}

    public static boolean isMp4(byte[] header) {
        if (header == null || header.length < 12) return false;
        int limit = Math.min(header.length - 3, 32);
        for (int i = 4; i < limit; i++) {
            if (header[i] == 'f' && header[i + 1] == 't' && header[i + 2] == 'y' && header[i + 3] == 'p') {
                return true;
            }
        }
        return false;
    }

    public static boolean isWebm(byte[] header) {
        return header != null && header.length >= 4
                && (header[0] & 0xFF) == 0x1A
                && (header[1] & 0xFF) == 0x45
                && (header[2] & 0xFF) == 0xDF
                && (header[3] & 0xFF) == 0xA3;
    }

    public static boolean isJpeg(byte[] header) {
        return header != null && header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF;
    }

    public static boolean isPng(byte[] header) {
        return header != null && header.length >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 'P' && header[2] == 'N' && header[3] == 'G';
    }

    public static boolean isWebp(byte[] header) {
        return header != null && header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
    }
}
