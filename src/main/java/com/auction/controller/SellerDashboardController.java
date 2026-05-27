package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.*;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.StatusType;
import com.auction.utils.AppContext;
import com.auction.utils.CurrencyFormatter;
import com.auction.utils.NotificationUtil;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SellerDashboardController — FIX hoàn chỉnh.
 *
 * FIXES THỰC HIỆN:
 * 1. Dùng addEventListener("seller", ...) thay vì setOnResponseReceived
 *    → không còn bị ghi đè bởi màn hình khác
 * 2. onPublishClick: bỏ pattern "listener tạm + restoreMainListener" phức tạp.
 *    Gửi CREATE_AUCTION, chờ broadcast AUCTION_CREATED với data=List → renderInventory ngay
 * 3. buildStatusBadge: thêm case APPROVED, REJECTED
 * 4. renderInventory: filter bằng seller.getUserName() (robust)
 * 5. Xử lý broadcast AUCTION_WENT_LIVE, AUCTION_ENDED
 * 6. onLogout: removeEventListener("seller") thay vì removeOnResponseReceived
 */
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

    // Live auctions panel
    @FXML private HBox liveAuctionsList;

    // Lịch sử thanh toán (Feature 3)
    @FXML private TableView<AuctionEarning> tablePaymentHistory;
    @FXML private TableColumn<AuctionEarning, String>  colPayItem;
    @FXML private TableColumn<AuctionEarning, String>  colPayDate;
    @FXML private TableColumn<AuctionEarning, String>  colPayAmount;
    @FXML private TableColumn<AuctionEarning, String>  colPayStatus;
    @FXML private Label lblTotalEarned;

    private Seller currentUser;
    private String selectedImagePath = null;
    private List<Auction> allAuctions = new ArrayList<>();

    // FIX: Key riêng cho seller listener
    private static final String LISTENER_KEY = "seller";

    @FXML
    public void initialize() {
        currentUser = (Seller) AppContext.getCurrentUser();
        if (lblUsername != null) lblUsername.setText(currentUser.getUserName());
        if (lblAvatarInitials != null)
            lblAvatarInitials.setText(currentUser.getUserName().substring(0, 1).toUpperCase());
        updateBalance();

        if (comboCategory != null) {
            comboCategory.setItems(FXCollections.observableArrayList("Vehicle", "Electronics", "Art"));
            comboCategory.setOnAction(e -> buildDynamicForm());
        }

        if (txtStartingPrice != null) {
            txtStartingPrice.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("\\d*(\\.\\d*)?") ? change : null));
        }

        // FIX: Dùng addEventListener với key "seller" — không ghi đè listener khác
        NetworkClient.getInstance().addEventListener(LISTENER_KEY, response -> {
            Platform.runLater(() -> handleResponse(response));
        });

        // Khởi tạo bảng lịch sử thanh toán
        setupPaymentTable();
        loadPaymentHistory();

        // Tải danh sách auction ban đầu
        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
    }

    // ─── XỬ LÝ RESPONSE ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleResponse(com.auction.protocol.Response response) {
        if (response.getStatus() != StatusType.SUCCESS) return;

        Object data = response.getData();
        String msg  = response.getMessage();

        // FIX: Nhận bất kỳ response nào có data là List<Auction> → cập nhật ngay
        if (data instanceof List<?> dataList && !dataList.isEmpty()
                && dataList.get(0) instanceof Auction) {
            allAuctions = (List<Auction>) dataList;
            renderInventory();
            renderLiveAuctions();
        }

        // Admin duyệt sản phẩm — thông báo nếu là sản phẩm của seller này
        if ("AUCTION_APPROVED".equals(msg) || msg != null && msg.startsWith("DUYỆT_OK|")) {
            if (data instanceof List<?> lst && !lst.isEmpty() && lst.get(0) instanceof Auction) {
                // Tìm auction vừa được duyệt trong list
                ((List<Auction>) data).stream()
                    .filter(a -> a.getSeller().getUserName().equals(currentUser.getUserName()))
                    .filter(a -> a.getStatus() == AuctionStatus.RUNNING || a.getStatus() == AuctionStatus.APPROVED)
                    .findFirst()
                    .ifPresent(approved -> showAlert("✅ Sản phẩm được duyệt!",
                        "Sản phẩm \"" + approved.getItem().getNameItem() +
                        "\" đã được Admin phê duyệt!"));
            }
        }

        // Admin từ chối — thông báo seller
        if ("AUCTION_REJECTED".equals(msg) || msg != null && msg.startsWith("TỪ_CHỐI_OK|")) {
            if (data instanceof List<?> lst && !lst.isEmpty() && lst.get(0) instanceof Auction) {
                ((List<Auction>) data).stream()
                    .filter(a -> a.getSeller().getUserName().equals(currentUser.getUserName()))
                    .filter(a -> a.getStatus() == AuctionStatus.REJECTED)
                    .findFirst()
                    .ifPresent(rejected -> showAlert("❌ Sản phẩm bị từ chối",
                        "Sản phẩm \"" + rejected.getItem().getNameItem() +
                        "\" đã bị Admin từ chối. Vui lòng kiểm tra lại thông tin."));
            }
        }

        // FIX BUG #2: Cập nhật số dư Seller sau khi phiên đấu giá kết thúc và settlement
        // Message format từ server: "SELLER_BALANCE_UPDATE|<username>"
        if (msg != null && msg.startsWith("SELLER_BALANCE_UPDATE|") && data instanceof Double) {
            String targetUsername = msg.split("\\|")[1];
            if (targetUsername.equals(currentUser.getUserName())) {
                double newBalance = (Double) data;
                currentUser.setBalance(newBalance);
                updateBalance();
                System.out.println("💰 [SELLER UI] Số dư cập nhật: " + newBalance);
            }
        }
        // Cập nhật số dư từ DEPOSIT/WITHDRAW (data là Double nhưng không có prefix)
        else if (data instanceof Double balance && (msg == null || !msg.contains("|"))) {
            currentUser.setBalance(balance);
            updateBalance();
        }

        // Thông báo cho Seller khi phiên của họ bị hủy giữa chừng
        if ("AUCTION_CANCELED".equals(msg)) {
            if (data instanceof List<?> lst && !lst.isEmpty() && lst.get(0) instanceof Auction) {
                @SuppressWarnings("unchecked")
                List<Auction> updatedList = (List<Auction>) data;
                allAuctions = updatedList;
                renderInventory();
                renderLiveAuctions();
                // Kiểm tra có phiên nào của seller bị hủy không
                updatedList.stream()
                    .filter(a -> a.getStatus() == AuctionStatus.CANCELED
                              && a.getSeller().getUserName().equals(currentUser.getUserName()))
                    .findFirst()
                    .ifPresent(canceled -> showAlert("🚫 Phiên bị hủy",
                            "Phiên đấu giá \"" + canceled.getItem().getNameItem()
                            + "\" đã bị Admin hủy.\n"
                            + "Sản phẩm của bạn hiện đã được trả về kho.\n"
                            + "Tiền của bidder đã được hoàn lại cho họ."));
            }
            return;
        }

        // Nếu nhận được response với message nhưng data không phải List → refresh thủ công
        if (("AUCTION_CREATED".equals(msg) || "UPDATE_AUCTION".equals(msg)
                || "AUCTION_WENT_LIVE".equals(msg) || "AUCTION_ENDED".equals(msg)
                || "AUCTION_CANCELED".equals(msg))
                && !(data instanceof List)) {
            NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
        }

        // Khi phiên kết thúc hoặc seller nhận tiền → tải lại lịch sử thanh toán
        if ("AUCTION_ENDED".equals(msg) ||
                (msg != null && msg.startsWith("SELLER_BALANCE_UPDATE|"))) {
            Platform.runLater(this::loadPaymentHistory);
        }

        // BUG #3 FIX: Thông báo khi phiên của seller kết thúc không có người mua
        if (msg != null && msg.startsWith("AUCTION_NO_BUYER|")) {
            String[] parts = msg.split("\\|");
            String sellerName = parts.length > 1 ? parts[1] : "";
            String itemName   = parts.length > 2 ? parts[2] : "sản phẩm";
            if (sellerName.equals(currentUser.getUserName())) {
                showAlert("📭 Phiên không có người tham gia",
                    "Phiên đấu giá sản phẩm \"" + itemName + "\" đã kết thúc\n"
                    + "nhưng không có ai đặt giá.\n"
                    + "Bạn có thể đăng lại sản phẩm với mức giá hấp dẫn hơn!");
            }
        }

        // BUG #8 FIX: Nhận FORCE_LOGOUT → tự đăng xuất nếu username khớp
        if (msg != null && msg.startsWith("FORCE_LOGOUT|")) {
            String logoutTarget = msg.split("\\|")[1];
            if (logoutTarget.equals(currentUser.getUserName())) {
                NetworkClient.getInstance().removeEventListener(LISTENER_KEY);
                AppContext.logout();
                try {
                    Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Login.fxml"));
                    Stage stage = (Stage) rootPane.getScene().getWindow();
                    stage.setScene(new Scene(root, 900, 600));
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.WARNING);
                    alert.setTitle("⚠️ Tài khoản bị khóa");
                    alert.setHeaderText(null);
                    alert.setContentText("🚫 Tài khoản của bạn đã bị Admin khóa.\n"
                        + "Bạn đã được đăng xuất tự động.");
                    alert.show();
                } catch (IOException ex) { ex.printStackTrace(); }
            }
        }
    }


    private void updateBalance() {
        if (lblHeaderBalance != null)
            lblHeaderBalance.setText(CurrencyFormatter.format(currentUser.getBalance()));
    }

    // ─── Lịch sử thanh toán ──────────────────────────────────────────────────

    /** Cài đặt các cột TableView cho lịch sử nhận tiền. */
    private void setupPaymentTable() {
        if (tablePaymentHistory == null) return;

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        if (colPayItem   != null) colPayItem.setCellValueFactory(
                d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getItemName()));
        if (colPayDate   != null) colPayDate.setCellValueFactory(
                d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getFormattedTime()));
        if (colPayAmount != null) colPayAmount.setCellValueFactory(
                d -> new javafx.beans.property.SimpleStringProperty(
                        CurrencyFormatter.format(d.getValue().getAmount())));
        if (colPayStatus != null) {
            colPayStatus.setCellValueFactory(
                    d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getStatus()));
            colPayStatus.setCellFactory(tc -> new javafx.scene.control.TableCell<>() {
                @Override protected void updateItem(String status, boolean empty) {
                    super.updateItem(status, empty);
                    if (empty || status == null) { setGraphic(null); return; }
                    Label badge = new Label();
                    if ("PAID".equals(status)) {
                        badge.setText("✅ Đã nhận");
                        badge.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else {
                        badge.setText(status);
                        badge.setStyle("-fx-text-fill: #A0A0A0;");
                    }
                    setGraphic(badge);
                }
            });
        }

        // Style bảng dark
        tablePaymentHistory.setStyle("-fx-background-color: transparent; "
                + "-fx-border-color: #2A2A2A; -fx-border-radius: 4; -fx-table-cell-border-color: #1A1A1A;");
    }

    /** Tải và hiển thị lịch sử nhận tiền của Seller. */
    private void loadPaymentHistory() {
        if (tablePaymentHistory == null) return;

        java.util.List<AuctionEarning> history = currentUser.getEarningHistory();
        tablePaymentHistory.setItems(
                javafx.collections.FXCollections.observableArrayList(history));

        // Tính tổng đã nhận
        double total = history.stream().mapToDouble(AuctionEarning::getAmount).sum();
        if (lblTotalEarned != null) {
            lblTotalEarned.setText("Tổng nhận: " + CurrencyFormatter.format(total));
        }
    }

    // ─── Kho hàng ─────────────────────────────────────────────────────────────

    private void renderInventory() {
        if (inventoryContainer == null) return;
        inventoryContainer.getChildren().clear();

        List<Auction> mine = allAuctions.stream()
            .filter(a -> a.getSeller() != null &&
                         a.getSeller().getUserName().equals(currentUser.getUserName()))
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

            // FIX: Badge với đủ các status mới
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
            boolean isEnded = auction.getStatus() == AuctionStatus.FINISHED
                           || auction.getStatus() == AuctionStatus.PAID;
            Label lpt = new Label(isEnded ? "Giá Chốt" : "Giá Hiện Tại");
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

    // ─── Live Auctions ─────────────────────────────────────────────────────────

    private void renderLiveAuctions() {
        if (liveAuctionsList == null) return;
        liveAuctionsList.getChildren().clear();

        // FIX: Include APPROVED (sắp diễn ra) + RUNNING + OPEN
        List<Auction> live = allAuctions.stream()
            .filter(a -> a.getStatus() == AuctionStatus.RUNNING
                      || a.getStatus() == AuctionStatus.OPEN
                      || a.getStatus() == AuctionStatus.APPROVED)
            .limit(4)
            .collect(Collectors.toList());

        if (live.isEmpty()) {
            Label empty = new Label("Hiện không có phiên đấu giá nào đang/sắp diễn ra.");
            empty.setStyle("-fx-text-fill: #555; -fx-font-size: 13px;");
            liveAuctionsList.getChildren().add(empty);
            return;
        }

        for (Auction a : live) {
            VBox card = new VBox(8);
            card.setPrefWidth(240);
            card.setStyle("-fx-background-color: #1A1A1A; -fx-border-color: #2A2A2A; " +
                          "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 14;");

            String statusText = a.getStatus() == AuctionStatus.APPROVED ? "📅 SẮP DIỄN RA" :
                                a.getStatus() == AuctionStatus.RUNNING  ? "🔴 ĐANG ĐẤU GIÁ" : "🟢 ĐANG MỞ";
            Label catBadge = new Label(statusText + " · " + a.getItem().getClass().getSimpleName().toUpperCase());
            catBadge.setStyle("-fx-background-color: rgba(245,197,24,0.15); -fx-text-fill: #F5C518; " +
                              "-fx-font-size: 10px; -fx-padding: 2 7; -fx-background-radius: 3; -fx-font-weight: bold;");

            Label title = new Label(a.getItem().getNameItem());
            title.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");
            title.setWrapText(true);

            Label price = new Label(CurrencyFormatter.format(a.getCurrentHighestBid()));
            price.setStyle("-fx-text-fill: #F5C518; -fx-font-size: 16px; -fx-font-weight: bold;");

            boolean isOwn = a.getSeller().getUserName().equals(currentUser.getUserName());
            Label ownerTag = new Label(isOwn ? "★ Sản phẩm của bạn" : "Người bán: " + a.getSeller().getUserName());
            ownerTag.setStyle("-fx-text-fill: " + (isOwn ? "#F5C518" : "#666") + "; -fx-font-size: 10px;");

            Button btnView = new Button(isOwn ? "Xem phiên của tôi" : "Xem →");
            btnView.setStyle("-fx-background-color: " + (isOwn ? "#2A2A2A" : "transparent") +
                "; -fx-text-fill: " + (isOwn ? "#F5C518" : "#A0A0A0") +
                "; -fx-border-color: " + (isOwn ? "#F5C518" : "#444") +
                "; -fx-border-radius: 4; -fx-cursor: hand; -fx-padding: 5 12; -fx-font-size: 11px;");
            btnView.setMaxWidth(Double.MAX_VALUE);
            btnView.setOnAction(e -> openAuctionDetail(a));

            card.getChildren().addAll(catBadge, title, price, ownerTag, btnView);
            liveAuctionsList.getChildren().add(card);
        }
    }

    // FIX: Badge với đầy đủ các trạng thái mới
    private Label buildStatusBadge(Auction a) {
        Label badge = new Label();
        String base = "-fx-padding: 3 8; -fx-background-radius: 4; -fx-font-size: 10px; -fx-font-weight: bold;";
        switch (a.getStatus()) {
            case PENDING_APPROVAL -> {
                badge.setText("⏳ Chờ Duyệt");
                badge.setStyle(base + "-fx-background-color: rgba(230,126,34,0.25); -fx-text-fill: #e67e22;");
            }
            case APPROVED -> {
                badge.setText("✅ Đã Duyệt");
                badge.setStyle(base + "-fx-background-color: rgba(52,152,219,0.25); -fx-text-fill: #3498db;");
            }
            case RUNNING, OPEN -> {
                badge.setText("🔴 Đang Đấu");
                badge.setStyle(base + "-fx-background-color: rgba(245,197,24,0.2); -fx-text-fill: #F5C518;");
            }
            case FINISHED, PAID -> {
                badge.setText("✅ Đã Bán");
                badge.setStyle(base + "-fx-background-color: rgba(39,174,96,0.2); -fx-text-fill: #27ae60;");
            }
            case REJECTED -> {
                badge.setText("❌ Bị Từ Chối");
                badge.setStyle(base + "-fx-background-color: rgba(231,76,60,0.25); -fx-text-fill: #e74c3c;");
            }
            case CANCELED -> {
                badge.setText("🚫 Đã Hủy");
                badge.setStyle(base + "-fx-background-color: rgba(127,140,141,0.2); -fx-text-fill: #7f8c8d;");
            }
            default -> {
                badge.setText(a.getStatus().toString());
                badge.setStyle(base + "-fx-background-color: rgba(127,140,141,0.2); -fx-text-fill: #7f8c8d;");
            }
        }
        return badge;
    }

    // ─── Upload ảnh ────────────────────────────────────────────────────────────

    @FXML
    public void onChooseImageClick(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn ảnh sản phẩm");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        File f = fc.showOpenDialog(stage);
        if (f != null) {
            selectedImagePath = f.getAbsolutePath();
            if (imgPreview != null) {
                try {
                    imgPreview.setImage(new Image("file:" + selectedImagePath, 300, 200, true, true));
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

    // ─── Form tạo sản phẩm ─────────────────────────────────────────────────────

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

    // ─── FIX #2: onPublishClick — thêm DateTimePicker cho thời gian ────────────
    // THAY THẾ TOÀN BỘ hàm onPublishClick() hiện tại bằng hàm này.
    // Cần thêm 4 field mới vào FXML (xem hướng dẫn bên dưới):
    //   @FXML private DatePicker datePickerStart;
    //   @FXML private TextField  txtTimeStart;      // HH:mm, VD: 09:00
    //   @FXML private DatePicker datePickerEnd;
    //   @FXML private TextField  txtTimeEnd;         // HH:mm, VD: 18:00

    @FXML private javafx.scene.control.DatePicker datePickerStart;
    @FXML private javafx.scene.control.TextField  txtTimeStart;
    @FXML private javafx.scene.control.DatePicker datePickerEnd;
    @FXML private javafx.scene.control.TextField  txtTimeEnd;

    @FXML
    public void onPublishClick(javafx.event.ActionEvent event) {
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

        // FIX #2: Parse thời gian bắt đầu và kết thúc từ DatePicker + TextField
        LocalDateTime startTime = parseDateTime(datePickerStart, txtTimeStart, LocalDateTime.now());
        LocalDateTime endTime   = parseDateTime(datePickerEnd,   txtTimeEnd,   LocalDateTime.now().plusDays(3));

        // Validate thời gian
        if (endTime.isBefore(LocalDateTime.now()) || endTime.isEqual(LocalDateTime.now())) {
            showAlert("Lỗi thời gian", "Thời gian kết thúc phải ở trong tương lai!");
            return;
        }
        if (endTime.isBefore(startTime) || endTime.isEqual(startTime)) {
            showAlert("Lỗi thời gian", "Thời gian kết thúc phải sau thời gian bắt đầu!");
            return;
        }

        List<String> dynamics = extractDynamicInputs();
        Item newItem = buildItem(category, name, desc, price, dynamics);
        if (newItem == null) { showAlert("Lỗi", "Danh mục không hợp lệ."); return; }

        if (selectedImagePath != null) newItem.setImagePath(selectedImagePath);

        // Tạo Auction với thời gian user đã chọn
        Auction newAuction = new Auction(newItem, currentUser, startTime, endTime);

        NetworkClient.getInstance().sendRequest(new Request(ActionType.CREATE_AUCTION, newAuction));
        showAlert("✅ Đang đăng sản phẩm...",
                "Sản phẩm \"" + name + "\" đang được gửi lên server.\n"
                        + "Kho hàng sẽ cập nhật ngay khi server xác nhận.");
        onClearFormClick(null);
    }

    /**
     * Helper: parse LocalDateTime từ DatePicker + TextField giờ.
     * Nếu user không chọn → dùng giá trị mặc định.
     *
     * @param picker       DatePicker (có thể null nếu chưa có trong FXML)
     * @param txtTime      TextField dạng "HH:mm"
     * @param defaultValue Giá trị mặc định nếu input trống/lỗi
     */
    private LocalDateTime parseDateTime(javafx.scene.control.DatePicker picker,
                                        javafx.scene.control.TextField txtTime,
                                        LocalDateTime defaultValue) {
        if (picker == null || picker.getValue() == null) return defaultValue;

        java.time.LocalDate date = picker.getValue();
        int hour = 0, minute = 0;

        if (txtTime != null && !txtTime.getText().trim().isEmpty()) {
            try {
                String[] parts = txtTime.getText().trim().split(":");
                hour   = Integer.parseInt(parts[0]);
                minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                // Clamp
                hour   = Math.max(0, Math.min(23, hour));
                minute = Math.max(0, Math.min(59, minute));
            } catch (NumberFormatException ignored) {
                // Giữ 00:00
            }
        }
        return LocalDateTime.of(date, java.time.LocalTime.of(hour, minute));
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

    // ─── Điều hướng ────────────────────────────────────────────────────────────

    @FXML public void onViewAllAuctionsClick(ActionEvent event) {
        try {
            NetworkClient.getInstance().removeEventListener(LISTENER_KEY);
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

    /** Click vào avatar → mở màn hình Settings (MouseEvent từ HBox.onMouseClicked). */
    @FXML public void onAvatarClick(javafx.scene.input.MouseEvent event) {
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
            stage.setScene(new Scene(root, 900, 650));
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

    /** Nút Dashboard — scroll về top, refresh kho hàng và live auctions. */
    @FXML public void onDashboardClick(ActionEvent event) {
        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
        loadPaymentHistory();
    }

    @FXML public void onRequestAppraisalClick(ActionEvent event) {
        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle("📸 Định Giá Sản Phẩm");
        dlg.setHeaderText("Hướng dẫn định giá sản phẩm đấu giá");
        dlg.setContentText(
            "💡 Gợi ý định giá theo danh mục:\n\n" +
            "🎨 Nghệ thuật (Art):\n" +
            "   • Tác phẩm nổi tiếng: từ 10.000.000 VNĐ\n" +
            "   • Nghệ sĩ mới nổi: từ 500.000 VNĐ\n\n" +
            "💻 Điện tử (Electronics):\n" +
            "   • Dựa theo giá thị trường hiện tại\n" +
            "   • Khởi điểm = 60-70% giá mới\n\n" +
            "🚗 Phương tiện (Vehicle):\n" +
            "   • Dựa theo năm sản xuất và km đã đi\n" +
            "   • Tham khảo chợ xe oto trực tuyến\n\n" +
            "✅ Lời khuyên: Đặt giá khởi điểm thấp để thu hút\n" +
            "   nhiều bidder, giá cuối thường cao hơn dự kiến!"
        );
        dlg.showAndWait();
    }


    // FIX: removeEventListener thay vì removeOnResponseReceived
    @FXML public void onLogoutClick(ActionEvent event) {
        NetworkClient.getInstance().removeEventListener(LISTENER_KEY);
        AppContext.logout();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 900, 600));
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void openAuctionDetail(Auction auction) {
        try {
            NetworkClient.getInstance().removeEventListener(LISTENER_KEY);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/AuctionDetail.fxml"));
            Parent root = loader.load();
            AuctionDetailController ctrl = loader.getController();
            ctrl.setAuctionData(auction);
            Stage stage = (Stage) rootPane.getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 820));
            stage.centerOnScreen();
        } catch (IOException e) { showAlert("Lỗi", "Không thể mở chi tiết phiên."); }
    }

    /**
     * Hiển thị thông báo.
     * — Nếu title có "✅" → toast success
     * — Nếu title có "❌" hoặc "Lỗi" → toast error
     * — Nếu title có "⚠" hoặc "Thiếu" → toast warning
     * — Mặc định → toast default
     */
    private void showAlert(String title, String content) {
        String type;
        String t = title.toLowerCase();
        if (t.contains("✅") || t.contains("thành công") || t.contains("đăng")) {
            type = "success";
        } else if (t.contains("❌") || t.contains("lỗi") || t.contains("không thể") || t.contains("bị từ") || t.contains("🚫")) {
            type = "error";
        } else if (t.contains("⚠") || t.contains("thiếu") || t.contains("hạn")) {
            type = "warning";
        } else {
            type = null;
        }

        // Dùng Toast thầm lặng (góc dưới phải) cho các thông báo nhỏ
        // Dùng Dialog cho lỗi nghiêm trọng cần user xác nhận
        boolean isCritical = "error".equals(type)
                && (content.length() > 60 || title.contains("Lỗi thời gian") || title.contains("Không thể"));

        if (isCritical) {
            NotificationUtil.showAlert(
                rootPane != null && rootPane.getScene() != null ? rootPane.getScene().getWindow() : null,
                title, content, type
            );
        } else {
            // Toast: hiển thị message ngắn gọn
            String toastMsg = content.length() > 80 ? title : title + " — " + content;
            NotificationUtil.showToast(toastMsg, rootPane, type);
        }
    }
}