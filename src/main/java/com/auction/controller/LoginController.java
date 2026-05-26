package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.Admin;
import com.auction.model.User;
import com.auction.protocol.StatusType;
import com.auction.utils.AppContext;
import com.auction.utils.NotificationUtil;
import com.auction.utils.UIUtils;
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
    @FXML private HBox hboxUsername;
    @FXML private HBox hboxPassword;
    @FXML private Label lblUsernameError;
    @FXML private Label lblPasswordError;
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtPasswordVisible;   // TextField hiện mật khẩu
    @FXML private Button btnTogglePassword;       // Nút mắt
    @FXML private Button btnLogin;
    @FXML private Button btnRegister;

    private boolean passwordVisible = false;

    @FXML
    public void initialize() {
        // Xóa lỗi khi người dùng bắt đầu gõ lại
        txtUsername.textProperty().addListener((obs, o, n) -> clearUsernameError());
        txtPassword.textProperty().addListener((obs, o, n) -> clearPasswordError());
        txtPasswordVisible.textProperty().addListener((obs, o, n) -> clearPasswordError());
    }

    // ==================== Helper hiển thị / ẩn lỗi ====================

    private static final String STYLE_BORDER_NORMAL = "-fx-background-color: #1a1a1a; -fx-border-color: #333333; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 0 10 0 15;";
    private static final String STYLE_BORDER_ERROR  = "-fx-background-color: #1a1a1a; -fx-border-color: #FF4444; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 0 10 0 15;";
    private static final String STYLE_BORDER_PASS_NORMAL = "-fx-background-color: #1a1a1a; -fx-border-color: #333333; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 0 5 0 15;";
    private static final String STYLE_BORDER_PASS_ERROR  = "-fx-background-color: #1a1a1a; -fx-border-color: #FF4444; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 0 5 0 15;";

    private void showUsernameError(String msg) {
        lblUsernameError.setText(msg);
        lblUsernameError.setVisible(true);
        lblUsernameError.setManaged(true);
        hboxUsername.setStyle(STYLE_BORDER_ERROR);
    }

    private void clearUsernameError() {
        lblUsernameError.setVisible(false);
        lblUsernameError.setManaged(false);
        hboxUsername.setStyle(STYLE_BORDER_NORMAL);
    }

    private void showPasswordError(String msg) {
        lblPasswordError.setText(msg);
        lblPasswordError.setVisible(true);
        lblPasswordError.setManaged(true);
        hboxPassword.setStyle(STYLE_BORDER_PASS_ERROR);
    }

    private void clearPasswordError() {
        lblPasswordError.setVisible(false);
        lblPasswordError.setManaged(false);
        hboxPassword.setStyle(STYLE_BORDER_PASS_NORMAL);
    }

    private void clearAllErrors() {
        clearUsernameError();
        clearPasswordError();
    }

    /** Toggle hiển thị / ẩn mật khẩu */
    @FXML
    public void onTogglePasswordVisibility(ActionEvent event) {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            // Sao chép giá trị sang TextField rồi hiện lên
            txtPasswordVisible.setText(txtPassword.getText());
            txtPasswordVisible.setVisible(true);
            txtPasswordVisible.setManaged(true);
            txtPassword.setVisible(false);
            txtPassword.setManaged(false);
            btnTogglePassword.setText("🙈");
        } else {
            // Sao chép giá trị về PasswordField
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

    @FXML
    public void onLoginClick(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = getCurrentPassword();
        clearAllErrors();

        // Kiểm tra trường rỗng
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

        NetworkClient.getInstance().setOnResponseReceived(response -> {
            Platform.runLater(() -> {
                if (response.getStatus() == StatusType.SUCCESS) {
                    try {
                        User userFromServer = (User) response.getData();
                        AppContext.setCurrentUser(userFromServer);
                        NotificationUtil.showToast("Đăng nhập thành công!", rootPane, "success");
                        navigateToDashboard(event, userFromServer);
                    } catch (Exception e) {
                        NotificationUtil.showToast("Lỗi nạp giao diện: " + e.getMessage(), rootPane, "error");
                    }
                } else {
                    // Phân loại lỗi từ server → hiển thị inline đúng chỗ
                    String msg = response.getMessage();
                    if (msg == null) msg = "Lỗi không xác định";
                    String msgLower = msg.toLowerCase();
                    if (msgLower.contains("không tồn tại")
                            || msgLower.contains("not found")
                            || msgLower.contains("username")
                            || msgLower.contains("tài khoản") && !msgLower.contains("mật khẩu")) {
                        showUsernameError("Tài khoản không tồn tại");
                    } else if (msgLower.contains("mật khẩu")
                            || msgLower.contains("password")
                            || msgLower.contains("sai")) {
                        showPasswordError("Mật khẩu chưa chính xác");
                    } else {
                        // Lỗi khác: hiển thị ở password
                        showPasswordError(msg);
                    }
                }
            });
        });

        UIUtils.showLoadingSpinner(rootPane, () ->
            NetworkClient.getInstance().login(username, password)
        );
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
            NotificationUtil.showToast("Không thể mở trang đăng ký!", rootPane, "error");
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