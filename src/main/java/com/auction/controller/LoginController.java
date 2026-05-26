package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.Admin;
import com.auction.model.User;
import com.auction.protocol.StatusType;
import com.auction.utils.AppContext;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import java.io.IOException;

public class LoginController {

    @FXML private StackPane rootPane;
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtPasswordVisible;   // TextField hiện mật khẩu
    @FXML private Button btnTogglePassword;       // Nút mắt
    @FXML private Button btnLogin;
    @FXML private Button btnRegister;

    // Labels lỗi inline
    @FXML private HBox usernameBox;
    @FXML private HBox passwordBox;
    @FXML private Label lblUsernameError;
    @FXML private Label lblPasswordError;
    @FXML private Label lblLoginError;

    private boolean passwordVisible = false;

    @FXML
    public void initialize() {
        // Xóa lỗi khi người dùng bắt đầu nhập lại
        txtUsername.textProperty().addListener((obs, oldVal, newVal) -> clearUsernameError());
        txtPassword.textProperty().addListener((obs, oldVal, newVal) -> clearPasswordError());
        txtPasswordVisible.textProperty().addListener((obs, oldVal, newVal) -> clearPasswordError());
    }

    // ── Hiển thị / xóa lỗi ────────────────────────────────────────────────────

    private void showUsernameError(String message) {
        lblUsernameError.setText(message);
        lblUsernameError.setVisible(true);
        lblUsernameError.setManaged(true);
        usernameBox.setStyle(usernameBox.getStyle().replace("-fx-border-color: #333333", "")
            + "-fx-border-color: #FF5252; -fx-border-radius: 4; -fx-background-radius: 4; "
            + "-fx-background-color: #1a1a1a; -fx-padding: 0 10 0 15;");
    }

    private void clearUsernameError() {
        lblUsernameError.setVisible(false);
        lblUsernameError.setManaged(false);
        usernameBox.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #333333; "
            + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 0 10 0 15;");
        clearLoginError();
    }

    private void showPasswordError(String message) {
        lblPasswordError.setText(message);
        lblPasswordError.setVisible(true);
        lblPasswordError.setManaged(true);
        passwordBox.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #FF5252; "
            + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 0 5 0 15;");
    }

    private void clearPasswordError() {
        lblPasswordError.setVisible(false);
        lblPasswordError.setManaged(false);
        passwordBox.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #333333; "
            + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 0 5 0 15;");
        clearLoginError();
    }

    private void showLoginError(String message) {
        lblLoginError.setText(message);
        lblLoginError.setVisible(true);
        lblLoginError.setManaged(true);
        // Highlight cả 2 box khi sai thông tin đăng nhập
        usernameBox.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #FF5252; "
            + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 0 10 0 15;");
        passwordBox.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #FF5252; "
            + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 0 5 0 15;");
    }

    private void clearLoginError() {
        lblLoginError.setVisible(false);
        lblLoginError.setManaged(false);
    }

    private void clearAllErrors() {
        clearUsernameError();
        clearPasswordError();
        clearLoginError();
    }

    // ── Toggle hiển thị mật khẩu ──────────────────────────────────────────────

    @FXML
    public void onTogglePasswordVisibility(ActionEvent event) {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            txtPasswordVisible.setText(txtPassword.getText());
            txtPasswordVisible.setVisible(true);
            txtPasswordVisible.setManaged(true);
            txtPassword.setVisible(false);
            txtPassword.setManaged(false);
            btnTogglePassword.setText("🙈");
        } else {
            txtPassword.setText(txtPasswordVisible.getText());
            txtPassword.setVisible(true);
            txtPassword.setManaged(true);
            txtPasswordVisible.setVisible(false);
            txtPasswordVisible.setManaged(false);
            btnTogglePassword.setText("👁");
        }
    }

    /** Lấy mật khẩu từ field đang hiển thị */
    private String getCurrentPassword() {
        return passwordVisible ? txtPasswordVisible.getText() : txtPassword.getText();
    }

    // ── Xử lý đăng nhập ───────────────────────────────────────────────────────

    @FXML
    public void onLoginClick(ActionEvent event) {
        clearAllErrors();

        String username = txtUsername.getText().trim();
        String password = getCurrentPassword();

        // Validate từng trường — hiển thị lỗi inline
        boolean hasError = false;
        if (username.isEmpty()) {
            showUsernameError("Vui lòng nhập tên đăng nhập");
            hasError = true;
        }
        if (password.isEmpty()) {
            showPasswordError("Vui lòng nhập mật khẩu");
            hasError = true;
        }
        if (hasError) return;

        // Disable nút, đổi text để báo đang xử lý
        btnLogin.setDisable(true);
        btnLogin.setText("Đang đăng nhập...");

        NetworkClient.getInstance().setOnResponseReceived(response -> {
            Platform.runLater(() -> {
                // Khôi phục nút
                btnLogin.setDisable(false);
                btnLogin.setText("ĐĂNG NHẬP  ➔");

                if (response.getStatus() == StatusType.SUCCESS) {
                    try {
                        User userFromServer = (User) response.getData();
                        AppContext.setCurrentUser(userFromServer);
                        navigateToDashboard(event, userFromServer);
                    } catch (Exception e) {
                        showLoginError("Lỗi nạp giao diện: " + e.getMessage());
                    }
                } else {
                    // Hiển thị lỗi server (sai tài khoản / mật khẩu) ngay trên form
                    String msg = response.getMessage();
                    if (msg == null || msg.isBlank()) {
                        msg = "Tên đăng nhập hoặc mật khẩu không đúng";
                    }
                    showLoginError(msg);
                }
            });
        });

        // Gọi login trực tiếp — không dùng spinner overlay để tránh che label lỗi
        NetworkClient.getInstance().login(username, password);
    }

    private void navigateToDashboard(ActionEvent event, User user) throws IOException {
        String fxmlPath;
        if (user instanceof Admin) {
            fxmlPath = "/com/auction/view/AdminDashboard.fxml";
        } else if (user instanceof com.auction.model.Seller) {
            fxmlPath = "/com/auction/view/SellerDashboard.fxml";
        } else {
            fxmlPath = "/com/auction/view/BidderDashboard.fxml";
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root, 1280, 800));
        stage.centerOnScreen();
    }

    @FXML
    private void onRegisterClick(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Register.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 900, 620));
        } catch (IOException e) {
            showLoginError("Không thể mở trang đăng ký!");
        }
    }

    /** Xử lý khi nhấn "Quên mật khẩu?" */
    @FXML
    private void onForgotPasswordClick(ActionEvent event) {
        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle("Quên mật khẩu?");
        dlg.setHeaderText("Hướng dẫn khôi phục tài khoản");
        dlg.setContentText(
            "Để đặt lại mật khẩu, vui lòng:\n\n" +
            "1. Liên hệ Admin hệ thống BIDPRECISION\n" +
            "2. Cung cấp tên đăng nhập và email đã đăng ký\n" +
            "3. Admin sẽ reset mật khẩu và gửi lại cho bạn\n\n" +
            "📧 Liên hệ: admin@bidprecision.vn\n" +
            "📞 Hotline: 1800-BIDPRECISION"
        );
        dlg.showAndWait();
    }
}