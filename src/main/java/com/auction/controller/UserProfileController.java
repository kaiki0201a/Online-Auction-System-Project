package com.auction.controller;

import com.auction.model.*;
import com.auction.utils.AppContext;
import com.auction.utils.CurrencyFormatter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class UserProfileController {

    @FXML private StackPane rootPane;
    @FXML private Label lblAvatar;
    @FXML private Label lblUsername;
    @FXML private Label lblRole;
    @FXML private Label lblEmail;
    @FXML private Label lblBalance;
    @FXML private Label lblTotalBids;
    @FXML private Label lblWinCount;
    @FXML private Label lblWinRate;
    @FXML private TableView<BidTransaction> tblHistory;
    @FXML private TableColumn<BidTransaction, String> colHisTime;
    @FXML private TableColumn<BidTransaction, String> colHisItem;
    @FXML private TableColumn<BidTransaction, String> colHisAmount;
    @FXML private TableColumn<BidTransaction, String> colHisStatus;

    private User currentUser;

    @FXML
    public void initialize() {
        currentUser = AppContext.getCurrentUser();

        // Setup columns
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        colHisTime.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getTimestamp().format(dtf)));
        colHisItem.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getAuction().getItem().getNameItem()));
        colHisAmount.setCellValueFactory(data ->
            new SimpleStringProperty(CurrencyFormatter.format(data.getValue().getBidAmount())));
        colHisStatus.setCellValueFactory(data -> {
            BidTransaction tx = data.getValue();
            Auction auc = tx.getAuction();
            boolean isWinner = auc.getHighestBidder() != null &&
                auc.getHighestBidder().getUserName().equals(currentUser.getUserName());
            boolean isFinished = auc.getStatus() == AuctionStatus.FINISHED || auc.getStatus() == AuctionStatus.PAID;

            String label;
            if (isFinished) {
                label = isWinner ? "✅ ĐÃ THẮNG" : "❌ THUA";
            } else {
                label = isWinner ? "🏆 ĐANG THẮNG" : "⚠ BỊ VƯỢT";
            }
            return new SimpleStringProperty(label);
        });

        // Style cột kết quả
        colHisStatus.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                if (item.contains("THẮNG") || item.contains("THẮNG")) {
                    setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                } else if (item.contains("THUA") || item.contains("VƯỢT")) {
                    setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #F5C518; -fx-font-weight: bold;");
                }
            }
        });

        if (currentUser != null) refreshUI();
    }

    /** Cho phép gọi từ Bidder dashboard */
    public void setUserData(User user) {
        this.currentUser = user;
        AppContext.setCurrentUser(user);
        refreshUI();
    }

    private void refreshUI() {
        if (currentUser == null) return;

        String name = currentUser.getUserName();
        if (lblAvatar != null) lblAvatar.setText(name.substring(0, 1).toUpperCase());
        if (lblUsername != null) lblUsername.setText(name);
        if (lblEmail != null) lblEmail.setText(currentUser.getEmail());

        // Role display
        String roleStr;
        double balance;
        if (currentUser instanceof Admin) {
            roleStr = "👑 Quản Trị Viên";
            balance = 0;
        } else if (currentUser instanceof Seller) {
            roleStr = "🏪 Người Bán (Seller)";
            balance = ((Seller) currentUser).getBalance();
        } else if (currentUser instanceof Bidder) {
            roleStr = "🏷 Người Mua (Bidder)";
            balance = ((Bidder) currentUser).getBalance();
            // Load lịch sử đặt giá
            loadBidHistory((Bidder) currentUser);
        } else {
            roleStr = "Thành viên";
            balance = 0;
        }
        if (lblRole != null) lblRole.setText(roleStr);
        if (lblBalance != null) lblBalance.setText(CurrencyFormatter.format(balance));
    }

    private void loadBidHistory(Bidder bidder) {
        List<BidTransaction> history = bidder.getTransactionHistory();

        if (tblHistory != null) {
            ObservableList<BidTransaction> obs = FXCollections.observableArrayList(history);
            tblHistory.setItems(obs);
        }

        // Thống kê
        int total = history.size();
        long wins = history.stream()
            .filter(tx -> {
                Auction a = tx.getAuction();
                return (a.getStatus() == AuctionStatus.FINISHED || a.getStatus() == AuctionStatus.PAID)
                    && a.getHighestBidder() != null
                    && a.getHighestBidder().getUserName().equals(bidder.getUserName());
            }).count();
        int rate = total > 0 ? (int) (wins * 100 / total) : 0;

        if (lblTotalBids != null) lblTotalBids.setText(String.valueOf(total));
        if (lblWinCount != null) lblWinCount.setText(String.valueOf(wins));
        if (lblWinRate != null) lblWinRate.setText(rate + "%");
    }

    @FXML
    public void onBackClick(ActionEvent event) {
        try {
            String path;
            if (currentUser instanceof Admin) {
                path = "/com/auction/view/AdminDashboard.fxml";
            } else if (currentUser instanceof Seller) {
                path = "/com/auction/view/SellerDashboard.fxml";
            } else {
                path = "/com/auction/view/BidderDashboard.fxml";
            }
            Parent root = FXMLLoader.load(getClass().getResource(path));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 800));
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    public void onGoToDepositClick(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/DepositWithdraw.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 500, 420));
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    public void onGoToSettingsClick(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Settings.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 600, 520));
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    public void onLogoutClick(ActionEvent event) {
        AppContext.logout();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 900, 600));
        } catch (IOException e) { e.printStackTrace(); }
    }
}