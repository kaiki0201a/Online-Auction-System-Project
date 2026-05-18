package com.auction.utils;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

public class NotificationUtil {

    // 1. Thay 'StackPane root' bằng 'Node anchorNode'. Cấm tiệt việc ép kiểu!
    public static void showToast(String message, Node anchorNode, String type) {
        if (anchorNode == null || anchorNode.getScene() == null) return;

        // Lấy Window hiện tại từ Node
        Window window = anchorNode.getScene().getWindow();

        // 2. Sử dụng Popup làm Overlay độc lập
        Popup popup = new Popup();
        Label toastLabel = new Label(message);

        // 3. XÓA TOÀN BỘ CSS CỨNG. Chuyển sang dùng styleClass
        toastLabel.getStyleClass().add("toast");
        if (type != null) {
            toastLabel.getStyleClass().add("toast-" + type);
        } else {
            toastLabel.getStyleClass().add("toast-default");
        }

        toastLabel.setOpacity(0);
        popup.getContent().add(toastLabel);

        // Tính toán vị trí hiển thị (Góc dưới bên phải màn hình App)
        popup.setOnShown(e -> {
            popup.setX(window.getX() + window.getWidth() - popup.getWidth() - 20);
            popup.setY(window.getY() + window.getHeight() - popup.getHeight() - 20);
        });

        // Hiển thị Popup
        popup.show(window);

        // Hiệu ứng Fade in
        Timeline fadeIn = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(toastLabel.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(300), new KeyValue(toastLabel.opacityProperty(), 1))
        );

        // Hiệu ứng Fade out
        Timeline fadeOut = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(toastLabel.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(500), new KeyValue(toastLabel.opacityProperty(), 0))
        );
        fadeOut.setOnFinished(e -> popup.hide());

        // 4. Dùng PauseTransition chuẩn của JavaFX thay vì Thread.sleep()
        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(e -> fadeOut.play());

        fadeIn.setOnFinished(e -> delay.play());
        fadeIn.play();
    }
}