package com.auction.utils;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * NotificationUtil — Bộ công cụ hiển thị thông báo cho toàn bộ ứng dụng.
 *
 * Bao gồm:
 *  - showToast()    : Thông báo nhỏ góc dưới phải (3 giây tự ẩn)
 *  - showConfirm()  : Dialog xác nhận YES/NO theo theme dark
 *  - showAlert()    : Dialog thông báo (OK) theo theme dark
 */
public class NotificationUtil {

    // ═══════════════════════════════════════════════════════════════════════
    //  TOAST — thông báo nhỏ tự ẩn, góc dưới phải
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Hiển thị toast notification.
     *
     * @param message    Nội dung thông báo
     * @param anchorNode Node trong scene cùng cửa sổ
     * @param type       "success" | "error" | "warning" | null (default)
     */
    public static void showToast(String message, Node anchorNode, String type) {
        if (anchorNode == null || anchorNode.getScene() == null) return;
        Window window = anchorNode.getScene().getWindow();
        showToastOnWindow(message, window, type);
    }

    /**
     * Hiển thị toast từ Window (khi không có Node tham chiếu).
     */
    public static void showToastOnWindow(String message, Window window, String type) {
        if (window == null) return;

        Popup popup = new Popup();
        Label toastLabel = new Label(message);

        toastLabel.getStyleClass().add("toast");
        if (type != null && !type.isBlank()) {
            toastLabel.getStyleClass().add("toast-" + type);
        } else {
            toastLabel.getStyleClass().add("toast-default");
        }

        toastLabel.setOpacity(0);
        popup.getContent().add(toastLabel);

        popup.setOnShown(e -> {
            popup.setX(window.getX() + window.getWidth() - popup.getWidth() - 24);
            popup.setY(window.getY() + window.getHeight() - popup.getHeight() - 24);
        });

        popup.show(window);

        // Fade in
        Timeline fadeIn = new Timeline(
                new KeyFrame(Duration.ZERO,          new KeyValue(toastLabel.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(300),   new KeyValue(toastLabel.opacityProperty(), 1))
        );

        // Fade out
        Timeline fadeOut = new Timeline(
                new KeyFrame(Duration.ZERO,          new KeyValue(toastLabel.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(500),   new KeyValue(toastLabel.opacityProperty(), 0))
        );
        fadeOut.setOnFinished(e -> popup.hide());

        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(e -> fadeOut.play());
        fadeIn.setOnFinished(e -> delay.play());
        fadeIn.play();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CONFIRM DIALOG — xác nhận YES / NO theo dark theme
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Hiển thị dialog xác nhận YES/NO với dark theme nhất quán.
     *
     * @param owner    Cửa sổ cha (có thể null)
     * @param title    Tiêu đề dialog
     * @param message  Nội dung câu hỏi
     * @param yesLabel Nhãn nút xác nhận (ví dụ: "🔒 Khóa tài khoản")
     * @param noLabel  Nhãn nút hủy (ví dụ: "Hủy")
     * @return Optional<Boolean> — true nếu bấm YES, false nếu bấm NO, empty nếu đóng
     */
    public static Optional<Boolean> showConfirm(Window owner, String title,
                                                 String message,
                                                 String yesLabel, String noLabel) {
        final boolean[] result = {false};
        final boolean[] acted  = {false};

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.setTitle(title);

        // ─── Layout ───────────────────────────────────────────────────────
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(32, 40, 28, 40));
        root.setPrefWidth(420);
        root.setStyle(
            "-fx-background-color: #1A1A1A;" +
            "-fx-border-color: #333;" +
            "-fx-border-width: 1.5;" +
            "-fx-border-radius: 12;" +
            "-fx-background-radius: 12;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.7), 24, 0, 0, 4);"
        );

        // Icon + Tiêu đề
        Label lblTitle = new Label("⚠️  " + title);
        lblTitle.setStyle("-fx-text-fill: #F5C518; -fx-font-size: 16px; -fx-font-weight: bold;");
        lblTitle.setAlignment(Pos.CENTER);
        lblTitle.setWrapText(true);

        // Nội dung
        Label lblMessage = new Label(message);
        lblMessage.setStyle("-fx-text-fill: #CCC; -fx-font-size: 13px;");
        lblMessage.setWrapText(true);
        lblMessage.setMaxWidth(340);
        lblMessage.setAlignment(Pos.CENTER);
        lblMessage.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        // Nút YES
        Button btnYes = new Button(yesLabel);
        btnYes.setPrefWidth(160);
        btnYes.setStyle(
            "-fx-background-color: #e74c3c; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-font-size: 13px;" +
            "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 20;"
        );
        btnYes.setOnAction(e -> { result[0] = true; acted[0] = true; dialog.close(); });

        // Nút NO
        Button btnNo = new Button(noLabel);
        btnNo.setPrefWidth(120);
        btnNo.setStyle(
            "-fx-background-color: #2A2A2A; -fx-text-fill: #AAA;" +
            "-fx-font-size: 13px;" +
            "-fx-border-color: #444; -fx-border-radius: 8; -fx-border-width: 1;" +
            "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 20;"
        );
        btnNo.setOnAction(e -> { result[0] = false; acted[0] = true; dialog.close(); });

        HBox btnBox = new HBox(12, btnNo, btnYes);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(lblTitle, lblMessage, btnBox);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.showAndWait();

        return acted[0] ? Optional.of(result[0]) : Optional.empty();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  INFO DIALOG — thông báo chỉ có nút OK, dark theme
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Hiển thị dialog thông báo thông tin (chỉ có nút OK).
     *
     * @param owner   Cửa sổ cha
     * @param title   Tiêu đề
     * @param message Nội dung
     * @param type    "success" | "error" | "warning" | "info"
     */
    public static void showAlert(Window owner, String title, String message, String type) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);

        VBox root = new VBox(18);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30, 40, 26, 40));
        root.setPrefWidth(380);
        root.setStyle(
            "-fx-background-color: #1A1A1A;" +
            "-fx-border-color: #333;" +
            "-fx-border-width: 1.5;" +
            "-fx-border-radius: 12;" +
            "-fx-background-radius: 12;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.7), 24, 0, 0, 4);"
        );

        // Icon theo loại
        String icon = switch (type) {
            case "success" -> "✅";
            case "error"   -> "❌";
            case "warning" -> "⚠️";
            default        -> "ℹ️";
        };
        String titleColor = switch (type) {
            case "success" -> "#27ae60";
            case "error"   -> "#e74c3c";
            case "warning" -> "#f39c12";
            default        -> "#3498db";
        };

        Label lblTitle = new Label(icon + "  " + title);
        lblTitle.setStyle("-fx-text-fill: " + titleColor + "; -fx-font-size: 15px; -fx-font-weight: bold;");
        lblTitle.setWrapText(true);

        Label lblMessage = new Label(message);
        lblMessage.setStyle("-fx-text-fill: #CCC; -fx-font-size: 13px;");
        lblMessage.setWrapText(true);
        lblMessage.setMaxWidth(300);
        lblMessage.setAlignment(Pos.CENTER);
        lblMessage.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Button btnOk = new Button("  OK  ");
        btnOk.setStyle(
            "-fx-background-color: " + titleColor + "; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-font-size: 13px;" +
            "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 8 28;"
        );
        btnOk.setOnAction(e -> dialog.close());

        HBox btnBox = new HBox(btnOk);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(lblTitle, lblMessage, btnBox);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.showAndWait();
    }
}