package com.auction.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class AddProductController {

    @FXML private StackPane rootPane;
    @FXML private TextField txtName;
    @FXML private TextField txtStartingPrice;
    @FXML private TextArea txtDescription;
    @FXML private ComboBox<String> categoryBox;
    @FXML private VBox dynamicForm;

    @FXML
    public void initialize() {
        categoryBox.setItems(FXCollections.observableArrayList("Art", "Electronics", "Vehicle"));

        // 1. PHỤC HỒI LOGIC RENDER FORM ĐỘNG (Đã xóa CSS cứng)
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
                lbl.setStyle("-fx-font-weight: bold;"); // Có thể chuyển cái này vào styles.css luôn nếu muốn
                dynamicForm.getChildren().addAll(lbl, txtArtist, txtYear);

            } else if ("Electronics".equals(cat)) {
                TextField txtBrand = new TextField();
                txtBrand.setPromptText("Thương hiệu");
                TextField txtModel = new TextField();
                txtModel.setPromptText("Model");
                TextField txtCondition = new TextField();
                txtCondition.setPromptText("Tình trạng (Mới/Cũ)");
                Label lbl = new Label("Thông tin Electronics:");
                lbl.setStyle("-fx-font-weight: bold;");
                dynamicForm.getChildren().addAll(lbl, txtBrand, txtModel, txtCondition);

            } else if ("Vehicle".equals(cat)) {
                TextField txtMake = new TextField();
                txtMake.setPromptText("Hãng sản xuất");
                TextField txtMileage = new TextField();
                txtMileage.setPromptText("Số KM đã đi");
                TextField txtEngine = new TextField();
                txtEngine.setPromptText("Loại động cơ");
                Label lbl = new Label("Thông tin Vehicle:");
                lbl.setStyle("-fx-font-weight: bold;");
                dynamicForm.getChildren().addAll(lbl, txtMake, txtMileage, txtEngine);
            }

            com.auction.utils.UIUtils.applyFadeIn(dynamicForm);
        });
    }

    @FXML
    public void handleSubmit() {
        String productName = txtName.getText();
        String price = txtStartingPrice.getText();
        String category = categoryBox.getValue();

        // (Tùy chọn) Validate nếu người dùng bỏ trống
        if (productName.isEmpty() || price.isEmpty() || category == null) {
            com.auction.utils.NotificationUtil.showToast("Vui lòng điền đủ thông tin!", rootPane, "warning");
            return;
        }

        // Hiện Spinner (Giả sử UIUtils đã được bạn fix để hiện Popup mượt mà như Toast)
        com.auction.utils.UIUtils.showLoadingSpinner(rootPane, () -> {

            // 2. CHẠY TASK GỌI MẠNG TRONG THREAD RIÊNG ĐỂ KHÔNG LÀM ĐƠ GIAO DIỆN
            new Thread(() -> {
                try {
                    // =================================================================
                    // TODO: ĐOẠN NÀY LÀ NƠI GỌI NETWORK CLIENT THẬT
                    // Ví dụ: NetworkClient.post("/api/products", ...);
                    // Giả lập thời gian chờ Server phản hồi:
                    Thread.sleep(1500);
                    // =================================================================

                    // 3. KHI CÓ KẾT QUẢ TỪ SERVER, PHẢI ĐẨY LẠI VÀO LUỒNG JAVAFX (Platform.runLater) ĐỂ CẬP NHẬT GIAO DIỆN
                    Platform.runLater(() -> {
                        com.auction.utils.NotificationUtil.showToast("Đã đăng sản phẩm thành công!", rootPane, "success");
                        resetForm();
                    });

                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        com.auction.utils.NotificationUtil.showToast("Lỗi kết nối Server!", rootPane, "error");
                    });
                }
            }).start();

        });
    }

    private void resetForm() {
        txtName.clear();
        txtStartingPrice.clear();
        txtDescription.clear();
        categoryBox.getSelectionModel().clearSelection();
        dynamicForm.setVisible(false);
        dynamicForm.setManaged(false);
    }
}