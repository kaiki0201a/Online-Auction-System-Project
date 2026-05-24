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

/**
 * DepositWithdrawController — FIX #4:
 *
 * Đổi setOnResponseReceived → addEventListener("deposit", ...)
 * → Không còn ghi đè listener của AuctionDetail hay Dashboard khác.
 * Nhớ removeEventListener("deposit") khi rời màn hình (onBackClick).
 */
public class DepositWithdrawController {

    @FXML private StackPane rootPane;
    @FXML private Label lblUsername;
    @FXML private Label lblCurrentBalance;
    @FXML private TextField txtAmount;
    @FXML private Label lblMessage;
    @FXML private Button btnDeposit;
    @FXML private Button btnWithdraw;

    private User currentUser;

    // FIX: Key riêng — không ghi đè listener của màn hình khác
    private static final String LISTENER_KEY = "deposit";

    @FXML
    public void initialize() {
        currentUser = AppContext.getCurrentUser();
        if (currentUser == null) return;

        if (lblUsername != null) lblUsername.setText(currentUser.getUserName());
        updateBalanceDisplay();

        // FIX: addEventListener thay vì setOnResponseReceived
        NetworkClient.getInstance().addEventListener(LISTENER_KEY, response -> {
            Platform.runLater(() -> {
                // Chỉ xử lý response liên quan đến DEPOSIT / WITHDRAW
                // (phân biệt bằng data type = Double = số dư mới)
                if (response.getStatus() == StatusType.SUCCESS
                        && response.getData() instanceof Double) {
                    double newBalance = (Double) response.getData();

                    // Cập nhật balance trong local model
                    if (currentUser instanceof Bidder b) b.setBalance(newBalance);
                    else if (currentUser instanceof Seller s) s.setBalance(newBalance);

                    updateBalanceDisplay();
                    setMessage("✅ " + response.getMessage(), "#27ae60");
                    NotificationUtil.showToast(response.getMessage(), rootPane, "success");
                    if (txtAmount != null) txtAmount.clear();

                } else if (response.getStatus() == StatusType.ERROR
                        && isDepositWithdrawError(response.getMessage())) {
                    // Chỉ hiện lỗi nếu là lỗi nạp/rút (tránh nhận lỗi broadcast từ màn hình khác)
                    setMessage("❌ " + response.getMessage(), "#e74c3c");
                    NotificationUtil.showToast(response.getMessage(), rootPane, "error");
                }
            });
        });
    }

    /**
     * Lọc: chỉ xử lý error message liên quan đến deposit/withdraw.
     * Tránh nhận nhầm error broadcast từ PLACE_BID, SET_AUTOBID, v.v.
     */
    private boolean isDepositWithdrawError(String msg) {
        if (msg == null) return false;
        return msg.contains("Nạp") || msg.contains("Rút") || msg.contains("Số dư")
                || msg.contains("tiền") || msg.contains("tài khoản");
    }

    private void updateBalanceDisplay() {
        if (lblCurrentBalance == null) return;
        if (currentUser instanceof Bidder b) {
            lblCurrentBalance.setText(CurrencyFormatter.format(b.getBalance()));
        } else if (currentUser instanceof Seller s) {
            lblCurrentBalance.setText(CurrencyFormatter.format(s.getBalance()));
        }
    }

    @FXML public void onDepositClick(ActionEvent event)  { processTransaction(true); }
    @FXML public void onWithdrawClick(ActionEvent event) { processTransaction(false); }

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
            // Kiểm tra số dư trước khi rút (client-side validation nhanh)
            if (!isDeposit) {
                double currentBalance = (currentUser instanceof Bidder b) ? b.getBalance()
                        : (currentUser instanceof Seller s) ? s.getBalance() : 0;
                if (amount > currentBalance) {
                    setMessage("❌ Số dư không đủ để rút! Bạn có: "
                            + CurrencyFormatter.format(currentBalance), "#e74c3c");
                    return;
                }
            }

            String payload = currentUser.getUserName() + "|" + amount;
            ActionType action = isDeposit ? ActionType.DEPOSIT : ActionType.WITHDRAW;
            NetworkClient.getInstance().sendRequest(new Request(action, payload));
            setMessage("⏳ Đang xử lý...", "#A0A0A0");

        } catch (NumberFormatException e) {
            setMessage("❌ Vui lòng nhập số tiền hợp lệ!", "#e74c3c");
        }
    }

    // Nút nhanh
    @FXML public void onQuick100k() { setAmount(100_000); }
    @FXML public void onQuick500k() { setAmount(500_000); }
    @FXML public void onQuick1M()   { setAmount(1_000_000); }
    @FXML public void onQuick5M()   { setAmount(5_000_000); }

    private void setAmount(double amount) {
        if (txtAmount != null) txtAmount.setText(String.valueOf((long) amount));
    }

    private void setMessage(String text, String color) {
        if (lblMessage != null) {
            lblMessage.setText(text);
            lblMessage.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13px; -fx-font-weight: bold;");
        }
    }

    @FXML
    public void onBackClick(ActionEvent event) {
        // FIX: Gỡ đúng key của màn hình này
        NetworkClient.getInstance().removeEventListener(LISTENER_KEY);
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
