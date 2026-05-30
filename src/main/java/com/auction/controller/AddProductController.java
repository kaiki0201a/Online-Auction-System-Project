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
import javafx.stage.FileChooser;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AddProductController {

    @FXML private StackPane rootPane;
    @FXML private TextField txtName;
    // FIX C-02: tên field đổi cho khớp fx:id="txtPrice" trong AddProduct.fxml
    @FXML private TextField txtPrice;
    @FXML private TextArea txtDescription;
    // FIX C-02: tên field đổi cho khớp fx:id="comboCategory" trong AddProduct.fxml
    @FXML private ComboBox<String> comboCategory;
    // FIX C-02: tên field đổi cho khớp fx:id="dynamicSpecContainer" trong AddProduct.fxml
    @FXML private VBox dynamicSpecContainer;
    // FIX C-02: thêm 2 field mới tồn tại trong FXML nhưng chưa được inject
    @FXML private DatePicker dpStartDate;
    @FXML private TextField txtStartTime;

    @FXML
    public void initialize() {
        // FIX C-02: use txtPrice (matched fx:id) instead of removed txtStartingPrice
        txtPrice.setTextFormatter(new javafx.scene.control.TextFormatter<>(change -> {
            if (change.getControlNewText().matches("\\d*(\\.\\d*)?")) {
                return change;
            }
            return null;
        }));
        // FIX C-02: use comboCategory (matched fx:id) instead of removed categoryBox
        comboCategory.setItems(FXCollections.observableArrayList("Art", "Electronics", "Vehicle"));

        // onCategoryChange is already wired via onAction="#onCategoryChange" in FXML;
        // the listener below drives dynamic form — keep it here and delegate from onCategoryChange()
        comboCategory.setOnAction(e -> {
            // FIX C-02: use dynamicSpecContainer (matched fx:id) instead of removed dynamicForm
            dynamicSpecContainer.getChildren().clear();
            dynamicSpecContainer.setVisible(true);
            dynamicSpecContainer.setManaged(true);

            String cat = comboCategory.getValue();
            Label lbl = new Label("Thông tin " + cat + ":");
            lbl.setStyle("-fx-font-weight: bold;");
            dynamicSpecContainer.getChildren().add(lbl);

            if ("Art".equals(cat)) {
                TextField txtArtist = new TextField();
                txtArtist.setPromptText("Tên họa sĩ / Nghệ nhân");
                TextField txtYear = new TextField();
                txtYear.setPromptText("Năm sáng tác (VD: 1990)");
                txtYear.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("\\d*") ? change : null));
                dynamicSpecContainer.getChildren().addAll(txtArtist, txtYear);
            } else if ("Electronics".equals(cat)) {
                TextField txtBrand = new TextField();
                txtBrand.setPromptText("Thương hiệu");
                TextField txtWarranty = new TextField();
                txtWarranty.setPromptText("Số tháng bảo hành (VD: 12)");
                txtWarranty.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("\\d*") ? change : null));
                dynamicSpecContainer.getChildren().addAll(txtBrand, txtWarranty);
            } else if ("Vehicle".equals(cat)) {
                TextField txtEngine = new TextField();
                txtEngine.setPromptText("Loại động cơ (VD: V8)");
                TextField txtMileage = new TextField();
                txtMileage.setPromptText("Số dặm đã đi (VD: 1000 hoặc 1000.5)");
                txtMileage.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("\\d*(\\.\\d*)?") ? change : null));
                dynamicSpecContainer.getChildren().addAll(txtEngine, txtMileage);
            }

            UIUtils.applyFadeIn(dynamicSpecContainer);

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
        String priceStr = txtPrice != null ? txtPrice.getText().trim() : "";
        String desc     = txtDescription != null ? txtDescription.getText().trim() : "";
        String category = comboCategory != null ? comboCategory.getValue() : null;

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
        for (Node node : dynamicSpecContainer.getChildren()) {
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
        txtName.clear(); txtPrice.clear(); txtDescription.clear();
        comboCategory.getSelectionModel().clearSelection();
        dynamicSpecContainer.getChildren().clear();
        dynamicSpecContainer.setVisible(false); dynamicSpecContainer.setManaged(false);
    }

    public void onCategoryChange(ActionEvent event) {
        // Delegate to the comboCategory.setOnAction listener set up in initialize()
        // No additional logic needed here — the setOnAction handler handles it.
    }

    /** FIX M-01: handler cho nút "Duyệt File" — browse image (standalone AddProduct form) */
    @FXML
    public void onBrowseImageClick(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn hình ảnh sản phẩm");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.jpeg", "*.png")
        );
        java.io.File file = chooser.showOpenDialog(
                rootPane != null ? rootPane.getScene().getWindow() : null
        );
        if (file != null) {
            NotificationUtil.showToast("Đã chọn: " + file.getName(), rootPane, "success");
        }
    }

    /** FIX M-01: handler cho nút "Lưu Bản Nháp" — clear form */
    @FXML
    public void onSaveDraftClick(ActionEvent event) {
        // Hiện tại chỉ reset form — có thể mở rộng lưu nháp sau
        resetForm();
        NotificationUtil.showToast("Đã xóa form. Bản nháp chưa được hỗ trợ.", rootPane, "warning");
    }
}
