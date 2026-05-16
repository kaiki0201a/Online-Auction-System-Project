package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.BidTransaction;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;

// 🛠️ IMPORT HELPER VÀO ĐÂY
import com.auction.utils.PriceChartHelper;

import java.util.List;

public class AuctionDetailController {
    @FXML private TextField txtBidAmount; 
    @FXML private Label lblProductName;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblSellerName, lblTimeLeft;
    @FXML private Label lblMessage;

    @FXML private LineChart<String, Number> priceChart;
    private XYChart.Series<String, Number> priceSeries;
    
    private Auction currentAuction;
    private Bidder currentUser;

    public void setAuctionData(Auction auction, Bidder user) {
        this.currentAuction = auction;
        this.currentUser = user;

        updateUI();

        // 🚀 CODE MỚI 1: GỌI HELPER ĐỂ VẼ LẠI TOÀN BỘ LỊCH SỬ TỪ ĐẦU (NẾU CÓ)
        // Helper này đã có sẵn lớp giáp chống Null Pointer cực kỳ an toàn!
        this.priceSeries = PriceChartHelper.buildHistoricalChart(priceChart, currentAuction.getBidHistory());

        // LẮNG NGHE TỪ SERVER 
        NetworkClient.getInstance().setOnResponseReceived(response -> {
            javafx.application.Platform.runLater(() -> {

                if ("UPDATE_AUCTION".equals(response.getMessage()) && response.getData() instanceof Auction) {
                    Auction updatedAuction = (Auction) response.getData();

                    if (updatedAuction.getAuctionId().equals(this.currentAuction.getAuctionId())) {
                        this.currentAuction = updatedAuction;
                        updateUI();

                        // 🚀 CODE MỚI 2: TÌM GIAO DỊCH MỚI NHẤT VÀ NHỜ HELPER VẼ LÊN BIỂU ĐỒ REAL-TIME
                        List<BidTransaction> history = updatedAuction.getBidHistory();
                        if (history != null && !history.isEmpty()) {
                            BidTransaction latestTx = history.get(history.size() - 1); // Lấy cục dữ liệu mới nhất
                            PriceChartHelper.updateChartRealTime(priceSeries, latestTx);
                        }

                        if (!updatedAuction.getHighestBidder().getUserName().equals(this.currentUser.getUserName())) {
                            lblMessage.setText("🔥 Báo động: Ai đó vừa trả giá cao hơn bạn!");
                            lblMessage.setStyle("-fx-text-fill: red;");
                        }
                    }
                } else if ("Đặt giá thành công!".equals(response.getMessage())) {
                    lblMessage.setText("✅ " + response.getMessage());
                    lblMessage.setStyle("-fx-text-fill: green;");
                    txtBidAmount.clear();
                } else if (response.getStatus() == com.auction.protocol.StatusType.ERROR) {
                    showError("Từ chối đặt giá", response.getMessage());
                }
            });
        });
    }

    private void updateUI() {
        lblProductName.setText(currentAuction.getItem().getNameItem());
        lblSellerName.setText(currentAuction.getSeller().getUserName());
        lblCurrentPrice.setText("$" + currentAuction.getCurrentHighestBid());
        lblTimeLeft.setText(currentAuction.getEndTime().toString());
    }

    @FXML
    public void onBidButtonClick() {
        try {
            double amount = Double.parseDouble(txtBidAmount.getText());
            com.auction.protocol.BidPayload payload = new com.auction.protocol.BidPayload(
                    currentAuction.getAuctionId(),
                    currentUser.getUserName(),
                    amount
            );

            NetworkClient.getInstance().sendRequest(new Request(ActionType.PLACE_BID, payload));
            txtBidAmount.clear();
            lblMessage.setText("Đang gửi yêu cầu..."); 
        } catch (NumberFormatException e) {
            showError("Lỗi", "Vui lòng nhập số tiền hợp lệ!");
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    public void onBackButtonClick(javafx.event.ActionEvent event) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/auction/view/Dashboard.fxml"));
            javafx.scene.Parent root = loader.load();

            DashboardController dashboardController = loader.getController();
            dashboardController.setUser(currentUser);

            javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new javafx.scene.Scene(root));
        } catch (Exception e) {
            e.printStackTrace();
            showError("Lỗi", "Không thể quay lại màn hình chính.");
        }
    }
}