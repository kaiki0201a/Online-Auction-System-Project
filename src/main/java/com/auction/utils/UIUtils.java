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

public class UIUtils {

    public static void applyFadeIn(Node node) {
        FadeTransition ft = new FadeTransition(Duration.millis(500), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    public static void showLoadingSpinner(StackPane root, Runnable actionAfterDelay) {
        // Create an overlay rectangle
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
        
        // Fade in overlay
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), overlayPane);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        // Simulate server response delay
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Thread.sleep(1500); // Simulate 1.5s delay
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
