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
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;

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

        categoryBox.setOnAction(e -> {
            dynamicForm.getChildren().clear();
            dynamicForm.setVisible(true);
            dynamicForm.setManaged(true);

            String cat = categoryBox.getValue();

            // ĐÃ SỬA: Vẽ đúng số lượng tham số khớp với Model
            if ("Art".equals(cat)) {
                TextField txtArtist = new TextField();
                txtArtist.setPromptText("Tên họa sĩ / Nghệ nhân");
                TextField txtYear = new TextField();
                txtYear.setPromptText("Năm sáng tác (VD: 1990)");
                Label lbl = new Label("Thông tin Art:");
                lbl.setStyle("-fx-font-weight: bold;");
                dynamicForm.getChildren().addAll(lbl, txtArtist, txtYear);

            } else if ("Electronics".equals(cat)) {
                TextField txtBrand = new TextField();
                txtBrand.setPromptText("Thương hiệu");
                TextField txtWarranty = new TextField();
                txtWarranty.setPromptText("Số tháng bảo hành (VD: 12)");
                Label lbl = new Label("Thông tin Electronics:");
                lbl.setStyle("-fx-font-weight: bold;");
                dynamicForm.getChildren().addAll(lbl, txtBrand, txtWarranty);

            } else if ("Vehicle".equals(cat)) {
                TextField txtEngine = new TextField();
                txtEngine.setPromptText("Loại động cơ (VD: V8, Xăng)");
                TextField txtMileage = new TextField();
                txtMileage.setPromptText("Số dặm đã đi (VD: 1000)");
                Label lbl = new Label("Thông tin Vehicle:");
                lbl.setStyle("-fx-font-weight: bold;");
                dynamicForm.getChildren().addAll(lbl, txtEngine, txtMileage);
            }

            UIUtils.applyFadeIn(dynamicForm);
        });
    }

    @FXML
    public void handleSubmit() {
        String productName = txtName.getText().trim();
        String priceStr = txtStartingPrice.getText().trim();
        String description = txtDescription.getText().trim();
        String category = categoryBox.getValue();

        // 1. VALIDATE CƠ BẢN
        if (productName.isEmpty() || priceStr.isEmpty() || category == null) {
            NotificationUtil.showToast("Vui lòng điền đủ thông tin!", rootPane, "warning");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
        } catch (NumberFormatException ex) {
            NotificationUtil.showToast("Giá khởi điểm phải là số!", rootPane, "error");
            return;
        }

        // 2. MÓC DỮ LIỆU TỪ FORM ĐỘNG VÀ TẠO ĐỐI TƯỢNG ITEM (Đã sửa code trích xuất dữ liệu)
        Item newItem = null;
        try {
            if ("Art".equals(category)) {
                String artist = ((TextField) dynamicForm.getChildren().get(1)).getText();
                int year = Integer.parseInt(((TextField) dynamicForm.getChildren().get(2)).getText());
                newItem = new Art(productName, description, price, artist, year);

            } else if ("Electronics".equals(category)) {
                String brand = ((TextField) dynamicForm.getChildren().get(1)).getText();
                int warranty = Integer.parseInt(((TextField) dynamicForm.getChildren().get(2)).getText());
                newItem = new Electronics(productName, description, price, brand, warranty);

            } else if ("Vehicle".equals(category)) {
                String engine = ((TextField) dynamicForm.getChildren().get(1)).getText();
                double mileage = Double.parseDouble(((TextField) dynamicForm.getChildren().get(2)).getText());
                newItem = new Vehicle(productName, description, price, engine, mileage);
            }
        } catch (Exception ex) {
            NotificationUtil.showToast("Vui lòng nhập đúng định dạng số (Năm / Số KM / Tháng bảo hành)!", rootPane, "warning");
            return;
        }

        // 3. TẠO PHIÊN ĐẤU GIÁ (AUCTION)
        User currentUser = AppContext.getCurrentUser();
        if (!(currentUser instanceof Seller)) {
            NotificationUtil.showToast("Lỗi quyền: Chỉ Seller mới được đăng sản phẩm!", rootPane, "error");
            return;
        }

        Auction newAuction = new Auction(
                newItem,
                (Seller) currentUser,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(3)
        );

        // 4. GỌI MẠNG ĐỂ ĐẨY LÊN SERVER
        UIUtils.showLoadingSpinner(rootPane, () -> {
            new Thread(() -> {
                try {
                    Request request = new Request(ActionType.CREATE_AUCTION, newAuction);
                    NetworkClient.getInstance().sendRequest(request);

                    Platform.runLater(() -> {
                        NotificationUtil.showToast("Gửi yêu cầu đăng sản phẩm thành công!", rootPane, "success");
                        resetForm();
                    });

                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        NotificationUtil.showToast("Lỗi kết nối Server!", rootPane, "error");
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