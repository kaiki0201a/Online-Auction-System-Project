package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.*;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.StatusType;
import com.auction.utils.AppContext;
import com.auction.utils.CurrencyFormatter;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * BidderDashboardController — FIX hoàn chỉnh.
 *
 * FIXES THỰC HIỆN:
 * 1. Dùng addEventListener("bidder", ...) → không bị ghi đè
 * 2. renderFeaturedAuctions: filter RUNNING + APPROVED (sắp diễn ra) + OPEN
 *    Badge riêng: "SẮP DIỄN RA" cho APPROVED, "ĐANG ĐẤU GIÁ" cho RUNNING
 * 3. Xử lý broadcast AUCTION_WENT_LIVE → append live auction realtime
 * 4. Xử lý broadcast AUCTION_ENDED → cập nhật status
 * 5. onLogout: removeEventListener("bidder")
 */
public class BidderDashboardController {

    @FXML private BorderPane rootPane;
    @FXML private Label lblWelcome;
    @FXML private Label lblStat;
    @FXML private Label lblHeaderBalance;
    @FXML private Label lblMainBalance;
    @FXML private Label lblAvatar;
    @FXML private Label lblUsername;
    @FXML private TextField txtSearch;

    @FXML private HBox liveAuctionsContainer;
    @FXML private ComboBox<String> comboCategory;

    @FXML private TableView<BidTransaction>           tableRecentBids;
    @FXML private TableColumn<BidTransaction, String> colItem;
    @FXML private TableColumn<BidTransaction, String> colDate;
    @FXML private TableColumn<BidTransaction, String> colAmount;
    @FXML private TableColumn<BidTransaction, BidTransaction> colStatus;

    private Bidder currentUser;
    private ObservableList<Auction> auctionData = FXCollections.observableArrayList();
    private Timeline masterTimer;
    private final List<Label> timerLabels = new ArrayList<>();

    // FIX: Key riêng cho bidder listener
    private static final String LISTENER_KEY = "bidder";

    @FXML
    public void initialize() {
        currentUser = (Bidder) AppContext.getCurrentUser();
        setupUserInfo();
        setupTableColumns();
        setupCategoryFilter();

        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, o, n) -> renderFeaturedAuctions());
        }

        // Timer đếm ngược 1 giây
        masterTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateAllTimers()));
        masterTimer.setCycleCount(Animation.INDEFINITE);
        masterTimer.play();

        // FIX: Dùng addEventListener với key "bidder"
        NetworkClient.getInstance().addEventListener(LISTENER_KEY, response -> {
            Platform.runLater(() -> handleResponse(response));
        });

        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
    }

    // ─── XỬ LÝ RESPONSE ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleResponse(com.auction.protocol.Response response) {
        // FIX: Xử lý sự kiện mất kết nối / kết nối lại từ NetworkClient auto-reconnect
        String msg = response.getMessage();
        if ("CONNECTION_LOST".equals(msg)) {
            showConnectionAlert("⚠️ Mất kết nối", "Đang thử kết nối lại với server...",
                    javafx.scene.control.Alert.AlertType.WARNING);
            return;
        }
        if ("CONNECTION_RESTORED".equals(msg)) {
            // Kết nối lại thành công → tải lại dữ liệu ngay
            NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
            showConnectionAlert("✅ Đã kết nối lại", "Kết nối tới server đã được khôi phục.",
                    javafx.scene.control.Alert.AlertType.INFORMATION);
            return;
        }
        if ("CONNECTION_FAILED".equals(msg)) {
            showConnectionAlert("❌ Mất kết nối", response.getData() != null
                    ? response.getData().toString()
                    : "Không thể kết nối tới server. Vui lòng khởi động lại ứng dụng.",
                    javafx.scene.control.Alert.AlertType.ERROR);
            return;
        }

        if (response.getStatus() != StatusType.SUCCESS) return;

        Object data = response.getData();

        // FIX: Nhận bất kỳ response nào có data là List<Auction> → cập nhật ngay
        if (data instanceof List<?> dataList && !dataList.isEmpty()
                && dataList.get(0) instanceof Auction) {
            auctionData.setAll((List<Auction>) dataList);

            // FIX: Đếm RUNNING + APPROVED cho số liệu thị trường
            long activeCount = auctionData.stream()
                .filter(a -> a.getStatus() == AuctionStatus.RUNNING
                          || a.getStatus() == AuctionStatus.APPROVED
                          || a.getStatus() == AuctionStatus.OPEN)
                .count();
            if (lblStat != null)
                lblStat.setText("Thị trường đang hoạt động · " + activeCount + " phiên đấu giá");

            renderFeaturedAuctions();
            loadRecentBids();
        }

        // Nếu nhận broadcast nhưng data không phải List → gửi GET_AUCTION_LIST
        if (("AUCTION_APPROVED".equals(msg) || "AUCTION_CREATED".equals(msg)
                || "UPDATE_AUCTION".equals(msg) || "AUCTION_REJECTED".equals(msg)
                || "AUCTION_WENT_LIVE".equals(msg) || "AUCTION_ENDED".equals(msg))
                && !(data instanceof List)) {
            NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
        }

        // Cập nhật số dư
        if (data instanceof Double balance) {
            currentUser.setBalance(balance);
            updateBalance();
        }
    }

    /** Hiện Alert thông báo trạng thái kết nối. */
    private void showConnectionAlert(String title, String content,
                                     javafx.scene.control.Alert.AlertType type) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show(); // show() thay vì showAndWait() để không block UI thread
    }


    // ─── Cài đặt UI ──────────────────────────────────────────────────────────

    private void setupUserInfo() {
        String name = currentUser.getUserName();
        if (lblAvatar != null)   lblAvatar.setText(name.substring(0, 1).toUpperCase());
        if (lblUsername != null) lblUsername.setText(name);
        if (lblWelcome != null)  lblWelcome.setText("Chào mừng trở lại, " + name + " 👋");
        updateBalance();
    }

    private void updateBalance() {
        String b = CurrencyFormatter.format(currentUser.getBalance());
        if (lblHeaderBalance != null) lblHeaderBalance.setText(b);
        if (lblMainBalance != null)   lblMainBalance.setText(b);
    }

    private void setupCategoryFilter() {
        if (comboCategory != null) {
            comboCategory.setItems(FXCollections.observableArrayList("Tất cả", "Art", "Electronics", "Vehicle"));
            comboCategory.setValue("Tất cả");
            comboCategory.setOnAction(e -> renderFeaturedAuctions());
        }
    }

    private void setupTableColumns() {
        if (tableRecentBids == null) return;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        colItem.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getAuction().getItem().getNameItem()));
        colDate.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getTimestamp().format(dtf)));
        colAmount.setCellValueFactory(d ->
            new SimpleStringProperty(CurrencyFormatter.format(d.getValue().getBidAmount())));
        colStatus.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colStatus.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(BidTransaction tx, boolean empty) {
                super.updateItem(tx, empty);
                if (empty || tx == null) { setGraphic(null); return; }
                Auction a = tx.getAuction();
                boolean win = a.getHighestBidder() != null &&
                    a.getHighestBidder().getUserName().equals(currentUser.getUserName());
                boolean done = a.getStatus() == AuctionStatus.FINISHED || a.getStatus() == AuctionStatus.PAID;
                Label badge = new Label();
                if (done) {
                    badge.setText(win ? "✅ ĐÃ THẮNG" : "❌ THUA");
                    badge.setStyle("-fx-text-fill:" + (win ? "#27ae60" : "#e74c3c") + ";-fx-font-weight:bold;");
                } else {
                    badge.setText(win ? "🏆 ĐANG THẮNG" : "⚠ BỊ VƯỢT");
                    badge.setStyle("-fx-text-fill:" + (win ? "#F5C518" : "#e74c3c") + ";-fx-font-weight:bold;");
                }
                setGraphic(badge);
            }
        });
    }

    private void loadRecentBids() {
        if (tableRecentBids == null) return;
        tableRecentBids.setItems(FXCollections.observableArrayList(currentUser.getTransactionHistory()));
    }

    // ─── Render auction cards nổi bật ─────────────────────────────────────────

    private void renderFeaturedAuctions() {
        if (liveAuctionsContainer == null) return;
        timerLabels.clear();
        liveAuctionsContainer.getChildren().clear();

        String kw  = txtSearch != null ? txtSearch.getText().toLowerCase() : "";
        String cat = comboCategory != null ? comboCategory.getValue() : "Tất cả";

        // FIX: Include RUNNING + APPROVED + OPEN trong market view
        List<Auction> filtered = auctionData.stream()
            .filter(a -> a.getStatus() == AuctionStatus.RUNNING
                      || a.getStatus() == AuctionStatus.OPEN
                      || a.getStatus() == AuctionStatus.APPROVED)
            .filter(a -> a.getItem().getNameItem().toLowerCase().contains(kw))
            .filter(a -> "Tất cả".equals(cat) || a.getItem().getClass().getSimpleName().equals(cat))
            .limit(3)
            .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            Label empty = new Label("Hiện chưa có phiên đấu giá nào đang hoặc sắp diễn ra.");
            empty.setStyle("-fx-text-fill: #555; -fx-font-size: 15px;");
            liveAuctionsContainer.getChildren().add(empty);
            return;
        }

        // Card 1: BIG (~55%)
        Node bigCard = buildCard(filtered.get(0), true);
        HBox.setHgrow(bigCard, Priority.ALWAYS);
        liveAuctionsContainer.getChildren().add(bigCard);

        // Card 2+3: nhỏ (~45%)
        if (filtered.size() > 1) {
            VBox rightCol = new VBox(16);
            rightCol.setPrefWidth(340);
            for (int i = 1; i < filtered.size(); i++) {
                rightCol.getChildren().add(buildCard(filtered.get(i), false));
            }
            liveAuctionsContainer.getChildren().add(rightCol);
        }
    }

    private Node buildCard(Auction auction, boolean isBig) {
        VBox card = new VBox();
        card.getStyleClass().add("auction-card");

        StackPane imgBox = new StackPane();
        imgBox.getStyleClass().add("card-image-placeholder");
        imgBox.setPrefHeight(isBig ? 330 : 155);

        String imgPath = auction.getItem().getImagePath();
        if (imgPath != null && !imgPath.isEmpty()) {
            try {
                Image img = new Image("file:" + imgPath, 600, 330, true, true);
                ImageView iv = new ImageView(img);
                iv.setFitWidth(isBig ? 600 : 340);
                iv.setFitHeight(isBig ? 330 : 155);
                iv.setPreserveRatio(false);
                imgBox.getChildren().add(iv);
            } catch (Exception ignored) {
                addPlaceholderLabel(imgBox, auction, isBig);
            }
        } else {
            addPlaceholderLabel(imgBox, auction, isBig);
        }

        // FIX: Badge khác nhau cho APPROVED (sắp diễn ra) vs RUNNING (đang đấu)
        boolean isApproved = auction.getStatus() == AuctionStatus.APPROVED;
        Label badgeLive = new Label(isApproved ? "📅 SẮP DIỄN RA" : "🔴 ĐANG ĐẤU GIÁ");
        badgeLive.setStyle("-fx-background-color:" + (isApproved ? "rgba(52,152,219,0.2)" : "rgba(245,197,24,0.2)") +
            ";-fx-text-fill:" + (isApproved ? "#3498db" : "#F5C518") +
            ";-fx-font-size:10px;-fx-padding:3 8;-fx-background-radius:4;-fx-font-weight:bold;");
        StackPane.setAlignment(badgeLive, Pos.TOP_LEFT);
        StackPane.setMargin(badgeLive, new Insets(10));
        imgBox.getChildren().add(badgeLive);

        // Timer đếm ngược
        Label lblTimer = new Label();
        lblTimer.setStyle("-fx-background-color:rgba(0,0,0,0.7);-fx-text-fill:white;" +
            "-fx-font-size:11px;-fx-padding:3 8;-fx-background-radius:4;-fx-font-weight:bold;");
        lblTimer.setUserData(auction);
        timerLabels.add(lblTimer);
        StackPane.setAlignment(lblTimer, Pos.TOP_RIGHT);
        StackPane.setMargin(lblTimer, new Insets(10));
        imgBox.getChildren().add(lblTimer);

        // Phần thông tin
        VBox info = new VBox(10);
        info.setPadding(new Insets(16));

        Label lblId = new Label("LÔ " + auction.getAuctionId().substring(0, 6).toUpperCase() + " · " +
            auction.getItem().getClass().getSimpleName().toUpperCase());
        lblId.setStyle("-fx-text-fill:#666;-fx-font-size:10px;-fx-font-weight:bold;");

        Label lblTitle = new Label(auction.getItem().getNameItem());
        lblTitle.setStyle("-fx-text-fill:#FFF;-fx-font-weight:bold;-fx-font-size:" +
            (isBig ? "19" : "14") + "px;");
        lblTitle.setWrapText(true);

        Label lblSeller = new Label("Người bán: " + auction.getSeller().getUserName());
        lblSeller.setStyle("-fx-text-fill:#666;-fx-font-size:11px;");

        HBox botRow = new HBox();
        botRow.setAlignment(Pos.CENTER_LEFT);
        VBox priceCol = new VBox(2);
        Label lp = new Label(isApproved ? "GIÁ KHỞI ĐIỂM" : "GIÁ HIỆN TẠI");
        lp.setStyle("-fx-text-fill:#666;-fx-font-size:9px;");
        Label lv = new Label(CurrencyFormatter.format(auction.getCurrentHighestBid()));
        lv.setStyle("-fx-text-fill:#F5C518;-fx-font-weight:bold;-fx-font-size:" +
            (isBig ? "20" : "15") + "px;");
        priceCol.getChildren().addAll(lp, lv);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        // FIX: Button text khác nhau cho approved vs running
        Button btn = new Button(isApproved ? "XEM CHI TIẾT →" : "ĐẶT GIÁ →");
        btn.getStyleClass().add(isBig ? "btn-gold" : "btn-outline");
        btn.setOnAction(e -> openDetail(auction));
        botRow.getChildren().addAll(priceCol, sp, btn);

        info.getChildren().addAll(lblId, lblTitle, lblSeller, botRow);
        card.getChildren().addAll(imgBox, info);

        // Hover zoom
        card.setOnMouseEntered(e -> animate(card, 1.02));
        card.setOnMouseExited(e -> animate(card, 1.0));
        card.setOnMouseClicked(e -> openDetail(auction));
        return card;
    }

    private void addPlaceholderLabel(StackPane box, Auction auction, boolean isBig) {
        Label lbl = new Label(auction.getItem().getClass().getSimpleName());
        lbl.setStyle("-fx-text-fill:#333;-fx-font-size:" + (isBig ? "40" : "22") + "px;-fx-font-weight:bold;");
        box.getChildren().add(lbl);
    }

    private void updateAllTimers() {
        for (Label lbl : timerLabels) {
            Auction a = (Auction) lbl.getUserData();
            if (a == null) continue;

            // FIX: Timer cho APPROVED hiển thị thời gian bắt đầu; RUNNING hiển thị thời gian kết thúc
            boolean isApproved = a.getStatus() == AuctionStatus.APPROVED;
            LocalDateTime target = isApproved ? a.getStartTime() : a.getEndTime();
            if (target == null) continue;

            long secs = ChronoUnit.SECONDS.between(LocalDateTime.now(), target);
            if (secs <= 0) {
                if (isApproved) {
                    lbl.setText("BẮT ĐẦU");
                    lbl.setStyle("-fx-background-color:rgba(39,174,96,0.8);-fx-text-fill:white;" +
                        "-fx-font-size:11px;-fx-padding:3 8;-fx-background-radius:4;");
                } else {
                    lbl.setText("KẾT THÚC");
                    lbl.setStyle("-fx-background-color:rgba(231,76,60,0.8);-fx-text-fill:white;" +
                        "-fx-font-size:11px;-fx-padding:3 8;-fx-background-radius:4;");
                }
            } else {
                String prefix = isApproved ? "BĐ: " : "";
                lbl.setText(prefix + String.format("%02d:%02d:%02d",
                    secs / 3600, (secs % 3600) / 60, secs % 60));
            }
        }
    }

    private void animate(Node n, double scale) {
        ScaleTransition st = new ScaleTransition(Duration.millis(200), n);
        st.setToX(scale); st.setToY(scale); st.play();
    }

    private void openDetail(Auction auction) {
        try {
            NetworkClient.getInstance().removeEventListener(LISTENER_KEY);
            if (masterTimer != null) masterTimer.stop();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/AuctionDetail.fxml"));
            Parent root = loader.load();
            AuctionDetailController ctrl = loader.getController();
            ctrl.setAuctionData(auction);
            Stage stage = (Stage) rootPane.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ─── Hành động FXML ───────────────────────────────────────────────────────

    @FXML public void onSearchClick(ActionEvent event) { renderFeaturedAuctions(); }

    @FXML public void onViewAllAuctions(ActionEvent event) {
        try {
            NetworkClient.getInstance().removeEventListener(LISTENER_KEY);
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/AuctionList.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 800));
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML public void onDepositClick(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/DepositWithdraw.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 900, 650));
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML public void onWithdrawClick(ActionEvent event) { onDepositClick(event); }

    @FXML public void onSettingsClick(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Settings.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 600, 530));
        } catch (IOException e) { e.printStackTrace(); }
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

    // FIX: removeEventListener thay vì removeOnResponseReceived
    @FXML public void onLogoutClick(ActionEvent event) {
        if (masterTimer != null) masterTimer.stop();
        NetworkClient.getInstance().removeEventListener(LISTENER_KEY);
        AppContext.logout();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 900, 600));
        } catch (IOException e) { e.printStackTrace(); }
    }
}