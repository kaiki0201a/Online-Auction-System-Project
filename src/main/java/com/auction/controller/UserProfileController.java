package com.auction.controller;

import com.auction.model.*;
import com.auction.utils.AppContext;
import com.auction.utils.CurrencyFormatter;
import javafx.animation.FadeTransition;
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
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class UserProfileController {

    // ── Header ────────────────────────────────────────────────────────────────
    @FXML private StackPane rootPane;
    @FXML private Label lblUsername;
    @FXML private Label lblMemberBadge;

    // ── Tier & Balance ────────────────────────────────────────────────────────
    @FXML private Label lblTier;
    @FXML private Label lblAuctionLimit;
    @FXML private Label lblBalance;

    // ── Card name inside Visa card ────────────────────────────────────────────
    @FXML private Label lblCardName;

    // ── Payment Methods collapsible ───────────────────────────────────────────
    @FXML private VBox  paymentSection;
    @FXML private Label lblPaymentChevron;
    @FXML private VBox  visaCardPanel;

    // ── Purchase History collapsible ──────────────────────────────────────────
    @FXML private VBox  historySection;
    @FXML private Label lblHistoryChevron;
    @FXML private VBox  historyPanel;

    // ── Purchase History content ──────────────────────────────────────────────
    @FXML private Label lblTotalBids;
    @FXML private Label lblWinCount;
    @FXML private Label lblWinRate;
    @FXML private TableView<BidTransaction>         tblHistory;
    @FXML private TableColumn<BidTransaction, String> colHisTime;
    @FXML private TableColumn<BidTransaction, String> colHisItem;
    @FXML private TableColumn<BidTransaction, String> colHisAmount;
    @FXML private TableColumn<BidTransaction, String> colHisStatus;

    // ── State ─────────────────────────────────────────────────────────────────
    private User    currentUser;
    private boolean paymentExpanded = false;
    private boolean historyExpanded  = false;

    // ─────────────────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        currentUser = AppContext.getCurrentUser();
        setupTableColumns();
        if (currentUser != null) refreshUI();
    }

    /** Called from Bidder dashboard to inject user directly. */
    public void setUserData(User user) {
        this.currentUser = user;
        AppContext.setCurrentUser(user);
        refreshUI();
    }

    // ── UI refresh ────────────────────────────────────────────────────────────
    private void refreshUI() {
        if (currentUser == null) return;

        String name = currentUser.getUserName();


        if (lblUsername != null) lblUsername.setText(name);
        if (lblCardName != null) lblCardName.setText(name.toUpperCase());

        // Tier & membership
        String tier  = "STANDARD";
        String badge = "STANDARD MEMBER";
        String limit = "$500,000";

        if (currentUser instanceof Admin) {
            tier  = "ADMIN";
            badge = "ADMIN";
            limit = "—";
        } else if (currentUser instanceof Seller) {
            tier  = "SELLER";
            badge = "SELLER MEMBER";
            limit = "$1,000,000";
        } else if (currentUser instanceof Bidder) {
            tier  = "PLATINUM";
            badge = "PLATINUM MEMBER";
            limit = "$2,500,000";
        }

        if (lblTier         != null) lblTier.setText(tier);
        if (lblMemberBadge  != null) lblMemberBadge.setText(badge);
        if (lblAuctionLimit != null) lblAuctionLimit.setText(limit);

        // Balance
        double balance = 0;
        if (currentUser instanceof Bidder b) balance = b.getBalance();
        else if (currentUser instanceof Seller s) balance = s.getBalance();
        if (lblBalance != null) lblBalance.setText(CurrencyFormatter.format(balance));

        // Bid history (Bidder only)
        if (currentUser instanceof Bidder bidder) {
            loadBidHistory(bidder);
        }
    }

    // ── Table columns setup ───────────────────────────────────────────────────
    private void setupTableColumns() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        if (colHisTime != null)
            colHisTime.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getTimestamp().format(dtf)));

        if (colHisItem != null)
            colHisItem.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getAuction().getItem().getNameItem()));

        if (colHisAmount != null)
            colHisAmount.setCellValueFactory(data ->
                new SimpleStringProperty(CurrencyFormatter.format(data.getValue().getBidAmount())));

        if (colHisStatus != null) {
            colHisStatus.setCellValueFactory(data -> {
                BidTransaction tx  = data.getValue();
                Auction        auc = tx.getAuction();
                boolean isWinner = auc.getHighestBidder() != null &&
                    auc.getHighestBidder().getUserName().equals(currentUser.getUserName());
                boolean isFinished = auc.getStatus() == AuctionStatus.FINISHED
                    || auc.getStatus() == AuctionStatus.PAID;
                String label;
                if (isFinished) {
                    label = isWinner ? "✅ ĐÃ THẮNG" : "❌ THUA";
                } else {
                    label = isWinner ? "🏆 ĐANG THẮNG" : "⚠ BỊ VƯỢT";
                }
                return new SimpleStringProperty(label);
            });

            colHisStatus.setCellFactory(tc -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) { setText(null); setStyle(""); return; }
                    setText(item);
                    if (item.contains("THẮNG")) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else if (item.contains("THUA") || item.contains("VƯỢT")) {
                        setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #F5C518; -fx-font-weight: bold;");
                    }
                }
            });
        }
    }

    private void loadBidHistory(Bidder bidder) {
        List<BidTransaction> history = bidder.getTransactionHistory();
        if (tblHistory != null) {
            tblHistory.setItems(FXCollections.observableArrayList(history));
        }
        int total = history.size();
        long wins = history.stream()
            .filter(tx -> {
                Auction a = tx.getAuction();
                return (a.getStatus() == AuctionStatus.FINISHED || a.getStatus() == AuctionStatus.PAID)
                    && a.getHighestBidder() != null
                    && a.getHighestBidder().getUserName().equals(bidder.getUserName());
            }).count();
        int rate = total > 0 ? (int)(wins * 100 / total) : 0;

        if (lblTotalBids != null) lblTotalBids.setText(String.valueOf(total));
        if (lblWinCount  != null) lblWinCount.setText(String.valueOf(wins));
        if (lblWinRate   != null) lblWinRate.setText(rate + "%");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Menu row click handlers ───────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    /** Personal Information → Settings page */
    @FXML
    public void onPersonalInfoClick(MouseEvent event) {
        openSettings(event);
    }

    /** Security & 2FA → Settings page */
    @FXML
    public void onSecurityClick(MouseEvent event) {
        openSettings(event);
    }

    /** Notifications → Settings page */
    @FXML
    public void onNotificationsClick(MouseEvent event) {
        openSettings(event);
    }

    private void openSettings(MouseEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Settings.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 600, 530));
        } catch (IOException e) { e.printStackTrace(); }
    }

    /** Payment Methods → toggle Visa card visibility */
    @FXML
    public void onPaymentMethodsClick(MouseEvent event) {
        paymentExpanded = !paymentExpanded;
        visaCardPanel.setVisible(paymentExpanded);
        visaCardPanel.setManaged(paymentExpanded);
        if (lblPaymentChevron != null)
            lblPaymentChevron.setText(paymentExpanded ? "˅" : "›");

        if (paymentExpanded) {
            FadeTransition ft = new FadeTransition(Duration.millis(220), visaCardPanel);
            ft.setFromValue(0); ft.setToValue(1); ft.play();
        }
    }

    /** Purchase History → toggle history panel visibility */
    @FXML
    public void onPurchaseHistoryClick(MouseEvent event) {
        historyExpanded = !historyExpanded;
        historyPanel.setVisible(historyExpanded);
        historyPanel.setManaged(historyExpanded);
        if (lblHistoryChevron != null)
            lblHistoryChevron.setText(historyExpanded ? "˅" : "›");

        if (historyExpanded) {
            FadeTransition ft = new FadeTransition(Duration.millis(220), historyPanel);
            ft.setFromValue(0); ft.setToValue(1); ft.play();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Button actions ────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void onGoToDepositClick(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/DepositWithdraw.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 500, 420));
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    public void onGoToWithdrawClick(ActionEvent event) {
        // Same deposit/withdraw screen — it has both tabs
        onGoToDepositClick(event);
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
    public void onLogoutClick(ActionEvent event) {
        AppContext.logout();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 900, 600));
        } catch (IOException e) { e.printStackTrace(); }
    }
}