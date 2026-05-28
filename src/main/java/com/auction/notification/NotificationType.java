package com.auction.notification;

/**
 * NotificationType — Enum định nghĩa các loại thông báo trong hệ thống.
 *
 * Mỗi type có:
 *  - icon        : emoji hiển thị trong toast/dialog
 *  - cssClass    : CSS class cho styling
 *  - accentColor : màu hex dùng khi cần inline styling
 *  - duration    : thời gian tự ẩn mặc định (giây), -1 = không tự ẩn
 */
public enum NotificationType {

    SUCCESS("✅", "toast-success", "#27ae60", 3),
    ERROR  ("❌", "toast-error",   "#e74c3c", 4),
    WARNING("⚠️", "toast-warning", "#f39c12", 4),
    INFO   ("ℹ️", "toast-info",    "#3498db", 3);

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final String icon;
    private final String cssClass;
    private final String accentColor;
    private final int    defaultDurationSeconds;

    // ─── Constructor ──────────────────────────────────────────────────────────

    NotificationType(String icon, String cssClass, String accentColor, int defaultDurationSeconds) {
        this.icon                   = icon;
        this.cssClass               = cssClass;
        this.accentColor            = accentColor;
        this.defaultDurationSeconds = defaultDurationSeconds;
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public String getIcon()                   { return icon; }
    public String getCssClass()               { return cssClass; }
    public String getAccentColor()            { return accentColor; }
    public int    getDefaultDurationSeconds() { return defaultDurationSeconds; }

    /**
     * Chuyển từ String (legacy API) sang enum.
     * Cho phép backward-compatible với code cũ dùng "success", "error", "warning".
     */
    public static NotificationType fromString(String type) {
        if (type == null) return INFO;
        return switch (type.toLowerCase()) {
            case "success" -> SUCCESS;
            case "error"   -> ERROR;
            case "warning" -> WARNING;
            case "info"    -> INFO;
            default        -> INFO;
        };
    }
}
