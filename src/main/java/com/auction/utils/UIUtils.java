package com.auction.utils;

import javafx.animation.FadeTransition;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * UIUtils — Utility class các hiệu ứng UI dùng chung (fade-in, spinner overlay).
 */
public class UIUtils {

    /** Utility class — không cho phép khởi tạo. */
    private UIUtils() {}

    /** Thời gian hiển thị spinner trước khi chạy action (ms). */
    private static final int SPINNER_DELAY_MS = 1500;

    /** Áp dụng hiệu ứng fade-in vào một Node JavaFX (500ms). */
    public static void applyFadeIn(Node node) {
        FadeTransition ft = new FadeTransition(Duration.millis(500), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    /**
     * Hiển thị overlay spinner trong {@code SPINNER_DELAY_MS}ms, sau đó chạy {@code actionAfterDelay}.
     *
     * Lưu ý: spinner là fixed delay — không gắn với thời gian response server thực tế.
     */
    public static void showLoadingSpinner(StackPane root, Runnable actionAfterDelay) {
        Rectangle overlay = new Rectangle(root.getWidth(), root.getHeight(), Color.rgb(0, 0, 0, 0.4));
        overlay.widthProperty().bind(root.widthProperty());
        overlay.heightProperty().bind(root.heightProperty());

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setStyle("-fx-progress-color: white;");
        spinner.setMaxSize(50, 50);

        VBox loadingBox = new VBox(spinner);
        loadingBox.setAlignment(Pos.CENTER);

        StackPane overlayPane = new StackPane(overlay, loadingBox);
        root.getChildren().add(overlayPane);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), overlayPane);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Thread.sleep(SPINNER_DELAY_MS);
                return null;
            }
        };
        
        task.setOnSucceeded(e -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), overlayPane);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(ev -> {
                root.getChildren().remove(overlayPane);
                if (actionAfterDelay != null) {
                    actionAfterDelay.run();
                }
            });
            fadeOut.play();
        });
        
        new Thread(task).start();
    }
}
