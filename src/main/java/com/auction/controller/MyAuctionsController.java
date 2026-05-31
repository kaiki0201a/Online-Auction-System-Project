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
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MyAuctionsController — Fix #6.
 * Hiển thị danh sách phiên đấu giá mà Bidder đã tham gia.
 * Data lấy từ transactionHistory của currentUser, sau đó được làm phong phú
 * bởi danh sách auction mới nhất từ server để hiển thị trạng thái thực.
 */
public class MyAuctionsController {

    @FXML private BorderPane rootPane;
    @FXML private Label lblCount;
    @FXML private Label lblLeading;
    @FXML private Label lblOutbid;
    @FXML private Label lblWon;
    @FXML private ComboBox<String> comboFilter;

    @FXML private TableView<MyAuctionRow> tableAuctions;
    @FXML private TableColumn<MyAuctionRow, String> colItem;
    @FXML private TableColumn<MyAuctionRow, String> colStatus;
    @FXML private TableColumn<MyAuctionRow, String> colMyBid;
    @FXML private TableColumn<MyAuctionRow, String> colCurrentBid;
    @FXML private TableColumn<MyAuctionRow, String> colEndTime;
    @FXML private TableColumn<MyAuctionRow, Button> colAction;

    private Bidder currentUser;
    private List<Auction> allServerAuctions = new ArrayList<>();
    private final ObservableList<MyAuctionRow> rows = FXCollections.observableArrayList();
    private Timeline refreshTimer;

    private static final String LISTENER_KEY = "myAuctions";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    @FXML
    public void initialize() {
        currentUser = (Bidder) AppContext.getCurrentUser();

        comboFilter.setItems(FXCollections.observableArrayList(
                "Tất cả", "Đang dẫn đầu", "Đang bị vượt", "Đã thắng", "Đã kết thúc"));
        comboFilter.setValue("Tất cả");
        comboFilter.setOnAction(e -> renderTable());

        setupTable();

        // Subscribe server updates
        NetworkClient.getInstance().addEventListener(LISTENER_KEY, response -> {
            Platform.runLater(() -> {
                if (response.getStatus() == StatusType.SUCCESS
                        && response.getData() instanceof List<?> list
                        && !list.isEmpty() && list.get(0) instanceof Auction) {
                    allServerAuctions = (List<Auction>) list;
                    renderTable();
                }
            });
        });
        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));

        // Auto-refresh timer để cập nhật countdown mỗi giây
        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateCountdowns()));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }

    private void setupTable() {
        colItem.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().itemName));
        colStatus.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().statusLabel));
        colMyBid.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().myBid));
        colCurrentBid.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().currentBid));
        colEndTime.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().endTime));
        colAction.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Button item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                MyAuctionRow row = getTableRow().getItem();
                if (row.auction == null) { setGraphic(null); return; }
                Button btn = new Button("Xem →");
                btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #F5C518; " +
                        "-fx-border-color: #F5C518; -fx-border-radius: 4; -fx-cursor: hand; -fx-padding: 4 12;");
                btn.setOnAction(e -> openDetail(row.auction));
                setGraphic(btn);
            }
        });

        // Color-code status column
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                if (item.contains("DẪN ĐẦU")) setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                else if (item.contains("BỊ VƯỢT")) setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                else if (item.contains("THẮNG")) setStyle("-fx-text-fill: #F5C518; -fx-font-weight: bold;");
                else setStyle("-fx-text-fill: #888;");
            }
        });

        tableAuctions.setItems(rows);
    }

    private void renderTable() {
        if (currentUser == null) return;
        List<BidTransaction> txHistory = currentUser.getTransactionHistory();
        if (txHistory == null || txHistory.isEmpty()) {
            rows.clear();
            updateStats();
            return;
        }

        // Lấy danh sách auction ID mà bidder đã tham gia (distinct)
        Set<String> participatedIds = txHistory.stream()
                .map(bt -> bt.getAuction().getAuctionId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String filter = comboFilter.getValue();
        List<MyAuctionRow> newRows = new ArrayList<>();

        for (String aId : participatedIds) {
            // Tìm auction từ server list (có dữ liệu realtime), fallback về local
            Auction auction = allServerAuctions.stream()
                    .filter(a -> a.getAuctionId().equals(aId))
                    .findFirst()
                    .orElse(txHistory.stream()
                            .filter(bt -> bt.getAuction().getAuctionId().equals(aId))
                            .findFirst().map(BidTransaction::getAuction).orElse(null));
            if (auction == null) continue;

            // Tính giá cao nhất của tôi trong phiên này
            double myHighestBid = txHistory.stream()
                    .filter(bt -> bt.getAuction().getAuctionId().equals(aId))
                    .mapToDouble(BidTransaction::getBidAmount)
                    .max().orElse(0);

            // Xác định trạng thái
            String statusLabel = resolveStatus(auction);

            // Áp dụng filter
            if ("Đang dẫn đầu".equals(filter) && !statusLabel.contains("DẪN ĐẦU")) continue;
            if ("Đang bị vượt".equals(filter) && !statusLabel.contains("BỊ VƯỢT")) continue;
            if ("Đã thắng".equals(filter) && !statusLabel.contains("THẮNG")) continue;
            if ("Đã kết thúc".equals(filter) && !statusLabel.contains("KẾT THÚC") && !statusLabel.contains("THẮNG")) continue;

            MyAuctionRow row = new MyAuctionRow();
            row.auction = auction;
            row.itemName = auction.getItem().getNameItem();
            row.statusLabel = statusLabel;
            row.myBid = CurrencyFormatter.format(myHighestBid);
            row.currentBid = CurrencyFormatter.format(auction.getCurrentHighestBid());
            row.endTime = formatTime(auction.getEndTime());
            newRows.add(row);
        }

        rows.setAll(newRows);
        lblCount.setText("(" + newRows.size() + " phiên)");
        updateStats();
    }

    private String resolveStatus(Auction auction) {
        AuctionStatus status = auction.getStatus();
        if (status == AuctionStatus.FINISHED || status == AuctionStatus.PAID) {
            if (auction.getHighestBidder() != null
                    && auction.getHighestBidder().getUserName().equals(currentUser.getUserName())) {
                return "🏆 THẮNG";
            }
            return "⏹ KẾT THÚC";
        }
        if (status == AuctionStatus.CANCELED) return "🚫 ĐÃ HỦY";
        if (status == AuctionStatus.REJECTED) return "❌ BỊ TỪ CHỐI";
        if (auction.getHighestBidder() != null
                && auction.getHighestBidder().getUserName().equals(currentUser.getUserName())) {
            return "✅ DẪN ĐẦU";
        }
        return "📉 BỊ VƯỢT";
    }

    private String formatTime(LocalDateTime endTime) {
        if (endTime == null) return "N/A";
        long secs = ChronoUnit.SECONDS.between(LocalDateTime.now(), endTime);
        if (secs <= 0) return "ĐÃ KẾT THÚC";
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        return String.format("%02d:%02d:%02d (%s)", h, m, s, endTime.format(FMT));
    }

    private void updateCountdowns() {
        // Re-render countdown mỗi giây mà không gửi thêm request
        rows.forEach(row -> row.endTime = formatTime(row.auction != null ? row.auction.getEndTime() : null));
        tableAuctions.refresh();
    }

    private void updateStats() {
        long leading = rows.stream().filter(r -> r.statusLabel.contains("DẪN ĐẦU")).count();
        long outbid  = rows.stream().filter(r -> r.statusLabel.contains("BỊ VƯỢT")).count();
        long won     = rows.stream().filter(r -> r.statusLabel.contains("THẮNG")).count();
        lblLeading.setText(String.valueOf(leading));
        lblOutbid.setText(String.valueOf(outbid));
        lblWon.setText(String.valueOf(won));
    }

    private void openDetail(Auction auction) {
        try {
            cleanup();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/AuctionDetail.fxml"));
            Parent root = loader.load();
            AuctionDetailController ctrl = loader.getController();
            ctrl.setAuctionData(auction);
            Stage stage = (Stage) rootPane.getScene().getWindow();
            double _w = stage.getWidth();
            double _h = stage.getHeight();
            double _x = stage.getX();
            double _y = stage.getY();
            stage.setScene(new Scene(root));
            stage.setWidth(_w);
            stage.setHeight(_h);
            stage.setX(_x);
            stage.setY(_y);
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    public void onBackClick(ActionEvent event) {
        cleanup();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/BidderDashboard.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            double _w2 = stage.getWidth();
            double _h2 = stage.getHeight();
            double _x2 = stage.getX();
            double _y2 = stage.getY();
            stage.setScene(new Scene(root));
            stage.setWidth(_w2);
            stage.setHeight(_h2);
            stage.setX(_x2);
            stage.setY(_y2);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void cleanup() {
        if (refreshTimer != null) refreshTimer.stop();
        NetworkClient.getInstance().removeEventListener(LISTENER_KEY);
    }

    /** Inner class đại diện 1 hàng trong bảng */
    public static class MyAuctionRow {
        public Auction auction;
        public String itemName;
        public String statusLabel;
        public String myBid;
        public String currentBid;
        public String endTime;
    }
}
