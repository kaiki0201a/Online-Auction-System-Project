package com.auction.controller;

import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.BidTransaction;
import com.auction.exception.AuctionException;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;

public class AuctionDetailController implements com.auction.utils.AuctionObserver{
    @FXML private TextField txtBidAmount; // Ô nhập số tiền
    @FXML private javafx.scene.control.Label lblProductName;
    @FXML private javafx.scene.control.Label lblCurrentPrice;

    // Hai biến này cực kỳ quan trọng để truyền vào Constructor của bạn
    private Auction currentAuction;
    private Bidder currentUser;

    // Hàm này dùng để nhận dữ liệu từ màn hình Dashboard truyền sang
    // Đừng quên đăng ký lắng nghe khi màn hình hiện lên
    public void setAuctionData(Auction auction, Bidder user) {
        this.currentAuction = auction;
        this.currentUser = user;
        this.currentAuction.addObserver(this); // Đăng ký với "chủ xị" Auction [cite: 3]
    }

    @FXML
    public void onBidButtonClick() {
        try {
            // Lấy số tiền người dùng nhập
            double amount = Double.parseDouble(txtBidAmount.getText());

            // ĐÚNG CHUẨN: Gọi Constructor với 3 tham số như bạn đã viết
            BidTransaction transaction = new BidTransaction(currentAuction, currentUser, amount);

            // Gọi hàm xử lý trong file Auction.java xịn của bạn
            currentAuction.processBid(transaction);

            // Sửa trong hàm onBidButtonClick
            showSuccess("Thành công", "Đặt giá thành công cho món: " + currentAuction.getItem().getNameItem());
        } catch (NumberFormatException e) {
            showError("Lỗi", "Vui lòng nhập một con số hợp lệ!");
        } catch (AuctionException e) {
            // Bắt các lỗi InvalidBidException, InsufficientBalanceException...
            showError("Thông báo", e.getMessage());
        }
    }

    private void showSuccess(String s) {
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
    @Override
    public void update(String message) {
        // Platform.runLater là bắt buộc để cập nhật UI từ luồng mạng (Thread của bạn C)
        javafx.application.Platform.runLater(() -> {
            lblCurrentPrice.setText("$" + currentAuction.getCurrentHighestBid());
            // Có thể hiện thêm một dòng thông báo nhỏ trên UI
        });
    }

}