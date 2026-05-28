package com.auction.notification;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * ToastManager — Quản lý hàng đợi toast notifications.
 *
 * Tính năng:
 *  - Stack nhiều toast: không đè nhau, cách nhau 12px theo chiều dọc
 *  - Fade-in 300ms → hiển thị N giây → Fade-out 400ms
 *  - Click để dismiss sớm
 *  - Thread-safe: luôn chạy trên JavaFX Application Thread
 *
 * Dùng inline style thay vì CSS class — Popup không kế thừa stylesheet
 * của parent scene nên CSS class sẽ không được áp dụng.
 */
public class ToastManager {

    // ─── Constants ────────────────────────────────────────────────────────────

    private static final double MARGIN_RIGHT  = 24;
    private static final double MARGIN_BOTTOM = 24;
    private static final double STACK_GAP     = 10;

    // ─── State ────────────────────────────────────────────────────────────────

    private final List<ToastEntry> active = new ArrayList<>();

    // ─── Inner record ─────────────────────────────────────────────────────────

    private record ToastEntry(Popup popup, Label label, double height) {}

    // ─── Public API ───────────────────────────────────────────────────────────

    public void show(Window window, String message, NotificationType type, int durationSeconds) {
        if (window == null || message == null || message.isBlank()) return;

        Platform.runLater(() -> {
            Popup popup = new Popup();
            popup.setAutoFix(false);

            // ─── Label với inline style ──────────────────────────────────────
            Label toastLabel = new Label(type.getIcon() + "  " + message);
            toastLabel.setStyle(buildStyle(type));
            toastLabel.setPadding(new Insets(11, 18, 11, 18));
            toastLabel.setOpacity(0);
            toastLabel.setWrapText(false);
            toastLabel.setMaxWidth(440);

            // Click to dismiss
            toastLabel.setOnMouseClicked(e -> dismissToast(popup, toastLabel));

            // Hover: brighten slightly
            toastLabel.setOnMouseEntered(e ->
                toastLabel.setStyle(buildStyle(type) + " -fx-opacity: 0.92;"));
            toastLabel.setOnMouseExited(e ->
                toastLabel.setStyle(buildStyle(type)));

            popup.getContent().add(toastLabel);

            // Show tạm để đo kích thước (vị trí off-screen)
            popup.show(window, -9999, -9999);

            // Đợi JavaFX layout xong để có width/height đúng
            Platform.runLater(() -> {
                double toastH = Math.max(toastLabel.getHeight(), 46);
                double toastW = toastLabel.getWidth();
                if (toastW < 10) toastW = 300; // fallback

                double yOffset = computeYOffset();

                double x = window.getX() + window.getWidth()  - toastW - MARGIN_RIGHT;
                double y = window.getY() + window.getHeight() - toastH - MARGIN_BOTTOM - yOffset;
                popup.setX(x);
                popup.setY(y);

                ToastEntry entry = new ToastEntry(popup, toastLabel, toastH);
                active.add(entry);

                // ─── Animation ───────────────────────────────────────────────
                Timeline fadeIn = buildFadeIn(toastLabel);
                Timeline fadeOut = buildFadeOut(toastLabel, () -> {
                    popup.hide();
                    active.remove(entry);
                    repositionAll(window);
                });

                if (durationSeconds > 0) {
                    PauseTransition pause = new PauseTransition(Duration.seconds(durationSeconds));
                    pause.setOnFinished(e -> fadeOut.play());
                    fadeIn.setOnFinished(e -> pause.play());
                }
                // durationSeconds <= 0 → không tự ẩn, user phải click

                fadeIn.play();
            });
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /** Build inline style dựa vào NotificationType — không phụ thuộc CSS file. */
    private static String buildStyle(NotificationType type) {
        String bg = switch (type) {
            case SUCCESS -> "rgba(30, 132, 73, 0.97)";   // xanh lá đậm
            case ERROR   -> "rgba(192, 57, 43, 0.97)";   // đỏ đậm
            case WARNING -> "rgba(175, 96, 26, 0.97)";   // cam đậm
            case INFO    -> "rgba(31, 97, 141, 0.97)";   // xanh dương đậm
        };
        String border = switch (type) {
            case SUCCESS -> "#27ae60";
            case ERROR   -> "#e74c3c";
            case WARNING -> "#e67e22";
            case INFO    -> "#2980b9";
        };

        return "-fx-background-color: " + bg + ";" +
               "-fx-text-fill: #FFFFFF;" +
               "-fx-font-size: 13px;" +
               "-fx-font-weight: bold;" +
               "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
               "-fx-background-radius: 8;" +
               "-fx-border-color: " + border + ";" +
               "-fx-border-width: 1.2;" +
               "-fx-border-radius: 8;" +
               "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.65), 16, 0, 0, 4);" +
               "-fx-cursor: hand;";
    }

    /** Tổng chiều cao của các toast đang active + gap. */
    private double computeYOffset() {
        return active.stream()
                     .mapToDouble(e -> e.height() + STACK_GAP)
                     .sum();
    }

    /** Reposition tất cả toast còn lại về đúng vị trí stack. */
    private void repositionAll(Window window) {
        double yOffset = 0;
        for (int i = active.size() - 1; i >= 0; i--) {
            ToastEntry e = active.get(i);
            double y = window.getY() + window.getHeight()
                     - e.height() - MARGIN_BOTTOM - yOffset;
            e.popup().setY(y);
            yOffset += e.height() + STACK_GAP;
        }
    }

    private void dismissToast(Popup popup, Label label) {
        buildFadeOut(label, popup::hide).play();
    }

    private Timeline buildFadeIn(Label label) {
        return new Timeline(
            new KeyFrame(Duration.ZERO,        new KeyValue(label.opacityProperty(), 0)),
            new KeyFrame(Duration.millis(280), new KeyValue(label.opacityProperty(), 1))
        );
    }

    private Timeline buildFadeOut(Label label, Runnable onFinished) {
        Timeline fadeOut = new Timeline(
            new KeyFrame(Duration.ZERO,        new KeyValue(label.opacityProperty(), 1)),
            new KeyFrame(Duration.millis(380), new KeyValue(label.opacityProperty(), 0))
        );
        fadeOut.setOnFinished(e -> onFinished.run());
        return fadeOut;
    }
}
