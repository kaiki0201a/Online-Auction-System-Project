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

    @FXML private StackPane rootPane;
    @FXML private TextField  txtUsername;
    @FXML private TextField  txtEmail;
    @FXML private PasswordField txtPassword;
    @FXML private TextField     txtPasswordVisible;
    @FXML private Button        btnTogglePassword;
    @FXML private PasswordField txtConfirmPassword;
    @FXML private TextField     txtConfirmVisible;
    @FXML private Button        btnToggleConfirm;
    @FXML private ToggleGroup   roleGroup;
    @FXML private RadioButton   radioBidder;
    @FXML private RadioButton   radioSeller;
    @FXML private RadioButton   radioAdmin;
    @FXML private VBox          adminCodeBox;   // Panel mã Admin (ẩn theo mặc định)
    @FXML private PasswordField txtAdminCode;   // Mã Admin bảo mật
    @FXML private Label         lblStatus;
    @FXML private Button        btnRegister;

    private boolean pwVisible      = false;
    private boolean confirmVisible = false;

    @FXML
    public void initialize() {
        // Lắng nghe thay đổi role để hiện/ẩn ô mã Admin
        if (roleGroup != null) {
            roleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
                boolean isAdmin = (newVal == radioAdmin);
                if (adminCodeBox != null) {
                    adminCodeBox.setVisible(isAdmin);
                    adminCodeBox.setManaged(isAdmin);
                }
                // Đổi style radio Admin khi được chọn
                if (radioAdmin != null) {
                    radioAdmin.setStyle(isAdmin
                        ? "-fx-text-fill: #e74c3c; -fx-font-size: 12px; -fx-font-weight: bold;"
                        : "-fx-text-fill: #888; -fx-font-size: 12px;");
                }
            });
        }
    }

    // ─── Toggle hiển thị mật khẩu ─────────────────────────────────────────────

    @FXML public void onTogglePassword(ActionEvent e) {
        pwVisible = !pwVisible;
        if (pwVisible) {
            txtPasswordVisible.setText(txtPassword.getText());
            txtPasswordVisible.setVisible(true);  txtPasswordVisible.setManaged(true);
            txtPassword.setVisible(false);         txtPassword.setManaged(false);
            btnTogglePassword.setText("🙈");
        } else {
            txtPassword.setText(txtPasswordVisible.getText());
            txtPassword.setVisible(true);          txtPassword.setManaged(true);
            txtPasswordVisible.setVisible(false);  txtPasswordVisible.setManaged(false);
            btnTogglePassword.setText("👁");
        }
    }

    @FXML public void onToggleConfirm(ActionEvent e) {
        confirmVisible = !confirmVisible;
        if (confirmVisible) {
            txtConfirmVisible.setText(txtConfirmPassword.getText());
            txtConfirmVisible.setVisible(true);  txtConfirmVisible.setManaged(true);
            txtConfirmPassword.setVisible(false); txtConfirmPassword.setManaged(false);
            btnToggleConfirm.setText("🙈");
        } else {
            txtConfirmPassword.setText(txtConfirmVisible.getText());
            txtConfirmPassword.setVisible(true);  txtConfirmPassword.setManaged(true);
            txtConfirmVisible.setVisible(false);  txtConfirmVisible.setManaged(false);
            btnToggleConfirm.setText("👁");
        }
    }

    private String getPassword()  { return pwVisible      ? txtPasswordVisible.getText() : txtPassword.getText(); }
    private String getConfirm()   { return confirmVisible ? txtConfirmVisible.getText()  : txtConfirmPassword.getText(); }

    // ─── Đăng ký ──────────────────────────────────────────────────────────────

    @FXML
    public void onRegisterClick(ActionEvent event) {
        String username = safe(txtUsername);
        String email    = safe(txtEmail);
        String password = getPassword();
        String confirm  = getConfirm();

        // Xác định role
        boolean isAdmin  = (radioAdmin  != null && radioAdmin.isSelected());
        boolean isSeller = (radioSeller != null && radioSeller.isSelected());
        String role = isAdmin ? "Admin" : (isSeller ? "Seller" : "Bidder");

        // ─── Validate ─────────────────────────────────────────────────────────
        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showStatus("❌ Vui lòng điền đầy đủ thông tin!", "#e74c3c"); return;
        }
        if (username.length() < 4) {
            showStatus("❌ Tên đăng nhập phải có ít nhất 4 ký tự!", "#e74c3c"); return;
        }
        if (!email.contains("@") || !email.contains(".")) {
            showStatus("❌ Email không hợp lệ!", "#e74c3c"); return;
        }
        if (password.length() < 6) {
            showStatus("❌ Mật khẩu phải có ít nhất 6 ký tự!", "#e74c3c"); return;
        }
        if (!password.equals(confirm)) {
            showStatus("❌ Mật khẩu xác nhận không khớp!", "#e74c3c"); return;
        }
        // Validate Admin code
        if (isAdmin) {
            String adminCode = txtAdminCode != null ? txtAdminCode.getText().trim() : "";
            if (adminCode.isEmpty()) {
                showStatus("❌ Vui lòng nhập mã xác nhận Admin!", "#e74c3c"); return;
            }
        }

        if (btnRegister != null) btnRegister.setDisable(true);

        // ─── Gửi request ──────────────────────────────────────────────────────
        NetworkClient.getInstance().setOnResponseReceived(response -> {
            Platform.runLater(() -> {
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
