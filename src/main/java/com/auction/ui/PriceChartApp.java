package com.auction.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class PriceChartApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        URL fxmlLocation = getClass().getResource("/com/auction/ui/PriceChart.fxml");
        
        if (fxmlLocation == null) {
            throw new RuntimeException("Trời ơi! Không tìm thấy file PriceChart.fxml trong thư mục resources!");
        }

        Parent root = FXMLLoader.load(fxmlLocation);

        Scene scene = new Scene(root, 800, 600); // Mở cửa sổ rộng 800px, cao 600px

        primaryStage.setTitle("Phòng Chế Tạo Biểu Đồ - Thành viên D");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // Công tắc nguồn ở đây!
    public static void main(String[] args) {
        launch(args);
    }
}