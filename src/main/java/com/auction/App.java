package com.auction;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        // Nạp giao diện Login từ resources
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/LoginView.fxml"));
        stage.setScene(new Scene(loader.load()));
        stage.setTitle("Hệ thống Đấu giá");
        stage.show();
    }
    public static void main(String[] args) { launch(args); }
}