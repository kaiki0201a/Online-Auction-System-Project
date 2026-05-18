package com.auction;

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
        
        // Thanh điều hướng (NavBar)
        HBox navBar = new HBox(15);
        navBar.setPadding(new Insets(15));
        navBar.setStyle("-fx-background-color: #2196F3;");
        navBar.setAlignment(Pos.CENTER_LEFT);
        
        Label brand = new Label("Auction System");
        brand.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold;");
        
        // Nút chuyển sang màn hình Đăng sản phẩm (Đã fix dùng FXML)
        Button btnSeller = new Button("Seller View");
        btnSeller.setStyle("-fx-cursor: hand;");
        btnSeller.setOnAction(e -> {
            javafx.scene.Node view = loadFXML("/com/auction/view/AddProduct.fxml");
            if (view != null) navigateTo(view);
        });
        
        // Nút chuyển sang màn hình Admin (Đã fix dùng FXML)
        Button btnAdmin = new Button("Admin Dashboard");
        btnAdmin.setStyle("-fx-cursor: hand;");
        btnAdmin.setOnAction(e -> {
            javafx.scene.Node view = loadFXML("/com/auction/view/AdminDashboard.fxml");
            if (view != null) navigateTo(view);
        });
        
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
        
        // Nhúng CSS tổng (Tùy chọn, nhớ sửa đường dẫn nếu cần)
        try {
            String cssPath = getClass().getResource("/com/auction/view/styles.css").toExternalForm();
            scene.getStylesheets().add(cssPath);
        } catch (Exception ex) {
            System.out.println("Chưa tìm thấy styles.css tổng, bỏ qua load CSS.");
        }

        primaryStage.setTitle("Online Auction System - JavaFX");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // Hàm phụ trợ dùng để đọc file FXML an toàn
    private javafx.scene.Node loadFXML(String fxmlPath) {
        try {
            return javafx.fxml.FXMLLoader.load(getClass().getResource(fxmlPath));
        } catch (java.io.IOException e) {
            e.printStackTrace();
            System.err.println("Lỗi: Không tìm thấy file FXML tại " + fxmlPath);
            return null;
        }
    }

    private void navigateTo(javafx.scene.Node view) {
        UIUtils.showLoadingSpinner(rootNode, () -> {
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
            UIUtils.applyFadeIn(view);
        });
    }

    public static int add(int a, int b) {
        return a + b;
    }

    public static void main(String[] args) {
        launch(args);
    }
}