package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.*;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.utils.AppContext;
import com.auction.utils.NotificationUtil;
import com.auction.utils.UIUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AddProductController {

    @FXML private StackPane rootPane;
    @FXML private TextField txtName, txtStartingPrice;
    @FXML private TextArea txtDescription;
    @FXML private ComboBox<String> categoryBox;
    @FXML private VBox dynamicForm;

    @FXML
    public void initialize() {
        // ---> BỔ SUNG: Chặn nhập chữ vào ô Giá khởi điểm <---
        txtStartingPrice.setTextFormatter(new javafx.scene.control.TextFormatter<>(change -> {
            if (change.getControlNewText().matches("\\d*(\\.\\d*)?")) {
                return change; // Hợp lệ (số và dấu chấm) -> cho phép hiển thị
            }
            return null; // Là chữ cái -> chặn ngay
        }));
        categoryBox.setItems(FXCollections.observableArrayList("Art", "Electronics", "Vehicle"));

        categoryBox.setOnAction(e -> {
            dynamicForm.getChildren().clear();
            dynamicForm.setVisible(true);
            dynamicForm.setManaged(true);

            String cat = categoryBox.getValue();
            Label lbl = new Label("Thông tin " + cat + ":");
            lbl.setStyle("-fx-font-weight: bold;");
            dynamicForm.getChildren().add(lbl);

            if ("Art".equals(cat)) {
                TextField txtArtist = new TextField();
                txtArtist.setPromptText("Tên họa sĩ / Nghệ nhân");

                TextField txtYear = new TextField();
                txtYear.setPromptText("Năm sáng tác (VD: 1990)");
                // Chặn nhập chữ (Chỉ cho phép số nguyên)
                txtYear.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("\\d*") ? change : null));

                dynamicForm.getChildren().addAll(txtArtist, txtYear);

            } else if ("Electronics".equals(cat)) {
                TextField txtBrand = new TextField();
                txtBrand.setPromptText("Thương hiệu");

                TextField txtWarranty = new TextField();
                txtWarranty.setPromptText("Số tháng bảo hành (VD: 12)");
                // Chặn nhập chữ (Chỉ cho phép số nguyên vì dùng Integer.parseInt)
                txtWarranty.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("\\d*") ? change : null));

                dynamicForm.getChildren().addAll(txtBrand, txtWarranty);

            } else if ("Vehicle".equals(cat)) {
                TextField txtEngine = new TextField();
                txtEngine.setPromptText("Loại động cơ (VD: V8)");

                TextField txtMileage = new TextField();
                txtMileage.setPromptText("Số dặm đã đi (VD: 1000 hoặc 1000.5)");
                // Chặn nhập chữ (Cho phép số thập phân vì dùng Double.parseDouble)
                txtMileage.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("\\d*(\\.\\d*)?") ? change : null));

                dynamicForm.getChildren().addAll(txtEngine, txtMileage);
            }

            UIUtils.applyFadeIn(dynamicForm);

            // Giãn cửa sổ tự động
            Platform.runLater(() -> {
                if (rootPane.getScene() != null && rootPane.getScene().getWindow() != null) {
                    ((javafx.stage.Stage) rootPane.getScene().getWindow()).sizeToScene();
                }
            });
        });
    }

    @FXML
    public void handleSubmit() {

    }

    private void resetForm() {
        txtName.clear(); txtStartingPrice.clear(); txtDescription.clear();
        categoryBox.getSelectionModel().clearSelection();
        dynamicForm.setVisible(false); dynamicForm.setManaged(false);
    }

    public void onCategoryChange(ActionEvent event) {
    }
}