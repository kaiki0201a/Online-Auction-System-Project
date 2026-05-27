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
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MyBidsController – Màn hình "Phiên đấu giá đang tham gia" cho Bidder.
 *
 * Hiển thị tất cả các phiên mà bidder đã đặt giá ít nhất một lần,
 * cùng trạng thái hiện tại (đang dẫn đầu / bị vượt / đã thắng / đã thua).
 */
public class MyBidsController {

    @FXML private StackPane rootPane;
    @FXML private Label lblBalance;
    @FXML private Label lblSubtitle;
    @FXML private Label lblCountActive;
    @FXML private Label lblCountLeading;
    @FXML private Label lblCountOutbid;
    @FXML private Label lblCountWon;
    @FXML private ComboBox<String> comboFilter;
    @FXML private VBox bidsContainer;

    private static final String LISTENER_KEY = "myBids";
    private Bidder currentUser;
    private List<Auction> allAuctions;

    private static final String[] FILTER_OPTIONS = {
        "Tất cả phiên tham gia",
        "Đang dẫn đầu 🏆",
        "Bị vượt giá ⚠️",
        "Phiên đang chạy 🔴",
        "Đã kết thúc ✅"
    };

    @FXML
    public void initialize() {
        currentUser = (Bidder) AppContext.getCurrentUser();

        // Cài filter combo
        comboFilter.setItems(FXCollections.observableArrayList(FILTER_OPTIONS));
        comboFilter.setValue(FILTER_OPTIONS[0]);
        comboFilter.setOnAction(e -> renderBids());

        updateBalance();

        // Lắng nghe response từ server
        NetworkClient.getInstance().addEventListener(LISTENER_KEY, response -> {
            Platform.runLater(() -> {
                if (response.getStatus() == StatusType.SUCCESS
                        && response.getData() instanceof List<?> list
                        && !list.isEmpty() && list.get(0) instanceof Auction) {
                    @SuppressWarnings("unchecked")
                    List<Auction> auctions = (List<Auction>) list;
                    allAuctions = auctions;
                    renderBids();
                }

                // Cập nhật balance nếu nhận được
                if (response.getData() instanceof Double newBalance) {
                    currentUser.setBalance(newBalance);
                    updateBalance();
                }
            });
        });

        // Yêu cầu server gửi danh sách phiên
        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
    }

    private void updateBalance() {
        if (lblBalance != null)
            lblBalance.setText(CurrencyFormatter.format(currentUser.getBalance()));
    }

    /**
     * Render danh sách phiên mà bidder đã từng tham gia.
     * "Tham gia" = có ít nhất một BidTransaction với bidder hiện tại.
     */
    private void renderBids() {
        if (allAuctions == null || bidsContainer == null) return;

        String filter = comboFilter.getValue();

        // Lấy tất cả phiên bidder đã bid
        List<Auction> myAuctions = allAuctions.stream()
            .filter(a -> bidderParticipated(a))
            .collect(Collectors.toList());

        // Thống kê
        long active  = myAuctions.stream()
            .filter(a -> a.getStatus() == AuctionStatus.RUNNING || a.getStatus() == AuctionStatus.OPEN).count();
        long leading = myAuctions.stream()
            .filter(a -> isLeading(a) && (a.getStatus() == AuctionStatus.RUNNING || a.getStatus() == AuctionStatus.OPEN)).count();
        long outbid  = myAuctions.stream()
            .filter(a -> !isLeading(a) && (a.getStatus() == AuctionStatus.RUNNING || a.getStatus() == AuctionStatus.OPEN)).count();
        long won     = myAuctions.stream()
            .filter(a -> isLeading(a) && (a.getStatus() == AuctionStatus.FINISHED || a.getStatus() == AuctionStatus.PAID)).count();

        if (lblCountActive  != null) lblCountActive.setText(String.valueOf(active));
        if (lblCountLeading != null) lblCountLeading.setText(String.valueOf(leading));
        if (lblCountOutbid  != null) lblCountOutbid.setText(String.valueOf(outbid));
        if (lblCountWon     != null) lblCountWon.setText(String.valueOf(won));

        // Filter
        List<Auction> filtered;
        filtered = switch (filter) {
            case "Đang dẫn đầu 🏆"   -> myAuctions.stream()
                .filter(a -> isLeading(a) && (a.getStatus() == AuctionStatus.RUNNING || a.getStatus() == AuctionStatus.OPEN))
                .collect(Collectors.toList());
            case "Bị vượt giá ⚠️"   -> myAuctions.stream()
                .filter(a -> !isLeading(a) && (a.getStatus() == AuctionStatus.RUNNING || a.getStatus() == AuctionStatus.OPEN))
                .collect(Collectors.toList());
            case "Phiên đang chạy 🔴" -> myAuctions.stream()
                .filter(a -> a.getStatus() == AuctionStatus.RUNNING || a.getStatus() == AuctionStatus.OPEN)
                .collect(Collectors.toList());
            case "Đã kết thúc ✅"     -> myAuctions.stream()
                .filter(a -> a.getStatus() == AuctionStatus.FINISHED || a.getStatus() == AuctionStatus.PAID
                    || a.getStatus() == AuctionStatus.CANCELED)
                .collect(Collectors.toList());
            default                    -> myAuctions;
        };

        bidsContainer.getChildren().clear();

        if (filtered.isEmpty()) {
            Label empty = new Label("Không tìm thấy phiên đấu giá nào trong danh mục này.");
            empty.setStyle("-fx-text-fill: #555; -fx-font-size: 14px; -fx-padding: 30 0;");
            bidsContainer.getChildren().add(empty);
            return;
        }

        for (Auction a : filtered) {
            bidsContainer.getChildren().add(buildCard(a));
        }
    }

    /** Kiểm tra bidder hiện tại có đặt giá trong phiên này chưa. */
    private boolean bidderParticipated(Auction a) {
        if (a.getBidHistory() == null) return false;
        return a.getBidHistory().stream()
            .anyMatch(tx -> tx.getBidder().getUserName().equals(currentUser.getUserName()));
    }

    /** Kiểm tra bidder đang dẫn đầu. */
    private boolean isLeading(Auction a) {
        return a.getHighestBidder() != null
            && a.getHighestBidder().getUserName().equals(currentUser.getUserName());
    }

    private Node buildCard(Auction auction) {
        HBox card = new HBox(16);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: #141414; -fx-border-color: #2A2A2A; -fx-border-radius: 10; "
            + "-fx-background-radius: 10; -fx-padding: 16 20;");

        // Loại sản phẩm icon
        String icon = switch (auction.getItem().getClass().getSimpleName()) {
            case "Art" -> "🎨";
            case "Electronics" -> "💻";
            case "Vehicle" -> "🚗";
            default -> "📦";
        };
        Label lblIcon = new Label(icon);
        lblIcon.setStyle("-fx-font-size: 28px;");
        lblIcon.setMinWidth(40);

        // Thông tin chính
        VBox info = new VBox(5);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label lblTitle = new Label(auction.getItem().getNameItem());
        lblTitle.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 15px;");
        lblTitle.setWrapText(true);

        HBox metaRow = new HBox(14);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        Label lblSeller = new Label("Người bán: " + auction.getSeller().getUserName());
        lblSeller.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        Label lblId = new Label("LÔ " + auction.getAuctionId().substring(0, 8).toUpperCase());
        lblId.setStyle("-fx-text-fill: #444; -fx-font-size: 10px;");

        // Timer
        String timeStr = buildTimerText(auction);
        Label lblTimer = new Label(timeStr);
        lblTimer.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");

        metaRow.getChildren().addAll(lblSeller, lblId, lblTimer);
        info.getChildren().addAll(lblTitle, metaRow);

        // Giá section
        VBox priceBox = new VBox(4);
        priceBox.setAlignment(Pos.CENTER_RIGHT);

        Label lpLabel = new Label("GIÁ HIỆN TẠI");
        lpLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 9px; -fx-font-weight: bold;");
        Label lpValue = new Label(CurrencyFormatter.format(auction.getCurrentHighestBid()));
        lpValue.setStyle("-fx-text-fill: #F5C518; -fx-font-weight: bold; -fx-font-size: 16px;");

        priceBox.getChildren().addAll(lpLabel, lpValue);

        // Badge trạng thái
        Label badge = buildStatusBadge(auction);

        // Nút xem chi tiết
        Button btnView = new Button("Xem →");
        btnView.setStyle("-fx-background-color: transparent; -fx-text-fill: #F5C518; "
            + "-fx-border-color: #F5C518; -fx-border-radius: 6; -fx-background-radius: 6; "
            + "-fx-cursor: hand; -fx-padding: 6 14; -fx-font-size: 12px;");
        boolean canBid = auction.getStatus() == AuctionStatus.RUNNING
            || auction.getStatus() == AuctionStatus.OPEN;
        if (!canBid) {
            btnView.setDisable(true);
            btnView.setStyle("-fx-background-color: transparent; -fx-text-fill: #444; "
                + "-fx-border-color: #333; -fx-border-radius: 6; -fx-background-radius: 6; "
                + "-fx-padding: 6 14; -fx-font-size: 12px;");
        } else {
            btnView.setOnAction(e -> openDetail(auction));
        }

        VBox rightBox = new VBox(8);
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        rightBox.getChildren().addAll(badge, priceBox, btnView);

        card.getChildren().addAll(lblIcon, info, rightBox);

        // Hover effect
        card.setOnMouseEntered(e ->
            card.setStyle(card.getStyle().replace("#141414", "#1a1a1a")));
        card.setOnMouseExited(e ->
            card.setStyle(card.getStyle().replace("#1a1a1a", "#141414")));

        return card;
    }

    private Label buildStatusBadge(Auction auction) {
        boolean leading = isLeading(auction);
        boolean finished = auction.getStatus() == AuctionStatus.FINISHED
            || auction.getStatus() == AuctionStatus.PAID;
        boolean canceled = auction.getStatus() == AuctionStatus.CANCELED;

        String text;
        String color;
        String bg;

        if (canceled) {
            text = "🚫 ĐÃ HỦY";
            color = "#e74c3c"; bg = "rgba(231,76,60,0.15)";
        } else if (finished) {
            text = leading ? "✅ ĐÃ THẮNG" : "❌ ĐÃ THUA";
            color = leading ? "#27ae60" : "#e74c3c";
            bg    = leading ? "rgba(39,174,96,0.15)" : "rgba(231,76,60,0.15)";
        } else {
            text = leading ? "🏆 ĐANG DẪN ĐẦU" : "⚠️ BỊ VƯỢT GIÁ";
            color = leading ? "#F5C518" : "#e74c3c";
            bg    = leading ? "rgba(245,197,24,0.15)" : "rgba(231,76,60,0.15)";
        }

        Label badge = new Label(text);
        badge.setStyle(String.format(
            "-fx-text-fill: %s; -fx-background-color: %s; "
            + "-fx-background-radius: 6; -fx-padding: 4 10; -fx-font-size: 11px; -fx-font-weight: bold;",
            color, bg));
        return badge;
    }

    private String buildTimerText(Auction auction) {
        if (auction.getStatus() == AuctionStatus.FINISHED
                || auction.getStatus() == AuctionStatus.PAID) return "⏹ Đã kết thúc";
        if (auction.getStatus() == AuctionStatus.CANCELED) return "🚫 Đã hủy";
        if (auction.getEndTime() == null) return "";
        long secs = ChronoUnit.SECONDS.between(LocalDateTime.now(), auction.getEndTime());
        if (secs <= 0) return "⏹ Hết giờ";
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        return String.format("⏱ %02d:%02d:%02d còn lại", h, m, s);
    }

    private void openDetail(Auction auction) {
        try {
            NetworkClient.getInstance().removeEventListener(LISTENER_KEY);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/AuctionDetail.fxml"));
            Parent root = loader.load();
            AuctionDetailController ctrl = loader.getController();
            ctrl.setAuctionData(auction);
            Stage stage = (Stage) rootPane.getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 820));
            stage.centerOnScreen();
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML public void onRefreshClick(ActionEvent event) {
        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
    }

    @FXML public void onBackClick(ActionEvent event) {
        NetworkClient.getInstance().removeEventListener(LISTENER_KEY);
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Settings.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 600, 530));
        } catch (IOException e) { e.printStackTrace(); }
    }
}
