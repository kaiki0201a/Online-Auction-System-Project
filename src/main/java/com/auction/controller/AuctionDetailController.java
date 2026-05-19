package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.BidTransaction;
import com.auction.model.User;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.utils.AppContext;
import com.auction.utils.PriceChartHelper;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class AuctionDetailController {
    @FXML private TextField txtBidAmount;
    @FXML private Label lblProductName, lblCurrentPrice, lblSellerName, lblTimeLeft, lblMessage;
    @FXML private LineChart<String, Number> priceChart;

    private XYChart.Series<String, Number> priceSeries;
    private Auction currentAuction;
    private Bidder currentUser;
    private Timeline countdownTimeline;

    public void setAuctionData(Auction auction) {
        this.currentAuction = auction;

        // TỰ LẤY USER TỪ KÉT SẮT
        User sessionUser = AppContext.getCurrentUser();
        if (sessionUser instanceof Bidder) {
            this.currentUser = (Bidder) sessionUser;
        } else {
            this.currentUser = null;
            txtBidAmount.setDisable(true); // Nếu không phải người mua thì cấm đặt giá
        }

        this.priceSeries = PriceChartHelper.buildHistoricalChart(priceChart, currentAuction.getBidHistory());
        startCountdown();
        updateUI();

        // LẮNG NGHE MẠNG
        NetworkClient.getInstance().setOnResponseReceived(response -> {
            Platform.runLater(() -> {
                if ("UPDATE_AUCTION".equals(response.getMessage()) && response.getData() instanceof Auction) {
                    Auction updatedAuction = (Auction) response.getData();

                    if (updatedAuction.getAuctionId().equals(this.currentAuction.getAuctionId())) {
                        if (updatedAuction.getEndTime().isAfter(this.currentAuction.getEndTime())) {
                            lblMessage.setText("🛡️ Hệ thống vừa gia hạn thêm thời gian đấu giá!");
                            lblMessage.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
                        }

                        this.currentAuction = updatedAuction;
                        updateUI();

                        List<BidTransaction> history = updatedAuction.getBidHistory();
                        if (history != null && !history.isEmpty()) {
                            BidTransaction latestTx = history.get(history.size() - 1);
                            PriceChartHelper.updateChartRealTime(priceSeries, latestTx);
                        }

                        if (this.currentUser != null && updatedAuction.getHighestBidder() != null &&
                                !updatedAuction.getHighestBidder().getUserName().equals(this.currentUser.getUserName())) {
                            lblMessage.setText("🔥 Cảnh báo: Ai đó vừa trả giá cao hơn bạn!");
                            lblMessage.setStyle("-fx-text-fill: #e74c3c;");
                        }
                    }
                }
                else if ("Đặt giá thành công!".equals(response.getMessage())) {
                    lblMessage.setText("✅ Đặt giá thành công!");
                    lblMessage.setStyle("-fx-text-fill: #27ae60;");
                }
                else if (response.getStatus() == com.auction.protocol.StatusType.ERROR) {
                    showError("Từ chối đặt giá", response.getMessage());
                }
            });
        });
    }

    private void startCountdown() {
        if (countdownTimeline != null) countdownTimeline.stop();

        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long secondsLeft = ChronoUnit.SECONDS.between(LocalDateTime.now(), currentAuction.getEndTime());
            if (secondsLeft <= 0) {
                lblTimeLeft.setText("ĐÃ KẾT THÚC");
                lblTimeLeft.setStyle("-fx-text-fill: gray;");
                countdownTimeline.stop();
            } else {
                long h = secondsLeft / 3600;
                long m = (secondsLeft % 3600) / 60;
                long s = secondsLeft % 60;
                lblTimeLeft.setText(String.format("%02d:%02d:%02d", h, m, s));
                if (secondsLeft < 60) lblTimeLeft.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            }
        }));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void updateUI() {
        lblProductName.setText(currentAuction.getItem().getNameItem());
        lblSellerName.setText(currentAuction.getSeller().getUserName());
        lblCurrentPrice.setText("$" + currentAuction.getCurrentHighestBid());
    }

    @FXML
    public void onBidButtonClick() {
        if (currentUser == null) return;
        try {
            double amount = Double.parseDouble(txtBidAmount.getText());
            com.auction.protocol.BidPayload payload = new com.auction.protocol.BidPayload(
                    currentAuction.getAuctionId(),
                    currentUser.getUserName(),
                    amount
            );
            NetworkClient.getInstance().sendRequest(new Request(ActionType.PLACE_BID, payload));
            lblMessage.setText("🚀 Đang gửi giá thầu...");
        } catch (NumberFormatException e) {
            showError("Lỗi", "Vui lòng nhập số tiền hợp lệ!");
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    public void onBackButtonClick(javafx.event.ActionEvent event) {
        // NGĂN RÒ RỈ BỘ NHỚ KHI THOÁT KHỎI MÀN HÌNH NÀY
        if (countdownTimeline != null) countdownTimeline.stop();
        NetworkClient.getInstance().setOnResponseReceived(null);

        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/auction/view/Dashboard.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new javafx.scene.Scene(root));
        } catch (Exception e) {
            showError("Lỗi", "Không thể quay lại: " + e.getMessage());
        }
    }
}