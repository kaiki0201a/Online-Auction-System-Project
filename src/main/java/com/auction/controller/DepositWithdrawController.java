package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.*;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.StatusType;
import com.auction.utils.AppContext;
import com.auction.utils.CurrencyFormatter;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

/**
 * DepositWithdrawController — Thiết kế mới (2 ô nhập độc lập + lịch sử giao dịch).
 *
 * Logic:
 *  - txtDepositAmount + btnDeposit  → gửi DEPOSIT request
 *  - txtWithdrawAmount + btnWithdraw → gửi WITHDRAW request
 *  - Sau khi nhận response SUCCESS (data=Double) → cộng/trừ local model,
 *    thêm WalletTransaction vào walletHistory, re-render bảng lịch sử.
 */
public class DepositWithdrawController {

    // ── Cards tóm tắt ──
    @FXML private StackPane rootPane;
    @FXML private Label lblUsername;
    @FXML private Label lblCurrentBalance;
    @FXML private Label lblTotalDeposit;
    @FXML private Label lblDepositCount;
    @FXML private Label lblTotalWithdraw;
    @FXML private Label lblWithdrawCount;
    @FXML private Label lblTotalTx;

    // ── Form nạp ──
    @FXML private TextField txtDepositAmount;
    @FXML private Label     lblDepositMessage;
    @FXML private Button    btnDeposit;

    // ── Form rút ──
    @FXML private TextField txtWithdrawAmount;
    @FXML private Label     lblWithdrawMessage;
    @FXML private Button    btnWithdraw;

    // ── Bảng lịch sử ──
    @FXML private VBox historyContainer;

    private User currentUser;

    // Phân biệt giao dịch đang chờ: "deposit" | "withdraw" | null
    private String pendingType = null;

    private static final String LISTENER_KEY = "deposit";

    // ─── Khởi tạo ────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        currentUser = AppContext.getCurrentUser();
        if (currentUser == null) return;

        if (lblUsername != null) {
            lblUsername.setText(currentUser.getUserName()
                    + " · " + (currentUser instanceof Bidder ? "Bidder" : "Seller"));
        }

        // TextFormatter: chỉ nhận số
        applyNumericFormatter(txtDepositAmount);
        applyNumericFormatter(txtWithdrawAmount);

        updateSummaryCards();
        renderHistory();

        // Đăng ký listener
        NetworkClient.getInstance().addEventListener(LISTENER_KEY, response ->
                Platform.runLater(() -> handleNetworkResponse(response))
        );
    }

    private void applyNumericFormatter(TextField tf) {
        if (tf == null) return;
        tf.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("\\d*(\\.\\d*)?") ? change : null));
    }

    // ─── Xử lý Response từ Server ────────────────────────────────────────────

    private void handleNetworkResponse(com.auction.protocol.Response response) {
        String msg = response.getMessage();
        // Bỏ qua mọi broadcast không liên quan
        if (isIgnoredBroadcast(msg)) return;

        if (response.getStatus() == StatusType.SUCCESS) {
            // FIX BUG B: Server trả về WalletTransaction đã được persist — dùng trực tiếp
            if (response.getData() instanceof WalletTransaction serverTx) {
                double newBalance = serverTx.getBalanceAfter();
                // Cập nhật balance trong model local
                setBalance(newBalance);
                // Thêm WalletTransaction từ server vào list local (server đã lưu vào file)
                addWalletHistory(serverTx);

                String savedType = pendingType;
                pendingType = null;
                updateSummaryCards();
                renderHistory();

                if ("deposit".equals(savedType) || serverTx.getType() == WalletTransaction.Type.DEPOSIT) {
                    setMsg(lblDepositMessage,
                            "✅ Nạp thành công! Số dư: " + CurrencyFormatter.format(newBalance), "#4CAF50");
                    clearField(txtDepositAmount);
                } else if ("withdraw".equals(savedType) || serverTx.getType() == WalletTransaction.Type.WITHDRAW) {
                    setMsg(lblWithdrawMessage,
                            "✅ Rút thành công! Số dư: " + CurrencyFormatter.format(newBalance), "#4CAF50");
                    clearField(txtWithdrawAmount);
                }
                return;
            }

            // Backward compat: Server cũ trả về Double balance (không có WalletTransaction)
            if (response.getData() instanceof Double newBalance) {
                double oldBalance = getBalance();
                double diff = newBalance - oldBalance;

                setBalance(newBalance);

                if (pendingType != null) {
                    WalletTransaction.Type wType = "deposit".equals(pendingType)
                            ? WalletTransaction.Type.DEPOSIT
                            : WalletTransaction.Type.WITHDRAW;
                    double amount = Math.abs(diff) > 0.001 ? Math.abs(diff)
                            : (pendingType.equals("deposit") ? extractAmount(txtDepositAmount) : extractAmount(txtWithdrawAmount));
                    addWalletHistory(new WalletTransaction(wType, amount, newBalance));
                }

                String savedType = pendingType;
                pendingType = null;
                updateSummaryCards();
                renderHistory();

                if ("deposit".equals(savedType)) {
                    setMsg(lblDepositMessage,
                            "✅ Nạp thành công! Số dư: " + CurrencyFormatter.format(newBalance), "#4CAF50");
                    clearField(txtDepositAmount);
                } else if ("withdraw".equals(savedType)) {
                    setMsg(lblWithdrawMessage,
                            "✅ Rút thành công! Số dư: " + CurrencyFormatter.format(newBalance), "#4CAF50");
                    clearField(txtWithdrawAmount);
                } else {
                    if (diff >= 0) {
                        setMsg(lblDepositMessage,
                                "✅ Nạp thành công! Số dư: " + CurrencyFormatter.format(newBalance), "#4CAF50");
                    } else {
                        setMsg(lblWithdrawMessage,
                                "✅ Rút thành công! Số dư: " + CurrencyFormatter.format(newBalance), "#4CAF50");
                    }
                }
            }

        } else if (response.getStatus() == StatusType.ERROR) {
            String errMsg = "❌ " + (msg != null ? msg : "Lỗi không xác định");
            if ("deposit".equals(pendingType)) setMsg(lblDepositMessage, errMsg, "#e74c3c");
            else if ("withdraw".equals(pendingType)) setMsg(lblWithdrawMessage, errMsg, "#e74c3c");
            pendingType = null;
        }
    }


    // ─── Xử lý nút NẠP ──────────────────────────────────────────────────────

    @FXML public void onDepositClick(ActionEvent e) {
        double amount = validateAmount(txtDepositAmount, lblDepositMessage, "nạp");
        if (amount <= 0) return;
        if (amount > 1_000_000_000) {
            setMsg(lblDepositMessage, "⚠️ Tối đa 1 tỷ mỗi lần nạp!", "#f39c12"); return;
        }
        pendingType = "deposit";
        setMsg(lblDepositMessage, "⏳ Đang xử lý...", "#A0A0A0");
        NetworkClient.getInstance().sendRequest(
                new Request(ActionType.DEPOSIT, currentUser.getUserName() + "|" + amount));
    }

    // Nút nhanh nạp
    @FXML public void onDepositQuick100k(ActionEvent e) { setField(txtDepositAmount, 100_000); }
    @FXML public void onDepositQuick500k(ActionEvent e) { setField(txtDepositAmount, 500_000); }
    @FXML public void onDepositQuick1M(ActionEvent e)   { setField(txtDepositAmount, 1_000_000); }
    @FXML public void onDepositQuick5M(ActionEvent e)   { setField(txtDepositAmount, 5_000_000); }

    // ─── Xử lý nút RÚT ──────────────────────────────────────────────────────

    @FXML public void onWithdrawClick(ActionEvent e) {
        double amount = validateAmount(txtWithdrawAmount, lblWithdrawMessage, "rút");
        if (amount <= 0) return;
        double balance = getBalance();
        if (amount > balance) {
            setMsg(lblWithdrawMessage,
                    "❌ Số dư không đủ! Bạn có: " + CurrencyFormatter.format(balance), "#e74c3c");
            return;
        }
        pendingType = "withdraw";
        setMsg(lblWithdrawMessage, "⏳ Đang xử lý...", "#A0A0A0");
        NetworkClient.getInstance().sendRequest(
                new Request(ActionType.WITHDRAW, currentUser.getUserName() + "|" + amount));
    }

    // Nút nhanh rút
    @FXML public void onWithdrawQuick100k(ActionEvent e) { setField(txtWithdrawAmount, 100_000); }
    @FXML public void onWithdrawQuick500k(ActionEvent e) { setField(txtWithdrawAmount, 500_000); }
    @FXML public void onWithdrawQuick1M(ActionEvent e)   { setField(txtWithdrawAmount, 1_000_000); }
    @FXML public void onWithdrawQuick5M(ActionEvent e)   { setField(txtWithdrawAmount, 5_000_000); }

    // ─── Render lịch sử giao dịch ────────────────────────────────────────────

    private void renderHistory() {
        if (historyContainer == null) return;
        historyContainer.getChildren().clear();

        List<WalletTransaction> history = getWalletHistory();

        if (history.isEmpty()) {
            Label empty = new Label("Chưa có giao dịch nào.");
            empty.setStyle("-fx-text-fill: #444; -fx-font-size: 13px; -fx-padding: 20;");
            historyContainer.getChildren().add(empty);
            return;
        }

        for (WalletTransaction tx : history) {
            boolean isDeposit = tx.getType() == WalletTransaction.Type.DEPOSIT;
            String color = isDeposit ? "#4CAF50" : "#e74c3c";
            String icon  = isDeposit ? "⬆" : "⬇";

            HBox row = new HBox();
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color: #1A1A1A; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10 14;");

            // Thời gian
            Label lblTime = new Label(tx.getFormattedTime());
            lblTime.setMinWidth(130);
            lblTime.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");

            // Loại
            Label lblType = new Label(icon + "  " + tx.getTypeLabel());
            lblType.setMinWidth(100);
            lblType.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px; -fx-font-weight: bold;");

            // Số tiền
            Label lblAmount = new Label((isDeposit ? "+" : "-") + CurrencyFormatter.format(tx.getAmount()));
            HBox.setHgrow(lblAmount, Priority.ALWAYS);
            lblAmount.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13px; -fx-font-weight: bold;");

            // Số dư sau
            Label lblAfter = new Label(CurrencyFormatter.format(tx.getBalanceAfter()));
            lblAfter.setMinWidth(160);
            lblAfter.setAlignment(Pos.CENTER_RIGHT);
            lblAfter.setStyle("-fx-text-fill: #D0D0D0; -fx-font-size: 12px;");

            row.getChildren().addAll(lblTime, lblType, lblAmount, lblAfter);
            historyContainer.getChildren().add(row);
        }
    }

    // ─── Cập nhật cards tóm tắt ──────────────────────────────────────────────

    private void updateSummaryCards() {
        if (lblCurrentBalance != null)
            lblCurrentBalance.setText(CurrencyFormatter.format(getBalance()));

        List<WalletTransaction> history = getWalletHistory();

        double totalDeposit  = history.stream().filter(t -> t.getType() == WalletTransaction.Type.DEPOSIT)
                .mapToDouble(WalletTransaction::getAmount).sum();
        double totalWithdraw = history.stream().filter(t -> t.getType() == WalletTransaction.Type.WITHDRAW)
                .mapToDouble(WalletTransaction::getAmount).sum();
        long countDeposit  = history.stream().filter(t -> t.getType() == WalletTransaction.Type.DEPOSIT).count();
        long countWithdraw = history.stream().filter(t -> t.getType() == WalletTransaction.Type.WITHDRAW).count();

        if (lblTotalDeposit != null)  lblTotalDeposit.setText(CurrencyFormatter.format(totalDeposit));
        if (lblDepositCount != null)  lblDepositCount.setText(countDeposit + " giao dịch");
        if (lblTotalWithdraw != null) lblTotalWithdraw.setText(CurrencyFormatter.format(totalWithdraw));
        if (lblWithdrawCount != null) lblWithdrawCount.setText(countWithdraw + " giao dịch");
        if (lblTotalTx != null)       lblTotalTx.setText(history.size() + " giao dịch");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private double getBalance() {
        if (currentUser instanceof Bidder b) return b.getBalance();
        if (currentUser instanceof Seller s) return s.getBalance();
        return 0;
    }

    private void setBalance(double v) {
        if (currentUser instanceof Bidder b) b.setBalance(v);
        else if (currentUser instanceof Seller s) s.setBalance(v);
    }

    private List<WalletTransaction> getWalletHistory() {
        if (currentUser instanceof Bidder b) return b.getWalletHistory();
        if (currentUser instanceof Seller s) return s.getWalletHistory();
        return List.of();
    }

    private void addWalletHistory(WalletTransaction wt) {
        if (currentUser instanceof Bidder b) b.addWalletTransaction(wt);
        else if (currentUser instanceof Seller s) s.addWalletTransaction(wt);
    }

    private double validateAmount(TextField tf, Label lbl, String action) {
        String text = tf != null ? tf.getText().trim() : "";
        if (text.isEmpty()) {
            setMsg(lbl, "⚠️ Vui lòng nhập số tiền muốn " + action + "!", "#f39c12");
            return -1;
        }
        try {
            double v = Double.parseDouble(text);
            if (v <= 0) { setMsg(lbl, "⚠️ Số tiền phải lớn hơn 0!", "#f39c12"); return -1; }
            return v;
        } catch (NumberFormatException ex) {
            setMsg(lbl, "❌ Số tiền không hợp lệ!", "#e74c3c");
            return -1;
        }
    }

    private double extractAmount(TextField tf) {
        try { return tf != null ? Double.parseDouble(tf.getText().trim()) : 0; }
        catch (Exception e) { return 0; }
    }

    private void setField(TextField tf, double amount) {
        if (tf != null) tf.setText(String.valueOf((long) amount));
    }

    private void clearField(TextField tf) {
        if (tf != null) tf.clear();
    }

    private void setMsg(Label lbl, String text, String color) {
        if (lbl != null) {
            lbl.setText(text);
            lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px; -fx-font-weight: bold;");
        }
    }

    /** Bỏ qua broadcast không liên quan */
    private boolean isIgnoredBroadcast(String msg) {
        if (msg == null) return false;
        return msg.startsWith("AUCTION_") || msg.startsWith("UPDATE_AUCTION")
                || msg.startsWith("SELLER_BALANCE_UPDATE|")
                || msg.startsWith("DUYỆT_OK|") || msg.startsWith("TỪ_CHỐI_OK|")
                || msg.equals("Đặt giá thành công!") || msg.equals("AUTOBID_OK")
                || msg.startsWith("AUTOBID_ERROR");
    }

    // ─── Navigation ──────────────────────────────────────────────────────────

    @FXML
    public void onBackClick(ActionEvent event) {
        NetworkClient.getInstance().removeEventListener(LISTENER_KEY);
        try {
            String fxml;
            if (currentUser instanceof com.auction.model.Admin)
                fxml = "/com/auction/view/AdminDashboard.fxml";
            else if (currentUser instanceof Seller)
                fxml = "/com/auction/view/SellerDashboard.fxml";
            else
                fxml = "/com/auction/view/BidderDashboard.fxml";

            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            double _w = stage.getWidth();
            double _h = stage.getHeight();
            double _x = stage.getX();
            double _y = stage.getY();
            stage.setScene(new Scene(root));
            stage.setWidth(_w);
            stage.setHeight(_h);
            stage.setX(_x);
            stage.setY(_y);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
