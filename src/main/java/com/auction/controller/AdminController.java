package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.*;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.protocol.StatusType;
import com.auction.utils.AppContext;
import com.auction.utils.CurrencyFormatter;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AdminController — FIX hoàn chỉnh.
 *
 * FIXES THỰC HIỆN:
 * 1. Dùng addEventListener("admin", ...) → không bị ghi đè
 * 2. handleResponse: khi nhận AUCTION_APPROVED/REJECTED với data=List → cập nhật trực tiếp
 *    Không cần gửi thêm GET_AUCTION_LIST (đã có data đầy đủ trong response)
 * 3. updateStats: đếm APPROVED cùng với RUNNING cho "đang hoạt động"
 * 4. setupAuctionTable: thêm case APPROVED và REJECTED trong display text
 * 5. renderPendingList: chỉ hiển thị PENDING_APPROVAL
 * 6. buildPendingCard: hiển thị đủ thông tin để admin quyết định
 */
public class AdminController {

    @FXML private BorderPane rootPane;
    @FXML private TabPane mainTabPane;

    @FXML private Label lblAdminName;
    @FXML private Label lblNavTotalUsers;
    @FXML private Label lblNavPending;
    @FXML private Label lblNavRunning;
    @FXML private Label lblNavRevenue;

    @FXML private Label lblStatUsers;
    @FXML private Label lblStatUsersSub;
    @FXML private Label lblStatRunning;
    @FXML private Label lblStatRunningSub;
    @FXML private Label lblStatPending;
    @FXML private Label lblStatRevenue;
    @FXML private TableView<Auction>            auctionTable;
    @FXML private TableColumn<Auction, String>  auctionItemCol;
    @FXML private TableColumn<Auction, String>  auctionSellerCol;
    @FXML private TableColumn<Auction, String>  auctionBidCol;
    @FXML private TableColumn<Auction, String>  auctionStatusCol;
    @FXML private TableColumn<Auction, String>  auctionBidCountCol;
    @FXML private TableColumn<Auction, Void>    auctionActionCol;

    @FXML private VBox pendingContainer;

    @FXML private TableView<User>               userTable;
    @FXML private TableColumn<User, String>     userNameCol;
    @FXML private TableColumn<User, String>     userEmailCol;
    @FXML private TableColumn<User, String>     userRoleCol;
    @FXML private TableColumn<User, String>     userBalanceCol;
    @FXML private TableColumn<User, String>     userStatusCol;
    @FXML private TableColumn<User, Void>       userActionCol;
    @FXML private TextField                     txtUserSearch;

    @FXML private Label lblFeedback;

    private ObservableList<User>    allUsers    = FXCollections.observableArrayList();
    private ObservableList<Auction> allAuctions = FXCollections.observableArrayList();
    private FilteredList<User>      filteredUsers;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    // FIX: Key riêng cho admin listener
    private static final String LISTENER_KEY = "admin";

    // ─── INITIALIZE ────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        Admin admin = (Admin) AppContext.getCurrentUser();
        if (lblAdminName != null) lblAdminName.setText("👑 " + admin.getUserName());

        setupAuctionTable();
        setupUserTable();

        filteredUsers = new FilteredList<>(allUsers, u -> true);
        if (userTable != null)   userTable.setItems(filteredUsers);
        if (auctionTable != null) auctionTable.setItems(allAuctions);

        // FIX: Dùng addEventListener với key "admin"
        NetworkClient.getInstance().addEventListener(LISTENER_KEY, response ->
            Platform.runLater(() -> handleResponse(response)));

        // Tải dữ liệu ban đầu
        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_USER_LIST, null));
        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
    }

    // ─── XỬ LÝ RESPONSE ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleResponse(Response response) {
        if (response.getStatus() == StatusType.SUCCESS) {
            Object data = response.getData();
            String msg  = response.getMessage();

            // Nhận danh sách User
            if ("Danh sách User".equals(msg) && data instanceof List) {
                allUsers.setAll((List<User>) data);
                updateStats();
            }

            // FIX: Nhận bất kỳ response nào có data là List<Auction> → cập nhật ngay
            if (data instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Auction) {
                allAuctions.setAll((List<Auction>) data);
                renderPendingList();
                updateStats();
            }

            // FIX: Admin vừa approve/reject → response đã có List<Auction> trong data
            // Không cần gửi thêm GET_AUCTION_LIST nếu data đã là List
            if (msg != null && (msg.startsWith("DUYỆT_OK|") || msg.startsWith("TỪ_CHỐI_OK|")
                    || "AUCTION_APPROVED".equals(msg) || "AUCTION_REJECTED".equals(msg))) {
                boolean isApprove = msg.startsWith("DUYỆT_OK|") || "AUCTION_APPROVED".equals(msg);
                setFeedback(isApprove ? "✅ Đã duyệt sản phẩm thành công!" : "❌ Đã từ chối sản phẩm!",
                    isApprove ? "#27ae60" : "#e74c3c");
                // Nếu data không phải List → mới cần refresh
                if (!(data instanceof List)) {
                    NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
                }
            }

            // Broadcast các sự kiện khác
            if ("UPDATE_AUCTION".equals(msg) || "AUCTION_CREATED".equals(msg)
                    || "AUCTION_WENT_LIVE".equals(msg) || "AUCTION_ENDED".equals(msg)) {
                if (!(data instanceof List)) {
                    NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
                }
            }

            // Ban/Unban
            if (msg != null && msg.contains("khoản")) {
                setFeedback("✅ " + msg, "#27ae60");
                NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_USER_LIST, null));
            }

        } else {
            setFeedback("❌ " + response.getMessage(), "#e74c3c");
        }
    }

    // ─── CẬP NHẬT THỐNG KÊ ────────────────────────────────────────────────────

    private void updateStats() {
        long totalUsers = allUsers.size();
        long bidders    = allUsers.stream().filter(u -> u instanceof Bidder).count();
        long sellers    = allUsers.stream().filter(u -> u instanceof Seller && !(u instanceof Admin)).count();

        // FIX: Đếm APPROVED + RUNNING + OPEN cho "đang hoạt động"
        long running    = allAuctions.stream()
            .filter(a -> a.getStatus() == AuctionStatus.RUNNING
                      || a.getStatus() == AuctionStatus.OPEN
                      || a.getStatus() == AuctionStatus.APPROVED)
            .count();
        long pending    = allAuctions.stream()
            .filter(a -> a.getStatus() == AuctionStatus.PENDING_APPROVAL).count();
        long finished   = allAuctions.stream()
            .filter(a -> a.getStatus() == AuctionStatus.FINISHED || a.getStatus() == AuctionStatus.PAID).count();

        double revenue = allAuctions.stream()
            .filter(a -> a.getStatus() == AuctionStatus.FINISHED || a.getStatus() == AuctionStatus.PAID)
            .mapToDouble(Auction::getCurrentHighestBid).sum();

        // Navbar
        safe(lblNavTotalUsers, String.valueOf(totalUsers));
        safe(lblNavPending,    String.valueOf(pending));
        safe(lblNavRunning,    String.valueOf(running));
        safe(lblNavRevenue,    CurrencyFormatter.format(revenue));

        // Tab tổng quan
        safe(lblStatUsers,      String.valueOf(totalUsers));
        safe(lblStatUsersSub,   bidders + " Bidder · " + sellers + " Seller");
        safe(lblStatRunning,    String.valueOf(running));
        safe(lblStatRunningSub, finished + " phiên đã kết thúc");
        safe(lblStatPending,    String.valueOf(pending));
        safe(lblStatRevenue,    CurrencyFormatter.format(revenue));
    }

    private void safe(Label lbl, String text) {
        if (lbl != null) lbl.setText(text);
    }

    // ─── TAB: DUYỆT SẢN PHẨM ─────────────────────────────────────────────────

    private void renderPendingList() {
        if (pendingContainer == null) return;
        pendingContainer.getChildren().clear();

        List<Auction> pending = allAuctions.stream()
            .filter(a -> a.getStatus() == AuctionStatus.PENDING_APPROVAL)
            .collect(Collectors.toList());

        if (pending.isEmpty()) {
            VBox empty = new VBox(12);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(60));
            Label icon = new Label("✅");
            icon.setStyle("-fx-font-size: 48px;");
            Label msg = new Label("Không có sản phẩm nào chờ duyệt.");
            msg.setStyle("-fx-text-fill: #555; -fx-font-size: 15px;");
            Label sub = new Label("Tất cả sản phẩm đã được xử lý.");
            sub.setStyle("-fx-text-fill: #333; -fx-font-size: 12px;");
            empty.getChildren().addAll(icon, msg, sub);
            pendingContainer.getChildren().add(empty);
            return;
        }

        for (Auction auction : pending) {
            pendingContainer.getChildren().add(buildPendingCard(auction));
        }
    }

    private HBox buildPendingCard(Auction auction) {
        HBox card = new HBox(16);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(18, 22, 18, 22));
        card.setStyle("-fx-background-color: #171717; -fx-border-color: #e74c3c; -fx-border-radius: 8; " +
                      "-fx-background-radius: 8; -fx-border-width: 0 0 0 3;");
        HBox.setHgrow(card, Priority.ALWAYS);

        // Thumbnail
        StackPane thumb = new StackPane();
        thumb.setMinSize(70, 70); thumb.setMaxSize(70, 70);
        thumb.setStyle("-fx-background-color: #2A2A2A; -fx-background-radius: 8;");

        String imgPath = auction.getItem().getImagePath();
        if (imgPath != null && !imgPath.isEmpty()) {
            try {
                ImageView iv = new ImageView(new Image("file:" + imgPath, 70, 70, true, true));
                iv.setFitWidth(70); iv.setFitHeight(70);
                thumb.getChildren().add(iv);
            } catch (Exception ignored) { addCategoryIcon(thumb, auction); }
        } else {
            addCategoryIcon(thumb, auction);
        }

        // Thông tin sản phẩm
        VBox info = new VBox(5);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label badge = new Label("⏳ CHỜ DUYỆT");
        badge.setStyle("-fx-background-color: rgba(231,76,60,0.2); -fx-text-fill: #e74c3c; " +
                       "-fx-padding: 2 8; -fx-background-radius: 3; -fx-font-size: 10px; -fx-font-weight: bold;");
        Label cat = new Label(auction.getItem().getClass().getSimpleName());
        cat.setStyle("-fx-background-color: rgba(245,197,24,0.15); -fx-text-fill: #F5C518; " +
                     "-fx-padding: 2 8; -fx-background-radius: 3; -fx-font-size: 10px; -fx-font-weight: bold;");
        header.getChildren().addAll(badge, cat);

        Label name = new Label(auction.getItem().getNameItem());
        name.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
        name.setWrapText(true);

        Label desc = new Label(auction.getItem().getDescriptionItem() != null
                && !auction.getItem().getDescriptionItem().isEmpty()
                ? auction.getItem().getDescriptionItem() : "Không có mô tả.");
        desc.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");
        desc.setWrapText(true);
        desc.setMaxWidth(500);

        GridPane meta = new GridPane();
        meta.setHgap(20); meta.setVgap(6);
        addMeta(meta, "Người bán:", auction.getSeller().getUserName(), 0);
        addMeta(meta, "Giá khởi điểm:", CurrencyFormatter.format(auction.getItem().getStartingPrice()), 1);
        addMeta(meta, "ID phiên:", auction.getAuctionId().substring(0, 8) + "...", 2);

        // FIX: Hiển thị startTime/endTime để admin biết thông tin đầy đủ
        GridPane timeMeta = new GridPane();
        timeMeta.setHgap(20); timeMeta.setVgap(4);
        if (auction.getStartTime() != null) {
            addMeta(timeMeta, "Bắt đầu:", auction.getStartTime().format(DTF), 0);
        }
        if (auction.getEndTime() != null) {
            addMeta(timeMeta, "Kết thúc:", auction.getEndTime().format(DTF), 1);
        }

        info.getChildren().addAll(header, name, desc, meta, timeMeta);

        // Nút hành động
        VBox actions = new VBox(10);
        actions.setAlignment(Pos.CENTER);
        actions.setMinWidth(160);

        Button btnApprove = new Button("✅  PHÊ DUYỆT");
        btnApprove.setMaxWidth(Double.MAX_VALUE);
        btnApprove.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; " +
                            "-fx-background-radius: 5; -fx-cursor: hand; -fx-padding: 10 18; -fx-font-size: 13px;");
        btnApprove.setOnAction(e -> onApproveAuction(auction, btnApprove));

        Button btnReject = new Button("❌  TỪ CHỐI");
        btnReject.setMaxWidth(Double.MAX_VALUE);
        btnReject.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold; " +
                           "-fx-background-radius: 5; -fx-cursor: hand; -fx-padding: 10 18; -fx-font-size: 13px;");
        btnReject.setOnAction(e -> onRejectAuction(auction, btnReject));

        actions.getChildren().addAll(btnApprove, btnReject);
        card.getChildren().addAll(thumb, info, actions);
        return card;
    }

    private void addCategoryIcon(StackPane thumb, Auction a) {
        String icon = switch (a.getItem().getClass().getSimpleName()) {
            case "Art" -> "🎨"; case "Electronics" -> "💻"; case "Vehicle" -> "🚗"; default -> "📦";
        };
        Label lbl = new Label(icon);
        lbl.setStyle("-fx-font-size: 28px;");
        thumb.getChildren().add(lbl);
    }

    private void addMeta(GridPane g, String key, String val, int col) {
        Label k = new Label(key);
        k.setStyle("-fx-text-fill: #555; -fx-font-size: 11px;");
        Label v = new Label(val);
        v.setStyle("-fx-text-fill: #A0A0A0; -fx-font-size: 11px; -fx-font-weight: bold;");
        g.add(k, col * 2, 0);
        g.add(v, col * 2 + 1, 0);
    }

    private void onApproveAuction(Auction auction, Button btn) {
        btn.setDisable(true);
        setFeedback("⏳ Đang duyệt " + auction.getItem().getNameItem() + "...", "#F5C518");
        NetworkClient.getInstance().sendRequest(
            new Request(ActionType.APPROVE_AUCTION, auction.getAuctionId()));
    }

    private void onRejectAuction(Auction auction, Button btn) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận từ chối");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc muốn TỪ CHỐI sản phẩm:\n\"" +
            auction.getItem().getNameItem() + "\"?");
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                btn.setDisable(true);
                setFeedback("⏳ Đang từ chối " + auction.getItem().getNameItem() + "...", "#e74c3c");
                NetworkClient.getInstance().sendRequest(
                    new Request(ActionType.REJECT_AUCTION, auction.getAuctionId()));
            }
        });
    }

    // ─── TAB: BẢNG ĐẤU GIÁ ───────────────────────────────────────────────────

    private void setupAuctionTable() {
        if (auctionItemCol != null)
            auctionItemCol.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getItem().getNameItem()));
        if (auctionSellerCol != null)
            auctionSellerCol.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getSeller().getUserName()));
        if (auctionBidCol != null)
            auctionBidCol.setCellValueFactory(d ->
                new SimpleStringProperty(CurrencyFormatter.format(d.getValue().getCurrentHighestBid())));
        if (auctionStatusCol != null)
            auctionStatusCol.setCellValueFactory(d -> {
                // FIX: Thêm case APPROVED và REJECTED
                AuctionStatus s = d.getValue().getStatus();
                String text = switch (s) {
                    case PENDING_APPROVAL -> "⏳ Chờ duyệt";
                    case APPROVED         -> "✅ Đã duyệt";
                    case RUNNING          -> "🔴 Đang chạy";
                    case OPEN             -> "🟢 Đang mở";
                    case REJECTED         -> "❌ Bị từ chối";
                    case FINISHED         -> "✅ Kết thúc";
                    case PAID             -> "💰 Đã thanh toán";
                    case CANCELED         -> "🚫 Đã hủy";
                    default               -> s.toString();
                };
                return new SimpleStringProperty(text);
            });
        if (auctionBidCountCol != null)
            auctionBidCountCol.setCellValueFactory(d ->
                new SimpleStringProperty(String.valueOf(d.getValue().getBidHistory().size())));

        if (auctionActionCol != null)
            auctionActionCol.setCellFactory(col -> new TableCell<>() {
                private final Button btnCancel  = new Button("🚫 Dừng Phiên");
                private final Button btnApprove = new Button("✅ Duyệt");
                private final HBox box = new HBox(6, btnApprove, btnCancel);
                {
                    box.setAlignment(Pos.CENTER_LEFT);
                    btnCancel.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-cursor: hand; " +
                                       "-fx-padding: 4 10; -fx-background-radius: 4; -fx-font-size: 11px;");
                    btnApprove.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-cursor: hand; " +
                                        "-fx-padding: 4 10; -fx-background-radius: 4; -fx-font-size: 11px;");
                    btnCancel.setOnAction(e -> {
                        Auction a = getTableView().getItems().get(getIndex());
                        NetworkClient.getInstance().sendRequest(
                            new Request(ActionType.CANCEL_AUCTION, a.getAuctionId()));
                    });
                    btnApprove.setOnAction(e -> {
                        Auction a = getTableView().getItems().get(getIndex());
                        onApproveAuction(a, btnApprove);
                    });
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || getIndex() >= getTableView().getItems().size()) {
                        setGraphic(null); return;
                    }
                    Auction a = getTableView().getItems().get(getIndex());
                    btnApprove.setVisible(a.getStatus() == AuctionStatus.PENDING_APPROVAL);
                    btnApprove.setManaged(a.getStatus() == AuctionStatus.PENDING_APPROVAL);
                    boolean canStop = a.getStatus() == AuctionStatus.RUNNING
                                   || a.getStatus() == AuctionStatus.OPEN
                                   || a.getStatus() == AuctionStatus.APPROVED;
                    btnCancel.setVisible(canStop);
                    btnCancel.setManaged(canStop);
                    setGraphic(box);
                }
            });
    }

    // ─── TAB: QUẢN LÝ USER ───────────────────────────────────────────────────

    private void setupUserTable() {
        if (userNameCol != null)
            userNameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUserName()));
        if (userEmailCol != null)
            userEmailCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmail()));
        if (userRoleCol != null)
            userRoleCol.setCellValueFactory(d -> {
                User u = d.getValue();
                if (u instanceof Admin)  return new SimpleStringProperty("👑 Admin");
                if (u instanceof Seller) return new SimpleStringProperty("🏪 Seller");
                return new SimpleStringProperty("🏷 Bidder");
            });
        if (userBalanceCol != null)
            userBalanceCol.setCellValueFactory(d -> {
                User u = d.getValue();
                double bal = 0;
                if (u instanceof Bidder b) bal = b.getBalance();
                else if (u instanceof Seller s) bal = s.getBalance();
                return new SimpleStringProperty(CurrencyFormatter.format(bal));
            });
        if (userStatusCol != null)
            userStatusCol.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().isBanned() ? "🔒 Bị khóa" : "✅ Hoạt động"));
        if (userActionCol != null)
            userActionCol.setCellFactory(col -> new TableCell<>() {
                private final Button btn = new Button();
                {
                    btn.setOnAction(e -> {
                        User user = getTableView().getItems().get(getIndex());
                        NetworkClient.getInstance().sendRequest(
                            new Request(ActionType.BAN_USER, user.getUserName()));
                    });
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || getIndex() >= getTableView().getItems().size()) {
                        setGraphic(null); return;
                    }
                    User user = getTableView().getItems().get(getIndex());
                    if (user instanceof Admin) { setGraphic(null); return; }
                    btn.setText(user.isBanned() ? "🔓 Mở Khóa" : "🔒 Khóa");
                    btn.setStyle(user.isBanned()
                        ? "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 5 12; -fx-background-radius: 4; -fx-font-size: 11px;"
                        : "-fx-background-color: #c0392b; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 5 12; -fx-background-radius: 4; -fx-font-size: 11px;");
                    setGraphic(btn);
                }
            });
    }

    @FXML
    public void onUserSearch() {
        String query = txtUserSearch != null ? txtUserSearch.getText().trim().toLowerCase() : "";
        if (filteredUsers != null) {
            filteredUsers.setPredicate(u -> query.isEmpty()
                || u.getUserName().toLowerCase().contains(query)
                || u.getEmail().toLowerCase().contains(query));
        }
    }

    // ─── Hành động ────────────────────────────────────────────────────────────

    @FXML
    public void onRefreshClick(ActionEvent event) {
        setFeedback("🔄 Đang làm mới...", "#A0A0A0");
        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_USER_LIST, null));
        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
    }

    // FIX: removeEventListener thay vì removeOnResponseReceived
    @FXML
    public void onLogoutClick(ActionEvent event) {
        NetworkClient.getInstance().removeEventListener(LISTENER_KEY);
        AppContext.logout();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 900, 600));
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void setFeedback(String msg, String color) {
        if (lblFeedback != null) {
            lblFeedback.setText(msg);
            lblFeedback.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13px; -fx-font-weight: bold; " +
                                 "-fx-padding: 10 28; -fx-background-color: #0a0a0a; " +
                                 "-fx-border-color: #1E1E1E; -fx-border-width: 1 0 0 0;");
        }
    }
}