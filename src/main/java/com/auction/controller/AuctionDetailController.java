package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.BidTransaction;
import com.auction.exception.AuctionException;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.StatusType;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
public class AuctionDetailController{
    @FXML private TextField txtBidAmount; // Ô nhập số tiền
    @FXML private Label lblProductName;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblSellerName, lblTimeLeft;
    @FXML private  Label lblMessage;

    // BIẾN THÊM MỚI CHO BIỂU ĐỒ
    @FXML private LineChart<String, Number> priceChart;
    private XYChart.Series<String, Number> priceSeries;
    // Hai biến này cực kỳ quan trọng để truyền vào Constructor của bạn
    private Auction currentAuction;
    private Bidder currentUser;

    // Hàm này dùng để nhận dữ liệu từ màn hình Dashboard truyền sang
    // Hàm này được gọi khi từ Dashboard bấm đúp chuyển sang
    public void setAuctionData(Auction auction, Bidder user) {
        this.currentAuction = auction;
        this.currentUser = user;

        updateUI();

        // --- CODE MỚI 1: KHỞI TẠO BIỂU ĐỒ ---
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Lịch sử giá đấu");
        priceChart.getData().clear(); // Xóa dữ liệu cũ (nếu có)
        priceChart.getData().add(priceSeries);

        // Vẽ điểm giá khởi điểm
        String timeNow = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        priceSeries.getData().add(new XYChart.Data<>(timeNow + "\n(Khởi điểm)", currentAuction.getCurrentHighestBid()));
        // ------------------------------------

        // LẮNG NGHE TỪ SERVER NHƯ CŨ
        NetworkClient.getInstance().setOnResponseReceived(response -> {
            javafx.application.Platform.runLater(() -> {

                if ("UPDATE_AUCTION".equals(response.getMessage()) && response.getData() instanceof Auction) {
                    Auction updatedAuction = (Auction) response.getData();

                    if (updatedAuction.getAuctionId().equals(this.currentAuction.getAuctionId())) {
                        this.currentAuction = updatedAuction;
                        updateUI();

                        // --- CODE MỚI 2: VẼ THÊM ĐIỂM KHI CÓ NGƯỜI ĐẶT GIÁ ---
                        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                        String bidderName = updatedAuction.getHighestBidder().getUserName();

                        // Thêm điểm giá mới vào đồ thị
                        XYChart.Data<String, Number> newPoint = new XYChart.Data<>(time + "\n(" + bidderName + ")", updatedAuction.getCurrentHighestBid());
                        priceSeries.getData().add(newPoint);
                        // -----------------------------------------------------

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

    // Viết thêm một hàm hỗ trợ để không phải copy code nhiều lần
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

            // Gửi yêu cầu đặt giá lên Server
            com.auction.protocol.BidPayload payload = new com.auction.protocol.BidPayload(
                    currentAuction.getAuctionId(),
                    currentUser.getUserName(),
                    amount
            );

            NetworkClient.getInstance().sendRequest(new Request(ActionType.PLACE_BID, payload));

            txtBidAmount.clear();
            lblMessage.setText("Đang gửi yêu cầu..."); // Lúc này lblMessage đã tồn tại nên sẽ không lỗi
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

    private void showSuccess(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    public void onBackButtonClick(javafx.event.ActionEvent event) {
        try {
            // 1. Tải lại file giao diện Dashboard
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/auction/view/Dashboard.fxml"));
            javafx.scene.Parent root = loader.load();

            // 2. Trả lại thông tin User cho Dashboard (để nó biết ai đang đăng nhập mà còn phân quyền)
            DashboardController dashboardController = loader.getController();
            dashboardController.setUser(currentUser);

            // 3. Thực hiện chuyển cảnh
            javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new javafx.scene.Scene(root));
        } catch (Exception e) {
            e.printStackTrace();
            showError("Lỗi", "Không thể quay lại màn hình chính.");
        }
    }
}