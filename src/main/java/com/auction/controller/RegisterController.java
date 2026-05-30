package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.protocol.StatusType;
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
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.IOException;

public class RegisterController {

    @FXML private StackPane     rootPane;
    @FXML private TextField     txtUsername;
    @FXML private TextField     txtEmail;
    @FXML private PasswordField txtPassword;
    @FXML private TextField     txtPasswordVisible;
    @FXML private Button        btnTogglePassword;
    @FXML private ToggleGroup   roleToggleGroup;
    @FXML private ToggleButton  btnBidder;
    @FXML private ToggleButton  btnSeller;
    @FXML private ToggleButton  btnAdmin;
    @FXML private VBox          adminCodeBox;
    @FXML private PasswordField txtAdminCode;
    @FXML private CheckBox      chkTerms;
    @FXML private Label         lblStatus;
    @FXML private Button        btnRegister;

    private static final String STYLE_ACTIVE   = "-fx-background-color: #D4AF37; -fx-text-fill: #000000; -fx-font-weight: bold; -fx-font-size: 13px; -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 12 10;";
    private static final String STYLE_INACTIVE = "-fx-background-color: #1e1e1e; -fx-text-fill: #AAAAAA; -fx-font-weight: bold; -fx-font-size: 13px; -fx-border-color: #333; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 12 10;";
    private static final String STYLE_ADMIN_ACTIVE = "-fx-background-color: #8B0000; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 13px; -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 12 10;";

    private boolean pwVisible = false;

    @FXML
    public void initialize() {
        // Lắng nghe thay đổi role để hiện/ẩn ô mã Admin và cập nhật style
        if (roleToggleGroup != null) {
            roleToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
                // Không cho phép bỏ chọn tất cả — nếu user click lại nút đang chọn, giữ nguyên
                if (newVal == null) {
                    roleToggleGroup.selectToggle(oldVal);
                    return;
                }
                updateRoleStyles(newVal);
            });
        }
        // Đảm bảo Bidder được chọn mặc định với style active
        if (btnBidder != null) {
            btnBidder.setStyle(STYLE_ACTIVE);
        }
    }

    /** Xử lý khi nhấn nút role (Bidder / Seller / Admin) */
    @FXML
    public void onRoleToggle(ActionEvent event) {
        updateRoleStyles(roleToggleGroup != null ? roleToggleGroup.getSelectedToggle() : null);
    }

    private void updateRoleStyles(Toggle selected) {
        boolean isAdmin = (selected == btnAdmin);
        // Cập nhật màu từng nút
        if (btnBidder != null)
            btnBidder.setStyle(selected == btnBidder ? STYLE_ACTIVE   : STYLE_INACTIVE);
        if (btnSeller != null)
            btnSeller.setStyle(selected == btnSeller ? STYLE_ACTIVE   : STYLE_INACTIVE);
        if (btnAdmin  != null)
            btnAdmin.setStyle( isAdmin                ? STYLE_ADMIN_ACTIVE : STYLE_INACTIVE);
        // Hiện/ẩn ô mã Admin
        if (adminCodeBox != null) {
            adminCodeBox.setVisible(isAdmin);
            adminCodeBox.setManaged(isAdmin);
        }
    }

    // ─── Toggle hiển thị mật khẩu ─────────────────────────────────────────────

    @FXML public void onTogglePassword(ActionEvent e) {
        pwVisible = !pwVisible;
        if (pwVisible) {
            txtPasswordVisible.setText(txtPassword.getText());
            txtPasswordVisible.setVisible(true);  txtPasswordVisible.setManaged(true);
            txtPassword.setVisible(false);         txtPassword.setManaged(false);
            btnTogglePassword.setText("👁");   // mắt mở = đang hiện
        } else {
            txtPassword.setText(txtPasswordVisible.getText());
            txtPassword.setVisible(true);          txtPassword.setManaged(true);
            txtPasswordVisible.setVisible(false);  txtPasswordVisible.setManaged(false);
            btnTogglePassword.setText("🔒");  // khoá = đang ẩn
        }
    }

    private String getPassword() { return pwVisible ? txtPasswordVisible.getText() : txtPassword.getText(); }

    // ─── Đăng ký ──────────────────────────────────────────────────────────────

    @FXML
    public void onRegisterClick(ActionEvent event) {
        String username = safe(txtUsername);
        String email    = safe(txtEmail);
        String password = getPassword();

        // Xác định role từ ToggleButton
        Toggle selected = (roleToggleGroup != null) ? roleToggleGroup.getSelectedToggle() : null;

        // ─── Validate ─────────────────────────────────────────────────────────
        if (selected == null) {
            showStatus("❌ Vui lòng chọn vai trò (Người mua / Người bán / Admin)!", "#e74c3c"); return;
        }

        boolean isAdmin  = (selected == btnAdmin);
        boolean isSeller = (selected == btnSeller);
        String role = isAdmin ? "Admin" : (isSeller ? "Seller" : "Bidder");

        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showStatus("❌ Vui lòng điền đầy đủ thông tin!", "#e74c3c"); return;
        }
        if (username.length() < 4) {
            showStatus("❌ Tên đăng nhập phải có ít nhất 4 ký tự!", "#e74c3c"); return;
        }
        if (!email.toLowerCase().endsWith("@gmail.com")) {
            showStatus("❌ Email phải có định dạng @gmail.com!", "#e74c3c"); return;
        }
        if (password.length() < 6) {
            showStatus("❌ Mật khẩu phải có ít nhất 6 ký tự!", "#e74c3c"); return;
        }
        // Validate Admin code
        if (isAdmin) {
            String adminCode = txtAdminCode != null ? txtAdminCode.getText().trim() : "";
            if (adminCode.isEmpty()) {
                showStatus("❌ Vui lòng nhập mã xác nhận Admin!", "#e74c3c"); return;
            }
        }

        if (btnRegister != null) btnRegister.setDisable(true);

        // FIX C-04: dùng addEventListener("register", ...) thay vì setOnResponseReceived
        // Tránh (1) ghi đè listener của màn hình khác, (2) bug gọi 2 lần liên tiếp.
        NetworkClient.getInstance().addEventListener("register", response -> {
            Platform.runLater(() -> {
                // Gỡ listener ngay sau khi nhận response đầu tiên
                NetworkClient.getInstance().removeEventListener("register");
                if (btnRegister != null) btnRegister.setDisable(false);
                if (response.getStatus() == StatusType.SUCCESS) {
                    showStatus("✅ " + response.getMessage(), "#27ae60");
                    new Thread(() -> {
                        try { Thread.sleep(1500); Platform.runLater(() -> navigateToLogin(event)); }
                        catch (InterruptedException ignored) {}
                    }).start();
                } else {
                    showStatus("❌ " + response.getMessage(), "#e74c3c");
                }
            });
        });

        showStatus("⏳ Đang xử lý...", "#A0A0A0");

        // ─── Gửi request ─────────────────────────────────────────────────────
        if (isAdmin) {
            String adminCode = txtAdminCode != null ? txtAdminCode.getText().trim() : "";
            NetworkClient.getInstance().register(username, password, email, role, adminCode);
        } else {
            NetworkClient.getInstance().register(username, password, email, role);
        }
    }

    @FXML public void onBackToLoginClick(ActionEvent event) { navigateToLogin(event); }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private void navigateToLogin(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 900, 600));
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void showStatus(String msg, String color) {
        if (lblStatus != null) {
            lblStatus.setText(msg);
            lblStatus.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;");
        }
    }

    private String safe(TextField f) {
        return (f != null) ? f.getText().trim() : "";
    }
}
