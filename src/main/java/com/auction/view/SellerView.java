package com.auction.view;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class SellerView extends VBox {
    public SellerView() {
        setSpacing(15);
        setPadding(new Insets(20));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(600);

        Label title = new Label("Đăng sản phẩm mới");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        // Basic Info
        TextField txtName = new TextField();
        txtName.setPromptText("Tên sản phẩm");
        TextField txtStartingPrice = new TextField();
        txtStartingPrice.setPromptText("Giá khởi điểm ($)");
        TextArea txtDescription = new TextArea();
        txtDescription.setPromptText("Mô tả sản phẩm");
        txtDescription.setPrefRowCount(3);

        // Category selection
        ComboBox<String> categoryBox = new ComboBox<>(FXCollections.observableArrayList(
                "Art", "Electronics", "Vehicle"
        ));
        categoryBox.setPromptText("Chọn danh mục");
        categoryBox.setMaxWidth(Double.MAX_VALUE);

        // Dynamic form container
        VBox dynamicForm = new VBox(10);
        dynamicForm.setStyle("-fx-padding: 15; -fx-border-color: #ccc; -fx-border-radius: 5; -fx-background-radius: 5; -fx-background-color: #f9f9f9;");
        dynamicForm.setVisible(false);
        dynamicForm.setManaged(false);
        
        categoryBox.setOnAction(e -> {
            dynamicForm.getChildren().clear();
            dynamicForm.setVisible(true);
            dynamicForm.setManaged(true);
            String cat = categoryBox.getValue();
            if ("Art".equals(cat)) {
                TextField txtArtist = new TextField();
                txtArtist.setPromptText("Tên họa sĩ / Nghệ nhân");
                TextField txtYear = new TextField();
                txtYear.setPromptText("Năm sáng tác");
                Label lbl = new Label("Thông tin Art:");
                lbl.setStyle("-fx-font-weight: bold");
                dynamicForm.getChildren().addAll(lbl, txtArtist, txtYear);
            } else if ("Electronics".equals(cat)) {
                TextField txtBrand = new TextField();
                txtBrand.setPromptText("Thương hiệu");
                TextField txtModel = new TextField();
                txtModel.setPromptText("Model");
                TextField txtCondition = new TextField();
                txtCondition.setPromptText("Tình trạng (Mới/Cũ)");
                Label lbl = new Label("Thông tin Electronics:");
                lbl.setStyle("-fx-font-weight: bold");
                dynamicForm.getChildren().addAll(lbl, txtBrand, txtModel, txtCondition);
            } else if ("Vehicle".equals(cat)) {
                TextField txtMake = new TextField();
                txtMake.setPromptText("Hãng sản xuất");
                TextField txtMileage = new TextField();
                txtMileage.setPromptText("Số KM đã đi");
                TextField txtEngine = new TextField();
                txtEngine.setPromptText("Loại động cơ");
                Label lbl = new Label("Thông tin Vehicle:");
                lbl.setStyle("-fx-font-weight: bold");
                dynamicForm.getChildren().addAll(lbl, txtMake, txtMileage, txtEngine);
            }
            com.auction.utils.UIUtils.applyFadeIn(dynamicForm);
        });

        Button btnSubmit = new Button("Đăng bán");
        btnSubmit.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20 10 20; -fx-font-size: 14px;");
        btnSubmit.setMaxWidth(Double.MAX_VALUE);
        btnSubmit.setOnAction(e -> {
            com.auction.utils.UIUtils.showLoadingSpinner((StackPane) this.getScene().getRoot(), () -> {
                com.auction.utils.NotificationUtil.showToast("Đã đăng sản phẩm thành công!", (StackPane) this.getScene().getRoot(), "success");
                // Reset form
                txtName.clear();
                txtStartingPrice.clear();
                txtDescription.clear();
                categoryBox.getSelectionModel().clearSelection();
                dynamicForm.setVisible(false);
                dynamicForm.setManaged(false);
            });
        });

        getChildren().addAll(title, txtName, txtStartingPrice, txtDescription, categoryBox, dynamicForm, btnSubmit);
    }
}
