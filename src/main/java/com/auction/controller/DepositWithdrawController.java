package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.StatusType;
import com.auction.utils.AppContext;
import com.auction.utils.CurrencyFormatter;
import com.auction.utils.NotificationUtil;
import javafx.application.Platform;
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

public class DepositWithdrawController {

    @FXML private StackPane rootPane;
    @FXML private Label lblUsername;
    @FXML private Label lblCurrentBalance;
    @FXML private TextField txtAmount;
    @FXML private Label lblMessage;
    @FXML private Button btnDeposit;
    @FXML private Button btnWithdraw;

    private User currentUser;

    @FXML
    public void initialize() {
        currentUser = AppContext.getCurrentUser();
        if (currentUser == null) return;

        if (lblUsername != null) lblUsername.setText(currentUser.getUserName());
        updateBalanceDisplay();

        // FIX: Dùng addEventListener với key riêng — không ghi đè listener khác
        NetworkClient.getInstance().addEventListener("wallet", response -> {
            Platform.runLater(() -> {
                if (response.getStatus() == StatusType.SUCCESS) {
                    if (response.getData() instanceof Double) {
                        double newAvailable = (Double) response.getData();
                        if (currentUser instanceof Bidder bidder) {
                            // Server trả về availableBalance sau deposit (không có frozen khi deposit)
                            bidder.setBalance(newAvailable + bidder.getFrozenBalance());
                        } else if (currentUser instanceof Seller seller) {
                            seller.setBalance(newAvailable);
                        }
                        updateBalanceDisplay();
                    }
                    setMessage("✅ " + response.getMessage(), "#27ae60");
                    NotificationUtil.showToast(response.getMessage(), rootPane, "success");
                    if (txtAmount != null) txtAmount.clear();
                } else {
                    setMessage("❌ " + response.getMessage(), "#e74c3c");
                    NotificationUtil.showToast(response.getMessage(), rootPane, "error");
                }
            });
        });
    }

    private void updateBalanceDisplay() {
        if (lblCurrentBalance == null) return;
        if (currentUser instanceof Bidder bidder) {
            String balStr = CurrencyFormatter.format(bidder.getBalance());
            String availStr = CurrencyFormatter.format(bidder.getAvailableBalance());
            if (bidder.getFrozenBalance() > 0) {
                lblCurrentBalance.setText(availStr + " (khả dụng)");
            } else {
                lblCurrentBalance.setText(balStr);
            }
        } else if (currentUser instanceof Seller) {
            lblCurrentBalance.setText(CurrencyFormatter.format(((Seller) currentUser).getBalance()));
        }
    }

    @FXML
    public void onDepositClick(ActionEvent event) {
        processTransaction(true);
    }

    @FXML
    public void onWithdrawClick(ActionEvent event) {
        processTransaction(false);
    }

    private void processTransaction(boolean isDeposit) {
        String amountStr = txtAmount != null ? txtAmount.getText().trim() : "";
        if (amountStr.isEmpty()) {
            setMessage("⚠️ Vui lòng nhập số tiền!", "#f39c12");
            return;
        }
        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                setMessage("⚠️ Số tiền phải lớn hơn 0!", "#f39c12");
                return;
            }
            if (amount > 1_000_000_000) {
                setMessage("⚠️ Số tiền tối đa là 1 tỷ mỗi lần!", "#f39c12");
                return;
            }

            String payload = currentUser.getUserName() + "|" + amount;
            ActionType action = isDeposit ? ActionType.DEPOSIT : ActionType.WITHDRAW;
            NetworkClient.getInstance().sendRequest(new Request(action, payload));
            setMessage("⏳ Đang xử lý...", "#A0A0A0");

        } catch (NumberFormatException e) {
            setMessage("❌ Vui lòng nhập số tiền hợp lệ!", "#e74c3c");
        }
    }

    @FXML public void onQuick100k() { setAmount(100_000); }
    @FXML public void onQuick500k() { setAmount(500_000); }
    @FXML public void onQuick1M()   { setAmount(1_000_000); }
    @FXML public void onQuick5M()   { setAmount(5_000_000); }

    private void setAmount(double amount) {
        if (txtAmount != null) {
            txtAmount.setText(String.valueOf((long) amount));
        }
    }


    private void setMessage(String text, String color) {
        if (lblMessage != null) {
            lblMessage.setText(text);
            lblMessage.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13px; -fx-font-weight: bold;");
        }
    }

    @FXML
    public void onBackClick(ActionEvent event) {
        // FIX: Xóa đúng key listener khi rời trang
        NetworkClient.getInstance().removeEventListener("wallet");
        try {
            String fxmlPath;
            if (currentUser instanceof com.auction.model.Admin) {
                fxmlPath = "/com/auction/view/AdminDashboard.fxml";
            } else if (currentUser instanceof Seller) {
                fxmlPath = "/com/auction/view/SellerDashboard.fxml";
            } else {
                fxmlPath = "/com/auction/view/BidderDashboard.fxml";
            }
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1200, 800));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
