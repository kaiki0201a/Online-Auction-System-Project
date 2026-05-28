package com.auction.utils;

import com.auction.notification.ConfirmDialog;
import com.auction.notification.NotificationService;
import com.auction.notification.NotificationType;
import javafx.scene.Node;
import javafx.stage.Window;

import java.util.Optional;

/**
 * NotificationUtil — Facade backward-compatible cho NotificationService.
 *
 * Class này giữ lại API cũ để không cần sửa toàn bộ controller cũ.
 * Tất cả phương thức delegate sang NotificationService (singleton mới).
 *
 * ✅ Code cũ vẫn hoạt động: NotificationUtil.showToast(...)
 * ✅ Code mới dùng clean API: NotificationService.get().success(...)
 *
 * @deprecated Sử dụng {@link NotificationService} trực tiếp cho code mới.
 */
public class NotificationUtil {

    private NotificationUtil() {}

    // ═══════════════════════════════════════════════════════════════════════════
    //  TOAST (legacy API — backward-compatible)
    // ═══════════════════════════════════════════════════════════════════════════

    /** @deprecated Dùng {@link NotificationService#get()}.success/error/warning/info(msg, anchor) */
    public static void showToast(String message, Node anchorNode, String type) {
        if (anchorNode == null || anchorNode.getScene() == null) return;
        NotificationService.get().toast(message, NotificationType.fromString(type), anchorNode);
    }

    /** @deprecated Dùng {@link NotificationService#get()}.toastOnWindow(msg, type, window) */
    public static void showToastOnWindow(String message, Window window, String type) {
        if (window == null) return;
        NotificationService.get().toastOnWindow(message, NotificationType.fromString(type), window);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CONFIRM DIALOG (legacy API — backward-compatible)
    // ═══════════════════════════════════════════════════════════════════════════

    /** @deprecated Dùng {@link ConfirmDialog} preset methods hoặc {@link NotificationService#get()}.confirm(...) */
    public static Optional<Boolean> showConfirm(Window owner, String title,
                                                 String message,
                                                 String yesLabel, String noLabel) {
        return ConfirmDialog.show(owner, title, message, yesLabel, noLabel, true);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  ALERT DIALOG (legacy API — backward-compatible)
    // ═══════════════════════════════════════════════════════════════════════════

    /** @deprecated Dùng {@link NotificationService#get()}.alert(...) */
    public static void showAlert(Window owner, String title, String message, String type) {
        NotificationService.get().alert(owner, title, message, NotificationType.fromString(type));
    }
}