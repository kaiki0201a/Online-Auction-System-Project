package com.auction.utils;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class NotificationUtil {
    
    public static void showToast(String message, StackPane root, String type) {
        if (root == null) return;

        Label toastLabel = new Label(message);
        
        String baseStyle = "-fx-text-fill: white; -fx-padding: 15px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-font-weight: bold; " +
                           "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 10, 0, 0, 5);";
        if ("error".equals(type)) {
            toastLabel.setStyle(baseStyle + "-fx-background-color: #f44336;"); // Red
        } else if ("success".equals(type)) {
            toastLabel.setStyle(baseStyle + "-fx-background-color: #4CAF50;"); // Green
        } else if ("warning".equals(type)) {
            toastLabel.setStyle(baseStyle + "-fx-background-color: #ff9800;"); // Orange
        } else {
            toastLabel.setStyle(baseStyle + "-fx-background-color: #333333;"); // Default dark
        }
        
        toastLabel.setOpacity(0);
        
        StackPane.setAlignment(toastLabel, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(toastLabel, new javafx.geometry.Insets(0, 20, 20, 0));
        
        root.getChildren().add(toastLabel);

        // Fade in
        Timeline fadeInTimeline = new Timeline();
        KeyFrame fadeInKey1 = new KeyFrame(Duration.millis(0), new KeyValue(toastLabel.opacityProperty(), 0));
        KeyFrame fadeInKey2 = new KeyFrame(Duration.millis(300), new KeyValue(toastLabel.opacityProperty(), 1));
        fadeInTimeline.getKeyFrames().addAll(fadeInKey1, fadeInKey2);
        
        // Fade out
        Timeline fadeOutTimeline = new Timeline();
        KeyFrame fadeOutKey1 = new KeyFrame(Duration.millis(0), new KeyValue(toastLabel.opacityProperty(), 1));
        KeyFrame fadeOutKey2 = new KeyFrame(Duration.millis(500), new KeyValue(toastLabel.opacityProperty(), 0));
        fadeOutTimeline.getKeyFrames().addAll(fadeOutKey1, fadeOutKey2);
        
        fadeOutTimeline.setOnFinished((e) -> root.getChildren().remove(toastLabel));
        
        fadeInTimeline.setOnFinished((e) -> {
            new Thread(() -> {
                try {
                    Thread.sleep(3000); // Show for 3 seconds
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                javafx.application.Platform.runLater(fadeOutTimeline::play);
            }).start();
        });

        fadeInTimeline.play();
    }
}
