package com.auction;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 1. Kết nối tới Server của bạn C trước khi hiện UI
        com.auction.client.NetworkClient.getInstance().connect("localhost", 8888);

        Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Login.fxml"));
        primaryStage.setTitle("Hệ thống Đấu giá Online 2026");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    @Override
    public void stop() {
        // 2. Ngắt kết nối an toàn khi đóng app
        com.auction.client.NetworkClient.getInstance().disconnect();
    }

    // --- PHẦN BẠN BỊ THIẾU LÀ Ở ĐÂY ---
    public static void main(String[] args) {
        launch(args); // Lệnh này sẽ kích hoạt JavaFX và gọi hàm start() ở trên
    }
}