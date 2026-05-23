package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.*;
import com.auction.protocol.ActionType;
import com.auction.protocol.BidPayload;
import com.auction.protocol.Request;
import com.auction.protocol.StatusType;
import com.auction.utils.AppContext;
import com.auction.utils.CurrencyFormatter;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
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
import java.util.List;

public class AuctionDetailController {

    // Cột trái: ảnh + mô tả
    @FXML private StackPane productImagePane;
    @FXML private ImageView imgProduct;
    @FXML private Label lblCategoryIcon;
    @FXML private Label lblCategoryLabel;
    @FXML private Label lblStatusBadge;
    @FXML private Label lblProductName;
    @FXML private Label lblItemCategory;
    @FXML private Label lblStartingPrice;
    @FXML private Label lblSellerName;
    @FXML private Label lblDescription;
    @FXML private Label lblMessage;

    // Cột phải: đặt giá
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblTimeLeft;
    @FXML private Label lblBidCount;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnBid;
    @FXML private Button btnQuick10;
    @FXML private Button btnQuick50;
    @FXML private Button btnQuick100;

    // Lịch sử bid
    @FXML private VBox bidHistoryContainer;

    private Auction currentAuction;
    private User sessionUser;
    private boolean canBid = false;
    private Timeline countdownTimeline;
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM");

    @FXML
    public void initialize() {
        if (txtBidAmount != null) {
            txtBidAmount.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("\\d*(\\.\\d*)?") ? change : null));
        }
    }

    public void setAuctionData(Auction auction) {
        this.currentAuction = auction;
        this.sessionUser = AppContext.getCurrentUser();

        // Bidder = có thể đặt giá; Seller/Admin = chỉ xem; Seller trùng owner = không đặt
        canBid = sessionUser instanceof Bidder;
        if (canBid && auction.getStatus() == AuctionStatus.FINISHED) canBid = false;

        if (btnBid != null) {
            btnBid.setDisable(!canBid);
            if (!canBid) {
                btnBid.setText(sessionUser instanceof Seller ? "👁 Chỉ Xem" : "ĐÃ KẾT THÚC");
                btnBid.setStyle(btnBid.getStyle() + "-fx-background-color: #2A2A2A; -fx-text-fill: #666;");
            }
        }
        if (txtBidAmount != null) txtBidAmount.setDisable(!canBid);
        if (btnQuick10 != null) btnQuick10.setDisable(!canBid);
        if (btnQuick50 != null) btnQuick50.setDisable(!canBid);
        if (btnQuick100 != null) btnQuick100.setDisable(!canBid);

        loadProductImage();
        updateUI();
        startCountdown();
        renderBidHistory();
        registerNetworkListener();
    }

    // ─── Ảnh sản phẩm ────────────────────────────────────────────────────────

    private void loadProductImage() {
        if (imgProduct == null) return;
        String imgPath = currentAuction.getItem().getImagePath();
        if (imgPath != null && !imgPath.isEmpty()) {
            try {
                Image img = new Image("file:" + imgPath, 700, 420, true, true);
                imgProduct.setImage(img);
                imgProduct.setVisible(true);
                if (lblCategoryIcon != null) lblCategoryIcon.setVisible(false);
                if (lblCategoryLabel != null) lblCategoryLabel.setVisible(false);
            } catch (Exception ignored) {
                showCategoryPlaceholder();
            }
        } else {
            showCategoryPlaceholder();
        }
    }

    private void showCategoryPlaceholder() {
        String cat = currentAuction.getItem().getClass().getSimpleName();
        String icon = switch (cat) {
            case "Art" -> "🎨";
            case "Electronics" -> "💻";
            case "Vehicle" -> "🚗";
            default -> "📦";
        };
        if (lblCategoryIcon != null) { lblCategoryIcon.setText(icon); lblCategoryIcon.setVisible(true); }
        if (lblCategoryLabel != null) { lblCategoryLabel.setText(cat); lblCategoryLabel.setVisible(true); }
        if (imgProduct != null) imgProduct.setVisible(false);
    }

    // ─── Cập nhật UI ──────────────────────────────────────────────────────────

    private void updateUI() {
        Item item = currentAuction.getItem();

        if (lblProductName != null) lblProductName.setText(item.getNameItem());
        if (lblCurrentPrice != null) lblCurrentPrice.setText(CurrencyFormatter.format(currentAuction.getCurrentHighestBid()));
        if (lblSellerName != null) lblSellerName.setText(currentAuction.getSeller().getUserName());
        if (lblItemCategory != null) lblItemCategory.setText(item.getClass().getSimpleName());
        if (lblStartingPrice != null) lblStartingPrice.setText(CurrencyFormatter.format(item.getStartingPrice()));
        if (lblDescription != null) {
            String desc = item.getDescriptionItem();
            lblDescription.setText(desc != null && !desc.isEmpty() ? desc : "Chưa có mô tả chi tiết.");
        }
        if (lblBidCount != null) lblBidCount.setText(String.valueOf(currentAuction.getBidHistory().size()));

        // Badge trạng thái
        if (lblStatusBadge != null) {
            if (currentAuction.getStatus() == AuctionStatus.FINISHED || currentAuction.getStatus() == AuctionStatus.PAID) {
                lblStatusBadge.setText("● ĐÃ KẾT THÚC");
                lblStatusBadge.setStyle("-fx-background-color: rgba(39,174,96,0.3); -fx-text-fill: #27ae60; -fx-padding: 4 12; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;");
            } else {
                lblStatusBadge.setText("● ĐANG DIỄN RA");
                lblStatusBadge.setStyle("-fx-background-color: rgba(245,197,24,0.25); -fx-text-fill: #F5C518; -fx-padding: 4 12; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;");
            }
        }
    }

    // ─── Countdown ────────────────────────────────────────────────────────────

    private void startCountdown() {
        if (countdownTimeline != null) countdownTimeline.stop();
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (lblTimeLeft == null) return;
            long secs = ChronoUnit.SECONDS.between(LocalDateTime.now(), currentAuction.getEndTime());
            if (secs <= 0) {
                lblTimeLeft.setText("ĐÃ KẾT THÚC");
                lblTimeLeft.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 18px;");
                countdownTimeline.stop();
            } else {
                long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
                lblTimeLeft.setText(String.format("%02d:%02d:%02d", h, m, s));
                lblTimeLeft.setStyle(secs < 60 ?
                    "-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 22px; -fx-font-family: monospace;" :
                    "-fx-text-fill: #F5C518; -fx-font-weight: bold; -fx-font-size: 22px; -fx-font-family: monospace;");
            }
        }));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    // ─── Lịch sử bid ─────────────────────────────────────────────────────────

    private void renderBidHistory() {
        if (bidHistoryContainer == null) return;
        bidHistoryContainer.getChildren().clear();

        List<BidTransaction> history = currentAuction.getBidHistory();
        if (history.isEmpty()) {
            Label empty = new Label("Chưa có ai đặt giá. Hãy là người đầu tiên!");
            empty.setStyle("-fx-text-fill: #555; -fx-font-style: italic; -fx-font-size: 12px;");
            bidHistoryContainer.getChildren().add(empty);
            return;
        }

        // Hiện từ mới nhất đến cũ nhất
        for (int i = history.size() - 1; i >= 0; i--) {
            BidTransaction tx = history.get(i);
            boolean isTop = (i == history.size() - 1);
            bidHistoryContainer.getChildren().add(buildHistoryRow(tx, isTop));
        }
    }

    private HBox buildHistoryRow(BidTransaction tx, boolean isTop) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setStyle(isTop ?
            "-fx-background-color: rgba(245,197,24,0.1); -fx-border-color: #F5C518; -fx-border-radius: 6; -fx-background-radius: 6; -fx-border-width: 1;" :
            "-fx-background-color: #1A1A1A; -fx-border-color: #2A2A2A; -fx-border-radius: 6; -fx-background-radius: 6; -fx-border-width: 1;");

        // Icon vị trí
        Label rankIcon = new Label(isTop ? "🏆" : "•");
        rankIcon.setStyle("-fx-font-size: " + (isTop ? "16" : "20") + "px; -fx-padding: 0 8 0 0;");
        rankIcon.setStyle("-fx-font-size: 14px; -fx-padding: 0 8 0 0; -fx-text-fill: " + (isTop ? "#F5C518" : "#555") + ";");

        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label bidder = new Label(tx.getBidder().getUserName());
        bidder.setStyle("-fx-text-fill: " + (isTop ? "#FFFFFF" : "#A0A0A0") + "; -fx-font-size: 13px; -fx-font-weight: " + (isTop ? "bold" : "normal") + ";");
        Label time = new Label(tx.getTimestamp().format(DTF));
        time.setStyle("-fx-text-fill: #444; -fx-font-size: 10px;");
        info.getChildren().addAll(bidder, time);

        Label amount = new Label(CurrencyFormatter.format(tx.getBidAmount()));
        amount.setStyle("-fx-text-fill: " + (isTop ? "#F5C518" : "#666") + "; -fx-font-weight: bold; -fx-font-size: " + (isTop ? "15" : "13") + "px;");

        row.getChildren().addAll(rankIcon, info, amount);
        return row;
    }

    // ─── Đặt giá ─────────────────────────────────────────────────────────────

    @FXML public void onQuickBid10() { addQuickAmount(10_000); }
    @FXML public void onQuickBid50() { addQuickAmount(50_000); }
    @FXML public void onQuickBid100() { addQuickAmount(100_000); }

    private void addQuickAmount(double increment) {
        if (txtBidAmount == null) return;
        double current = currentAuction.getCurrentHighestBid();
        double newBid = current + increment;
        // Nếu người dùng đã nhập, cộng thêm
        try {
            if (!txtBidAmount.getText().isEmpty()) {
                newBid = Double.parseDouble(txtBidAmount.getText()) + increment;
            }
        } catch (NumberFormatException ignored) {}
        txtBidAmount.setText(String.valueOf((long) newBid));
    }

    @FXML
    public void onBidButtonClick() {
        if (!canBid) {
            setMessage("⛔ Chỉ người mua (Bidder) mới có thể đặt giá!", "#e74c3c");
            return;
        }
        if (currentAuction.getStatus() == AuctionStatus.FINISHED) {
            setMessage("⛔ Phiên đấu giá đã kết thúc!", "#e74c3c");
            return;
        }

        String text = txtBidAmount != null ? txtBidAmount.getText().trim() : "";
        if (text.isEmpty()) {
            setMessage("⚠️ Vui lòng nhập số tiền đặt giá!", "#f39c12");
            return;
        }

        try {
            double amount = Double.parseDouble(text);
            if (amount <= currentAuction.getCurrentHighestBid()) {
                setMessage("⚠️ Giá phải cao hơn giá hiện tại: " + CurrencyFormatter.format(currentAuction.getCurrentHighestBid()), "#f39c12");
                return;
            }

            Bidder bidder = (Bidder) sessionUser;
            if (amount > bidder.getBalance()) {
                setMessage("⚠️ Số dư không đủ! Số dư: " + CurrencyFormatter.format(bidder.getBalance()), "#e74c3c");
                return;
            }

            BidPayload payload = new BidPayload(currentAuction.getAuctionId(), bidder.getUserName(), amount);
            NetworkClient.getInstance().sendRequest(new Request(ActionType.PLACE_BID, payload));
            setMessage("🚀 Đang gửi giá thầu...", "#A0A0A0");
            if (btnBid != null) btnBid.setDisable(true);

        } catch (NumberFormatException ex) {
            setMessage("❌ Số tiền không hợp lệ!", "#e74c3c");
        }
    }

    // ─── Network listener ─────────────────────────────────────────────────────

    private void registerNetworkListener() {
        NetworkClient.getInstance().setOnResponseReceived(response -> {
            Platform.runLater(() -> {
                if ("UPDATE_AUCTION".equals(response.getMessage()) && response.getData() instanceof Auction updated) {
                    if (updated.getAuctionId().equals(currentAuction.getAuctionId())) {
                        boolean wasExtended = updated.getEndTime().isAfter(currentAuction.getEndTime());
                        currentAuction = updated;
                        updateUI();
                        renderBidHistory();
                        if (wasExtended) setMessage("⏱️ Hệ thống vừa gia hạn thêm thời gian!", "#f39c12");

                        // Kiểm tra bị vượt giá
                        if (canBid && updated.getHighestBidder() != null &&
                                !updated.getHighestBidder().getUserName().equals(sessionUser.getUserName())) {
                            setMessage("🔥 Ai đó vừa trả giá cao hơn bạn!", "#e74c3c");
                        }
                    }
                }

                if (response.getStatus() == StatusType.SUCCESS && "Đặt giá thành công!".equals(response.getMessage())) {
                    setMessage("✅ Đặt giá thành công!", "#27ae60");
                    if (txtBidAmount != null) txtBidAmount.clear();
                    if (btnBid != null) btnBid.setDisable(false);
                }

                if (response.getStatus() == StatusType.ERROR) {
                    setMessage("❌ " + response.getMessage(), "#e74c3c");
                    if (btnBid != null) btnBid.setDisable(false);
                }
            });
        });
    }

    private void setMessage(String text, String color) {
        if (lblMessage != null) {
            lblMessage.setText(text);
            lblMessage.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13px; -fx-font-weight: bold;");
        }
    }

    // ─── Back ────────────────────────────────────────────────────────────────

    @FXML
    public void onBackButtonClick(javafx.event.ActionEvent event) {
        if (countdownTimeline != null) countdownTimeline.stop();
        NetworkClient.getInstance().removeOnResponseReceived();

        try {
            String path;
            if (sessionUser instanceof Admin) {
                path = "/com/auction/view/AdminDashboard.fxml";
            } else if (sessionUser instanceof Seller) {
                path = "/com/auction/view/SellerDashboard.fxml";
            } else {
                path = "/com/auction/view/BidderDashboard.fxml";
            }

            Parent root = FXMLLoader.load(getClass().getResource(path));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 800));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}