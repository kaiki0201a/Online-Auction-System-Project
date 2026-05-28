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
        txtStartingPrice.setTextFormatter(new javafx.scene.control.TextFormatter<>(change -> {
            if (change.getControlNewText().matches("\\d*(\\.\\d*)?")) {
                return change;
            }
            return null;
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
                txtYear.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("\\d*") ? change : null));
                dynamicForm.getChildren().addAll(txtArtist, txtYear);
            } else if ("Electronics".equals(cat)) {
                TextField txtBrand = new TextField();
                txtBrand.setPromptText("Thương hiệu");
                TextField txtWarranty = new TextField();
                txtWarranty.setPromptText("Số tháng bảo hành (VD: 12)");
                txtWarranty.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("\\d*") ? change : null));
                dynamicForm.getChildren().addAll(txtBrand, txtWarranty);
            } else if ("Vehicle".equals(cat)) {
                TextField txtEngine = new TextField();
                txtEngine.setPromptText("Loại động cơ (VD: V8)");
                TextField txtMileage = new TextField();
                txtMileage.setPromptText("Số dặm đã đi (VD: 1000 hoặc 1000.5)");
                txtMileage.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("\\d*(\\.\\d*)?") ? change : null));
                dynamicForm.getChildren().addAll(txtEngine, txtMileage);
            }

            UIUtils.applyFadeIn(dynamicForm);

            Platform.runLater(() -> {
                if (rootPane.getScene() != null && rootPane.getScene().getWindow() != null) {
                    ((javafx.stage.Stage) rootPane.getScene().getWindow()).sizeToScene();
                }
            });
        });
    }

    @FXML
    public void handleSubmit() {
        String name     = txtName != null ? txtName.getText().trim() : "";
        String priceStr = txtStartingPrice != null ? txtStartingPrice.getText().trim() : "";
        String desc     = txtDescription != null ? txtDescription.getText().trim() : "";
        String category = categoryBox != null ? categoryBox.getValue() : null;

        // ── Validate ──────────────────────────────────────────────────────────
        if (name.isEmpty()) {
            NotificationUtil.showToast("Vui lòng nhập tên sản phẩm!", rootPane, "warning"); return;
        }
        if (priceStr.isEmpty()) {
            NotificationUtil.showToast("Vui lòng nhập giá khởi điểm!", rootPane, "warning"); return;
        }
        if (category == null) {
            NotificationUtil.showToast("Vui lòng chọn danh mục!", rootPane, "warning"); return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
            if (price <= 0) {
                NotificationUtil.showToast("Giá khởi điểm phải lớn hơn 0!", rootPane, "warning"); return;
            }
        } catch (NumberFormatException e) {
            NotificationUtil.showToast("Giá tiền không hợp lệ!", rootPane, "error"); return;
        }

        // ── Đọc các field động theo category ─────────────────────────────────
        List<String> dynamicValues = new ArrayList<>();
        for (Node node : dynamicForm.getChildren()) {
            if (node instanceof TextField tf) {
                dynamicValues.add(tf.getText().trim());
            }
        }

        // ── Build Item ────────────────────────────────────────────────────────
        Item item;
        try {
            item = switch (category) {
                case "Art" -> new Art(name, desc, price,
                        dynamicValues.size() > 0 ? dynamicValues.get(0) : "Unknown",
                        dynamicValues.size() > 1 && !dynamicValues.get(1).isEmpty()
                                ? Integer.parseInt(dynamicValues.get(1)) : 2000);
                case "Electronics" -> new Electronics(name, desc, price,
                        dynamicValues.size() > 0 ? dynamicValues.get(0) : "Unknown",
                        dynamicValues.size() > 1 && !dynamicValues.get(1).isEmpty()
                                ? Integer.parseInt(dynamicValues.get(1)) : 12);
                case "Vehicle" -> new Vehicle(name, desc, price,
                        dynamicValues.size() > 0 ? dynamicValues.get(0) : "Unknown",
                        dynamicValues.size() > 1 && !dynamicValues.get(1).isEmpty()
                                ? Double.parseDouble(dynamicValues.get(1)) : 0.0);
                default -> null;
            };
        } catch (NumberFormatException e) {
            NotificationUtil.showToast("Thông số danh mục không hợp lệ!", rootPane, "error"); return;
        }

        if (item == null) {
            NotificationUtil.showToast("Danh mục không hợp lệ!", rootPane, "error"); return;
        }

        // ── Tạo Auction và gửi lên server ─────────────────────────────────────
        Seller seller = (Seller) AppContext.getCurrentUser();
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime   = startTime.plusDays(3); // Mặc định 3 ngày nếu không chọn

        Auction auction = new Auction(item, seller, startTime, endTime);

        NetworkClient.getInstance().addEventListener("addProduct", response -> {
            Platform.runLater(() -> {
                NetworkClient.getInstance().removeEventListener("addProduct");
                if (response.getStatus() == com.auction.protocol.StatusType.SUCCESS) {
                    NotificationUtil.showToast("Đăng sản phẩm thành công! Chờ Admin duyệt.", rootPane, "success");
                    resetForm();
                    // Đóng popup sau 1.5 giây
                    new Thread(() -> {
                        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                        Platform.runLater(() -> {
                            if (rootPane.getScene() != null)
                                ((javafx.stage.Stage) rootPane.getScene().getWindow()).close();
                        });
                    }).start();
                } else {
                    NotificationUtil.showToast(response.getMessage(), rootPane, "error");
                }
            });
        });

        NetworkClient.getInstance().sendRequest(new Request(ActionType.CREATE_AUCTION, auction));
        NotificationUtil.showToast("Đang gửi sản phẩm lên server...", rootPane, "success");
    }

    private void resetForm() {
        txtName.clear(); txtStartingPrice.clear(); txtDescription.clear();
        categoryBox.getSelectionModel().clearSelection();
        dynamicForm.getChildren().clear();
        dynamicForm.setVisible(false); dynamicForm.setManaged(false);
    }

    public void onCategoryChange(ActionEvent event) {
    }
}
