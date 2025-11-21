package com.tmd.entity.dto.call;

/**
 * 支持的通话类型
 */
public enum CallType {
    VOICE,
    VIDEO;

    /**
     * 兼容字符串入参
     */
    public static CallType fromString(String raw) {
        if (raw == null) {
            return VOICE;
        }
        try {
            return CallType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return VOICE;
        }
    }
}
