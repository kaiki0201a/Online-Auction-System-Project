package com.auction.notification;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.stage.Window;

import java.util.Optional;

/**
 * NotificationService — Entry point DUY NHẤT cho mọi notification trong toàn app.
 *
 * Singleton. Controller chỉ cần import class này và gọi:
 *   NotificationService.get().toast("...", NotificationType.SUCCESS, node);
 *   NotificationService.get().confirm(...);
 *
 * Tại sao cần class này thay vì gọi trực tiếp ToastManager/ConfirmDialog?
 *  - Single Responsibility: controller không biết gì về Popup, Stage, animation
 *  - Testable: có thể mock NotificationService trong unit tests
 *  - Centralized: muốn thay đổi cách hiển thị toast → chỉ sửa 1 chỗ
 *
 * ⚠️  Tất cả phương thức phải gọi trên JavaFX Application Thread.
 *     Nếu gọi từ background thread, dùng Platform.runLater().
 */
public final class NotificationService {

    // ─── Singleton ────────────────────────────────────────────────────────────

    private static final NotificationService INSTANCE = new NotificationService();
    private final ToastManager toastManager = new ToastManager();

    private NotificationService() {}

    public static NotificationService get() { return INSTANCE; }

    // ═══════════════════════════════════════════════════════════════════════════
    //  TOAST — thông báo nhỏ tự ẩn
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Hiện toast từ một Node trong scene (tự resolve Window).
     *
     * @param message Nội dung (tối đa ~80 ký tự để hiển thị tốt)
     * @param type    Loại thông báo
     * @param anchor  Bất kỳ Node nào trong cùng cửa sổ
     */
    public void toast(String message, NotificationType type, Node anchor) {
        if (anchor == null || anchor.getScene() == null) return;
        toastOnWindow(message, type, anchor.getScene().getWindow(), type.getDefaultDurationSeconds());
    }

    /**
     * Hiện toast với duration tùy chỉnh.
     */
    public void toast(String message, NotificationType type, Node anchor, int durationSeconds) {
        if (anchor == null || anchor.getScene() == null) return;
        toastOnWindow(message, type, anchor.getScene().getWindow(), durationSeconds);
    }

    /**
     * Hiện toast trực tiếp từ Window (khi không có Node tham chiếu).
     */
    public void toastOnWindow(String message, NotificationType type, Window window) {
        toastOnWindow(message, type, window, type.getDefaultDurationSeconds());
    }

    /**
     * Hiện toast trực tiếp từ Window với duration tùy chỉnh.
     * durationSeconds = -1 → không tự ẩn, user phải click.
     */
    public void toastOnWindow(String message, NotificationType type, Window window,
                               int durationSeconds) {
        if (window == null || message == null) return;
        ensureFxThread(() ->
            toastManager.show(window, message, type, durationSeconds)
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  SHORTCUT TOAST METHODS — clean, readable call sites
    // ═══════════════════════════════════════════════════════════════════════════

    public void success(String message, Node anchor) { toast(message, NotificationType.SUCCESS, anchor); }
    public void error  (String message, Node anchor) { toast(message, NotificationType.ERROR,   anchor); }
    public void warning(String message, Node anchor) { toast(message, NotificationType.WARNING,  anchor); }
    public void info   (String message, Node anchor) { toast(message, NotificationType.INFO,    anchor); }

    public void success(String message, Window w) { toastOnWindow(message, NotificationType.SUCCESS, w); }
    public void error  (String message, Window w) { toastOnWindow(message, NotificationType.ERROR,   w); }
    public void warning(String message, Window w) { toastOnWindow(message, NotificationType.WARNING,  w); }
    public void info   (String message, Window w) { toastOnWindow(message, NotificationType.INFO,    w); }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CONFIRM DIALOG — delegate sang ConfirmDialog
    // ═══════════════════════════════════════════════════════════════════════════

    public Optional<Boolean> confirm(Window owner, String title, String body,
                                      String yesLabel, String noLabel, boolean danger) {
        return ConfirmDialog.show(owner, title, body, yesLabel, noLabel, danger);
    }

    // Preset shortcuts
    public Optional<Boolean> confirmLogout     (Window owner)                      { return ConfirmDialog.logout(owner); }
    public Optional<Boolean> confirmCancelAuction(Window owner, String itemName)   { return ConfirmDialog.cancelAuction(owner, itemName); }
    public Optional<Boolean> confirmBanUser    (Window owner, String username)     { return ConfirmDialog.banUser(owner, username); }
    public Optional<Boolean> confirmUnbanUser  (Window owner, String username)     { return ConfirmDialog.unbanUser(owner, username); }
    public Optional<Boolean> confirmLargeBid   (Window owner, double amount)       { return ConfirmDialog.placeLargeBid(owner, amount); }
    public Optional<Boolean> confirmWithdraw   (Window owner, double amount)       { return ConfirmDialog.withdraw(owner, amount); }
    public Optional<Boolean> confirmRejectProduct(Window owner, String itemName)   { return ConfirmDialog.rejectProduct(owner, itemName); }

    // ═══════════════════════════════════════════════════════════════════════════
    //  ALERT DIALOG — thông báo chỉ có nút OK (dark theme)
    // ═══════════════════════════════════════════════════════════════════════════

    public void alert(Window owner, String title, String body, NotificationType type) {
        ensureFxThread(() -> showAlertDialog(owner, title, body, type));
    }

    private void showAlertDialog(Window owner, String title, String body, NotificationType type) {
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        if (owner != null) dialog.initOwner(owner);
        dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);
        dialog.initStyle(javafx.stage.StageStyle.UNDECORATED);

        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(18);
        root.setAlignment(javafx.geometry.Pos.CENTER);
        root.setPadding(new javafx.geometry.Insets(30, 44, 26, 44));
        root.setPrefWidth(400);
        root.setStyle(
            "-fx-background-color: #151515;" +
            "-fx-border-color: #2A2A2A; -fx-border-width: 1;" +
            "-fx-border-radius: 14; -fx-background-radius: 14;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 30, 0, 0, 6);"
        );

        javafx.scene.control.Label lblTitle = new javafx.scene.control.Label(
            type.getIcon() + "  " + title);
        lblTitle.setStyle("-fx-text-fill: " + type.getAccentColor() +
                          "; -fx-font-size: 15px; -fx-font-weight: bold;");
        lblTitle.setWrapText(true);

        javafx.scene.control.Label lblBody = new javafx.scene.control.Label(body);
        lblBody.setStyle("-fx-text-fill: #BBBBBB; -fx-font-size: 13px;");
        lblBody.setWrapText(true);
        lblBody.setMaxWidth(312);
        lblBody.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        javafx.scene.control.Button btnOk = new javafx.scene.control.Button("  OK  ");
        btnOk.setStyle(
            "-fx-background-color: " + type.getAccentColor() + "; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-font-size: 13px;" +
            "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 8 28;"
        );
        btnOk.setOnAction(e -> dialog.close());

        javafx.scene.layout.HBox btnBox = new javafx.scene.layout.HBox(btnOk);
        btnBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        root.getChildren().addAll(lblTitle, lblBody, btnBox);

        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    // ─── Thread safety ────────────────────────────────────────────────────────

    private void ensureFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
