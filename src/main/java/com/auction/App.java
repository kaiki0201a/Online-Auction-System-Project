package com.auction;

import com.auction.view.AdminView;
import com.auction.view.SellerView;
import com.auction.utils.NotificationUtil;
import com.auction.utils.UIUtils;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class App extends Application {

    private StackPane rootNode;
    private VBox contentArea;

    @Override
    public void start(Stage primaryStage) {
        rootNode = new StackPane();
        
        VBox mainContainer = new VBox();
        
        // Top Navigation
        HBox navBar = new HBox(15);
        navBar.setPadding(new Insets(15));
        navBar.setStyle("-fx-background-color: #2196F3;");
        navBar.setAlignment(Pos.CENTER_LEFT);
        
        Label brand = new Label("Auction System");
        brand.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold;");
        
        Button btnSeller = new Button("Seller View");
        btnSeller.setStyle("-fx-cursor: hand;");
        btnSeller.setOnAction(e -> navigateTo(new SellerView()));
        
        Button btnAdmin = new Button("Admin Dashboard");
        btnAdmin.setStyle("-fx-cursor: hand;");
        btnAdmin.setOnAction(e -> navigateTo(new AdminView()));
        
        Button btnTestNotification1 = new Button("Test: Bị vượt giá");
        btnTestNotification1.setStyle("-fx-cursor: hand;");
        btnTestNotification1.setOnAction(e -> {
            NotificationUtil.showToast("Bạn đã bị vượt giá ở sản phẩm iPhone 15!", rootNode, "warning");
        });
        
        Button btnTestNotification2 = new Button("Test: Thắng cuộc");
        btnTestNotification2.setStyle("-fx-cursor: hand;");
        btnTestNotification2.setOnAction(e -> {
            NotificationUtil.showToast("Chúc mừng! Bạn đã thắng cuộc phiên đấu giá!", rootNode, "success");
        });

        navBar.getChildren().addAll(brand, btnSeller, btnAdmin, btnTestNotification1, btnTestNotification2);
        
        contentArea = new VBox();
        contentArea.setAlignment(Pos.CENTER);
        Label initLabel = new Label("Chọn một màn hình trên thanh điều hướng");
        initLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #666;");
        contentArea.getChildren().add(initLabel);
        
        mainContainer.getChildren().addAll(navBar, contentArea);
        VBox.setVgrow(contentArea, javafx.scene.layout.Priority.ALWAYS);
        
        rootNode.getChildren().add(mainContainer);
        
        Scene scene = new Scene(rootNode, 900, 600);
        primaryStage.setTitle("Online Auction System - JavaFX");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
    private void navigateTo(javafx.scene.Node view) {
        UIUtils.showLoadingSpinner(rootNode, () -> {
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
            UIUtils.applyFadeIn(view);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}