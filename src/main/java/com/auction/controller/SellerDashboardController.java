package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.*;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.StatusType;
import com.auction.utils.AppContext;
import com.auction.utils.CurrencyFormatter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SellerDashboardController {

    @FXML private BorderPane rootPane;
    @FXML private Label lblUsername;
    @FXML private Label lblAvatarInitials;
    @FXML private Label lblHeaderBalance;

    // Kho hàng
    @FXML private HBox inventoryContainer;

    // Form tạo sản phẩm
    @FXML private TextField txtName;
    @FXML private TextField txtStartingPrice;
    @FXML private TextArea txtDescription;
    @FXML private ComboBox<String> comboCategory;
    @FXML private VBox dynamicFormContainer;

    // Ảnh sản phẩm
    @FXML private StackPane imagePreviewBox;
    @FXML private ImageView imgPreview;
    @FXML private Label lblImageHint;

    // Live auctions panel (Seller có thể xem nhưng không đấu giá)
    @FXML private HBox liveAuctionsList;

    private Seller currentUser;
    private String selectedImagePath = null;
    private List<Auction> allAuctions = new ArrayList<>();

    @FXML
    public void initialize() {
        currentUser = (Seller) AppContext.getCurrentUser();
        if (lblUsername != null) lblUsername.setText(currentUser.getUserName());
        if (lblAvatarInitials != null) lblAvatarInitials.setText(currentUser.getUserName().substring(0, 1).toUpperCase());
        updateBalance();

        if (comboCategory != null) {
            comboCategory.setItems(FXCollections.observableArrayList("Vehicle", "Electronics", "Art"));
            comboCategory.setOnAction(e -> buildDynamicForm());
        }

        // Validator số tiền
        if (txtStartingPrice != null) {
            txtStartingPrice.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("\\d*(\\.\\d*)?") ? change : null));
        }

        NetworkClient.getInstance().setOnResponseReceived(response -> {
            Platform.runLater(() -> {
                if (response.getStatus() == StatusType.SUCCESS) {
                    String msg = response.getMessage();

                    // Nhận danh sách Auction từ mọi nguồn
                    if (response.getData() instanceof java.util.List<?> dataList) {
                        if (!dataList.isEmpty() && dataList.get(0) instanceof Auction) {
                            allAuctions = (java.util.List<Auction>) dataList;
                            renderInventory();
                            renderLiveAuctions();
                        }
                    }

                    // Admin đã duyệt sản phẩm → thông báo + refresh
                    if ("AUCTION_APPROVED".equals(msg)) {
                        if (!(response.getData() instanceof java.util.List)) {
                            NetworkClient.getInstance().sendRequest(
                                new com.auction.protocol.Request(ActionType.GET_AUCTION_LIST, null));
                        }
                        // Hiển thị thông báo nếu sản phẩm là của Seller này
                        if (response.getData() instanceof Auction approved) {
                            if (approved.getSeller().getUserName().equals(currentUser.getUserName())) {
                                showAlert("✅ Sản phẩm được duyệt!",
                                    "Sản phẩm \"" + approved.getItem().getNameItem() +
                                    "\" đã được Admin phê duyệt và hiển thị trong các phiên đấu giá!");
                            }
                        }
                    }

                    // Admin từ chối sản phẩm → thông báo Seller
                    if ("AUCTION_REJECTED".equals(msg)) {
                        if (!(response.getData() instanceof java.util.List)) {
                            NetworkClient.getInstance().sendRequest(
                                new com.auction.protocol.Request(ActionType.GET_AUCTION_LIST, null));
                        }
                        if (response.getData() instanceof Auction rejected) {
                            if (rejected.getSeller().getUserName().equals(currentUser.getUserName())) {
                                showAlert("❌ Sản phẩm bị từ chối",
                                    "Sản phẩm \"" + rejected.getItem().getNameItem() +
                                    "\" đã bị Admin từ chối. Vui lòng kiểm tra lại thông tin sản phẩm.");
                            }
                        }
                    }

                    // Broadcast bidder đặt giá mới
                    if ("UPDATE_AUCTION".equals(msg)) {
                        if (!(response.getData() instanceof java.util.List)) {
                            NetworkClient.getInstance().sendRequest(
                                new com.auction.protocol.Request(ActionType.GET_AUCTION_LIST, null));
                        }
                    }

                    // Seller mới đăng sản phẩm (broadcast từ server)
                    if ("AUCTION_CREATED".equals(msg)) {
                        if (!(response.getData() instanceof java.util.List)) {
                            NetworkClient.getInstance().sendRequest(
                                new com.auction.protocol.Request(ActionType.GET_AUCTION_LIST, null));
                        }
                    }

                    // Cập nhật số dư
                    if (response.getData() instanceof Double) {
                        currentUser.setBalance((Double) response.getData());
                        updateBalance();
                    }
                }
            });
        });

        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
    }

    private void updateBalance() {
        if (lblHeaderBalance != null)
            lblHeaderBalance.setText(CurrencyFormatter.format(currentUser.getBalance()));
    }

    // ─── Kho hàng ────────────────────────────────────────────────────────────

    private void renderInventory() {
        if (inventoryContainer == null) return;
        inventoryContainer.getChildren().clear();

        List<Auction> mine = allAuctions.stream()
            .filter(a -> a.getSeller().getUserName().equals(currentUser.getUserName()))
            .collect(Collectors.toList());

        if (mine.isEmpty()) {
            Label empty = new Label("Chưa có sản phẩm nào. Hãy tạo phiên đấu giá đầu tiên!");
            empty.setStyle("-fx-text-fill: #555; -fx-font-size: 14px;");
            inventoryContainer.getChildren().add(empty);
            return;
        }

        for (Auction auction : mine) {
            VBox card = new VBox();
            card.getStyleClass().add("auction-card");
            card.setPrefWidth(270);

            StackPane imgBox = new StackPane();
            imgBox.getStyleClass().add("card-image-placeholder");
            imgBox.setPrefHeight(155);

            // Hiển thị ảnh nếu có
            String imgPath = auction.getItem().getImagePath();
            if (imgPath != null && !imgPath.isEmpty()) {
                try {
                    Image img = new Image("file:" + imgPath, 270, 155, true, true);
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(270); iv.setFitHeight(155); iv.setPreserveRatio(false);
                    imgBox.getChildren().add(iv);
                } catch (Exception ignored) {}
            } else {
                Label catIcon = new Label(auction.getItem().getClass().getSimpleName());
                catIcon.setStyle("-fx-text-fill: #333; -fx-font-size: 18px; -fx-font-weight: bold;");
                imgBox.getChildren().add(catIcon);
            }

            // Badge trạng thái
            Label badge = buildStatusBadge(auction);
            StackPane.setAlignment(badge, Pos.TOP_RIGHT);
            StackPane.setMargin(badge, new Insets(10));
            imgBox.getChildren().add(badge);

            VBox info = new VBox(8);
            info.setPadding(new Insets(14));
            Label lblTitle = new Label(auction.getItem().getNameItem());
            lblTitle.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
            lblTitle.setWrapText(true);

            HBox row = new HBox();
            row.setAlignment(Pos.CENTER_LEFT);
            VBox pCol = new VBox(2);
            String priceTitle = (auction.getStatus() == AuctionStatus.FINISHED || auction.getStatus() == AuctionStatus.PAID) ? "Giá Chốt" : "Giá Hiện Tại";
            Label lpt = new Label(priceTitle);
            lpt.setStyle("-fx-text-fill: #666; -fx-font-size: 9px;");
            Label lpv = new Label(CurrencyFormatter.format(auction.getCurrentHighestBid()));
            lpv.setStyle("-fx-text-fill: #F5C518; -fx-font-weight: bold; -fx-font-size: 15px;");
            pCol.getChildren().addAll(lpt, lpv);
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            Button btnView = new Button("Chi tiết →");
            btnView.getStyleClass().add("btn-outline");
            btnView.setOnAction(e -> openAuctionDetail(auction));
            row.getChildren().addAll(pCol, sp, btnView);
            info.getChildren().addAll(lblTitle, row);
            card.getChildren().addAll(imgBox, info);
            card.setOnMouseClicked(e -> openAuctionDetail(auction));
            inventoryContainer.getChildren().add(card);
        }
    }

    // ─── Live Auctions (Seller xem nhưng không đặt giá) ─────────────────────

    private void renderLiveAuctions() {
        if (liveAuctionsList == null) return;
        liveAuctionsList.getChildren().clear();

        List<Auction> live = allAuctions.stream()
            .filter(a -> a.getStatus() == AuctionStatus.RUNNING || a.getStatus() == AuctionStatus.OPEN)
            .limit(4)
            .collect(Collectors.toList());

        if (live.isEmpty()) {
            Label empty = new Label("Hiện không có phiên đấu giá nào đang diễn ra.");
            empty.setStyle("-fx-text-fill: #555; -fx-font-size: 13px;");
            liveAuctionsList.getChildren().add(empty);
            return;
        }

        for (Auction a : live) {
            VBox card = new VBox(8);
            card.setPrefWidth(240);
            card.setStyle("-fx-background-color: #1A1A1A; -fx-border-color: #2A2A2A; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 14;");

            Label catBadge = new Label(a.getItem().getClass().getSimpleName().toUpperCase());
            catBadge.setStyle("-fx-background-color: rgba(245,197,24,0.15); -fx-text-fill: #F5C518; -fx-font-size: 10px; -fx-padding: 2 7; -fx-background-radius: 3; -fx-font-weight: bold;");

            Label title = new Label(a.getItem().getNameItem());
            title.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");
            title.setWrapText(true);

            Label price = new Label(CurrencyFormatter.format(a.getCurrentHighestBid()));
            price.setStyle("-fx-text-fill: #F5C518; -fx-font-size: 16px; -fx-font-weight: bold;");

            boolean isOwn = a.getSeller().getUserName().equals(currentUser.getUserName());
            Label ownerTag = new Label(isOwn ? "★ Sản phẩm của bạn" : "Người bán: " + a.getSeller().getUserName());
            ownerTag.setStyle("-fx-text-fill: " + (isOwn ? "#F5C518" : "#666") + "; -fx-font-size: 10px;");

            Button btnView = new Button(isOwn ? "Xem phiên của tôi" : "Xem →");
            btnView.setStyle("-fx-background-color: " + (isOwn ? "#2A2A2A" : "transparent") + "; -fx-text-fill: " +
                (isOwn ? "#F5C518" : "#A0A0A0") + "; -fx-border-color: " + (isOwn ? "#F5C518" : "#444") + "; -fx-border-radius: 4; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
            btnView.setMaxWidth(Double.MAX_VALUE);
            btnView.setOnAction(e -> openAuctionDetail(a));

            card.getChildren().addAll(catBadge, title, price, ownerTag, btnView);
            liveAuctionsList.getChildren().add(card);
        }
    }

    private Label buildStatusBadge(Auction a) {
        Label badge = new Label();
        String base = "-fx-padding: 3 8; -fx-background-radius: 4; -fx-font-size: 10px; -fx-font-weight: bold;";
        switch (a.getStatus()) {
            case PENDING_APPROVAL -> {
                badge.setText("⏳ Chờ Duyệt");
                badge.setStyle(base + "-fx-background-color: rgba(230,126,34,0.25); -fx-text-fill: #e67e22;");
            }
            case RUNNING, OPEN -> {
                badge.setText("🔴 Đang Đấu");
                badge.setStyle(base + "-fx-background-color: rgba(245,197,24,0.2); -fx-text-fill: #F5C518;");
            }
            case FINISHED, PAID -> {
                badge.setText("✅ Đã Bán");
                badge.setStyle(base + "-fx-background-color: rgba(39,174,96,0.2); -fx-text-fill: #27ae60;");
            }
            case CANCELED -> {
                badge.setText("❌ Hủy");
                badge.setStyle(base + "-fx-background-color: rgba(231,76,60,0.2); -fx-text-fill: #e74c3c;");
            }
        }
        return badge;
    }

    // ─── Upload ảnh sản phẩm ─────────────────────────────────────────────────

    @FXML
    public void onChooseImageClick(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh sản phẩm");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp")
        );

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            selectedImagePath = selectedFile.getAbsolutePath();
            if (imgPreview != null) {
                try {
                    Image img = new Image("file:" + selectedImagePath, 300, 200, true, true);
                    imgPreview.setImage(img);
                    imgPreview.setVisible(true);
                    if (lblImageHint != null) lblImageHint.setVisible(false);
                } catch (Exception ignored) {}
            }
        }
    }

    @FXML
    public void onRemoveImageClick(ActionEvent event) {
        selectedImagePath = null;
        if (imgPreview != null) { imgPreview.setImage(null); imgPreview.setVisible(false); }
        if (lblImageHint != null) lblImageHint.setVisible(true);
    }

    // ─── Form tạo sản phẩm ────────────────────────────────────────────────────

    private void buildDynamicForm() {
        if (dynamicFormContainer == null || comboCategory == null) return;
        dynamicFormContainer.getChildren().clear();
        String cat = comboCategory.getValue();
        if (cat == null) return;

        Label lblSection = new Label("Thông Số " + cat);
        lblSection.setStyle("-fx-text-fill: #E6B969; -fx-font-size: 14px; -fx-font-weight: bold;");
        dynamicFormContainer.getChildren().add(lblSection);

        HBox row = new HBox(20);
        if ("Art".equals(cat)) {
            row.getChildren().addAll(
                createInputField("TÊN HỌA SĨ / NGHỆ NHÂN", "VD: Pablo Picasso"),
                createInputField("NĂM SÁNG TÁC", "VD: 1937"));
        } else if ("Electronics".equals(cat)) {
            row.getChildren().addAll(
                createInputField("THƯƠNG HIỆU", "VD: Apple, Samsung"),
                createInputField("BẢO HÀNH (THÁNG)", "VD: 24"));
        } else if ("Vehicle".equals(cat)) {
            row.getChildren().addAll(
                createInputField("LOẠI ĐỘNG CƠ", "VD: 4.0L V8"),
                createInputField("SỐ KM (MILEAGE)", "VD: 12000"));
        }
        dynamicFormContainer.getChildren().add(row);
    }

    private VBox createInputField(String title, String prompt) {
        VBox box = new VBox(7);
        HBox.setHgrow(box, Priority.ALWAYS);
        Label lbl = new Label(title);
        lbl.setStyle("-fx-text-fill: #A0A0A0; -fx-font-size: 10px; -fx-font-weight: bold;");
        TextField txt = new TextField();
        txt.setPromptText(prompt);
        txt.getStyleClass().add("form-input");
        box.getChildren().addAll(lbl, txt);
        return box;
    }

    @FXML
    public void onPublishClick(ActionEvent event) {
        String name     = txtName          != null ? txtName.getText().trim()          : "";
        String priceStr = txtStartingPrice != null ? txtStartingPrice.getText().trim() : "";
        String desc     = txtDescription   != null ? txtDescription.getText().trim()   : "";
        String category = comboCategory    != null ? comboCategory.getValue()           : null;

        if (name.isEmpty() || priceStr.isEmpty() || category == null) {
            showAlert("Thiếu thông tin", "Vui lòng nhập Tên sản phẩm, Giá khởi điểm và chọn Danh mục.");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
            if (price <= 0) { showAlert("Lỗi", "Giá khởi điểm phải lớn hơn 0."); return; }
        } catch (NumberFormatException ex) {
            showAlert("Lỗi", "Giá tiền không hợp lệ."); return;
        }

        List<String> dynamics = extractDynamicInputs();
        Item newItem = buildItem(category, name, desc, price, dynamics);
        if (newItem == null) { showAlert("Lỗi", "Danh mục không hợp lệ."); return; }

        // Gắn ảnh nếu đã chọn
        if (selectedImagePath != null) newItem.setImagePath(selectedImagePath);

        Auction newAuction = new Auction(newItem, currentUser,
                LocalDateTime.now(), LocalDateTime.now().plusDays(3));

        // Gửi request và lắng nghe response để tự refresh
        // Lưu listener cũ và đặt listener tạm (chỉ dùng 1 lần)
        NetworkClient.getInstance().setOnResponseReceived(response -> {
            Platform.runLater(() -> {
                if (response.getStatus() == StatusType.SUCCESS &&
                        ("AUCTION_CREATED".equals(response.getMessage()) ||
                         "Đăng sản phẩm thành công!".equals(response.getMessage()))) {
                    // Hiển thị thông báo thành công
                    showAlert("✅ Đăng thành công!", "Sản phẩm \"" + name + "\" đã được đăng bán!\n" +
                              "Kiểm tra kho hàng bên trên.");
                    onClearFormClick(null);
                }

                // Sau đó tiếp tục xử lý bình thường (danh sách, balance...)
                if (response.getData() instanceof java.util.List<?> dataList) {
                    if (!dataList.isEmpty() && dataList.get(0) instanceof Auction) {
                        allAuctions = (List<Auction>) dataList;
                        renderInventory();
                        renderLiveAuctions();
                    }
                }
                if (response.getData() instanceof Double) {
                    currentUser.setBalance((Double) response.getData());
                    updateBalance();
                }
                // Khôi phục listener chính sau khi xử lý xong
                restoreMainListener();
            });
        });

        NetworkClient.getInstance().sendRequest(new Request(ActionType.CREATE_AUCTION, newAuction));
    }

    /** Khôi phục listener chính của Seller Dashboard */
    private void restoreMainListener() {
        NetworkClient.getInstance().setOnResponseReceived(response -> {
            Platform.runLater(() -> {
                if (response.getStatus() == StatusType.SUCCESS) {
                    if (response.getData() instanceof java.util.List<?> dataList) {
                        if (!dataList.isEmpty() && dataList.get(0) instanceof Auction) {
                            allAuctions = (List<Auction>) dataList;
                            renderInventory();
                            renderLiveAuctions();
                        }
                    }
                    if ("AUCTION_CREATED".equals(response.getMessage()) ||
                            "UPDATE_AUCTION".equals(response.getMessage())) {
                        if (!(response.getData() instanceof java.util.List)) {
                            NetworkClient.getInstance().sendRequest(
                                new Request(ActionType.GET_AUCTION_LIST, null));
                        }
                    }
                    if (response.getData() instanceof Double) {
                        currentUser.setBalance((Double) response.getData());
                        updateBalance();
                    }
                }
            });
        });
    }

    private List<String> extractDynamicInputs() {
        List<String> inputs = new ArrayList<>();
        if (dynamicFormContainer == null || dynamicFormContainer.getChildren().size() < 2) return inputs;
        Node rowNode = dynamicFormContainer.getChildren().get(1);
        if (rowNode instanceof HBox hbox) {
            for (Node n : hbox.getChildren()) {
                if (n instanceof VBox vb && vb.getChildren().size() > 1) {
                    Node input = vb.getChildren().get(1);
                    if (input instanceof TextField tf) inputs.add(tf.getText().trim());
                }
            }
        }
        return inputs;
    }

    private Item buildItem(String cat, String name, String desc, double price, List<String> d) {
        try {
            return switch (cat) {
                case "Art" -> new Art(name, desc, price,
                    d.size() > 0 ? d.get(0) : "Unknown",
                    d.size() > 1 ? Integer.parseInt(d.get(1).isEmpty() ? "2000" : d.get(1)) : 2000);
                case "Electronics" -> new Electronics(name, desc, price,
                    d.size() > 0 ? d.get(0) : "Unknown",
                    d.size() > 1 ? Integer.parseInt(d.get(1).isEmpty() ? "12" : d.get(1)) : 12);
                case "Vehicle" -> new Vehicle(name, desc, price,
                    d.size() > 0 ? d.get(0) : "Unknown",
                    d.size() > 1 ? Double.parseDouble(d.get(1).isEmpty() ? "0" : d.get(1)) : 0);
                default -> null;
            };
        } catch (Exception e) { return null; }
    }

    @FXML
    public void onClearFormClick(ActionEvent event) {
        if (txtName != null) txtName.clear();
        if (txtStartingPrice != null) txtStartingPrice.clear();
        if (txtDescription != null) txtDescription.clear();
        if (comboCategory != null) comboCategory.getSelectionModel().clearSelection();
        if (dynamicFormContainer != null) dynamicFormContainer.getChildren().clear();
        selectedImagePath = null;
        if (imgPreview != null) { imgPreview.setImage(null); imgPreview.setVisible(false); }
        if (lblImageHint != null) lblImageHint.setVisible(true);
    }

    // ─── Điều hướng ──────────────────────────────────────────────────────────

    @FXML public void onViewAllAuctionsClick(ActionEvent event) {
        try {
            NetworkClient.getInstance().removeOnResponseReceived();
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/AuctionList.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 800));
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML public void onSettingsClick(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Settings.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 600, 530));
        } catch (IOException e) { showAlert("Lỗi", "Không thể mở Cài đặt."); }
    }

    @FXML public void onDepositClick(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/DepositWithdraw.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 500, 430));
        } catch (IOException e) { showAlert("Lỗi", "Không thể mở Nạp/Rút tiền."); }
    }

    @FXML public void onProfileClick(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/UserProfile.fxml"));
            Parent root = loader.load();
            UserProfileController ctrl = loader.getController();
            ctrl.setUserData(currentUser);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 860, 670));
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML public void onRequestAppraisalClick(ActionEvent event) {
        showAlert("Định Giá", "Tính năng định giá online đang được phát triển.");
    }

    @FXML public void onLogoutClick(ActionEvent event) {
        NetworkClient.getInstance().removeOnResponseReceived();
        AppContext.logout();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 900, 600));
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void openAuctionDetail(Auction auction) {
        try {
            NetworkClient.getInstance().removeOnResponseReceived();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/AuctionDetail.fxml"));
            Parent root = loader.load();
            AuctionDetailController ctrl = loader.getController();
            ctrl.setAuctionData(auction);
            Stage stage = (Stage) rootPane.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (IOException e) { showAlert("Lỗi", "Không thể mở chi tiết phiên."); }
    }

    private void showAlert(String title, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(content);
        a.showAndWait();
    }
}