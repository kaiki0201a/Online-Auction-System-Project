package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.Admin;
import com.auction.model.User;
import com.auction.notification.FormValidator;
import com.auction.notification.NotificationService;
import com.auction.notification.NotificationType;
import com.auction.protocol.StatusType;
import com.auction.utils.AppContext;
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
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtPasswordVisible;   // TextField hiện mật khẩu
    @FXML private Button btnTogglePassword;       // Nút mắt
    @FXML private Button btnLogin;
    @FXML private Button btnRegister;
    @FXML private Label  lblUsernameError;        // Lỗi inline dưới ô username
    @FXML private Label  lblPasswordError;        // Lỗi inline dưới ô password
    @FXML private HBox   hboxUsername;            // Border đỏ khi username lỗi
    @FXML private HBox   hboxPassword;            // Border đỏ khi password lỗi

    private boolean passwordVisible = false;

    // Realtime form validators
    private FormValidator usernameValidator;
    private FormValidator passwordValidator;

    @FXML
    public void initialize() {
        // Realtime validation — chỉ hiện lỗi sau khi user chạm vào field
        if (txtUsername != null && lblUsernameError != null) {
            usernameValidator = FormValidator.of(txtUsername)
                    .required("Vui lòng nhập tên đăng nhập")
                    .minLength(3, "Tên đăng nhập tối thiểu 3 ký tự")
                    .withErrorLabel(lblUsernameError)
                    .attach();
        }
        if (txtPassword != null && lblPasswordError != null) {
            passwordValidator = FormValidator.of(txtPassword)
                    .required("Vui lòng nhập mật khẩu")
                    .minLength(1, "Vui lòng nhập mật khẩu")
                    .withErrorLabel(lblPasswordError)
                    .attach();
        }
    }

    /** Toggle hiển thị / ẩn mật khẩu */
    @FXML
    public void onTogglePasswordVisibility(ActionEvent event) {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            txtPasswordVisible.setText(txtPassword.getText());
            txtPasswordVisible.setVisible(true);
            txtPasswordVisible.setManaged(true);
            txtPassword.setVisible(false);
            txtPassword.setManaged(false);
            btnTogglePassword.setText("👁");   // mắt mở = đang hiện
        } else {
            txtPassword.setText(txtPasswordVisible.getText());
            txtPassword.setVisible(true);
            txtPassword.setManaged(true);
            txtPasswordVisible.setVisible(false);
            txtPasswordVisible.setManaged(false);
            btnTogglePassword.setText("🔒");  // khoá = đang ẩn
        }
    }

    /** Xoá toàn bộ lỗi inline */
    private void clearErrors() {
        setFieldError(lblUsernameError, hboxUsername, null);
        setFieldError(lblPasswordError, hboxPassword, null);
    }

    /** Hiện / ẩn lỗi inline dưới một field.
     *  msg == null → xoá lỗi; msg != null → hiện lỗi đỏ. */
    private void setFieldError(Label lbl, HBox box, String msg) {
        if (lbl != null) {
            if (msg != null) {
                lbl.setText("⚠ " + msg);
                lbl.setVisible(true);
                lbl.setManaged(true);
            } else {
                lbl.setText("");
                lbl.setVisible(false);
                lbl.setManaged(false);
            }
        }
        if (box != null) {
            if (msg != null) {
                box.setStyle(box.getStyle()
                        .replace("-fx-border-color: #333333", "")
                        .replace("-fx-border-color:#333333", "")
                        + "; -fx-border-color: #e74c3c;");
            } else {
                String s = box.getStyle()
                        .replaceAll(";?\\s*-fx-border-color:\\s*#e74c3c", "")
                        .trim();
                box.setStyle(s + "; -fx-border-color: #333333;");
            }
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

        clearErrors();

        // Validate client-side trước khi gửi request
        boolean usernameOk = (usernameValidator != null) ? usernameValidator.validate()
                : !username.isEmpty();
        boolean passwordOk = password.length() >= 1;

        if (!usernameOk) {
            if (username.isEmpty())
                setFieldError(lblUsernameError, hboxUsername, "Vui lòng nhập tên đăng nhập");
            return;
        }
        if (!passwordOk) {
            setFieldError(lblPasswordError, hboxPassword, "Vui lòng nhập mật khẩu");
            return;
        }

        // FIX C-05: dùng addEventListener("login", ...) thay vì setOnResponseReceived.
        // Mỗi lần click "Đăng Nhập" đăng ký lại key "login" (ghi đè lần trước nếu có) —
        // listener sẽ tự remove ngay sau khi nhận response đầu tiên.
        NetworkClient.getInstance().addEventListener("login", response -> {
            Platform.runLater(() -> {
                NetworkClient.getInstance().removeEventListener("login");
                if (response.getStatus() == StatusType.SUCCESS) {
                    try {
                        User userFromServer = (User) response.getData();
                        AppContext.setCurrentUser(userFromServer);
                        NotificationService.get().success("Đăng nhập thành công! Chào " + userFromServer.getUserName(), rootPane);
                        navigateToDashboard(event, userFromServer);
                    } catch (Exception e) {
                        NotificationService.get().error("Lỗi nạp giao diện: " + e.getMessage(), rootPane);
                    }
                } else {
                    String msg = response.getMessage();
                    clearErrors();
                    // Phân biệt loại lỗi để hiện đúng chỗ
                    if (msg != null && (msg.toLowerCase().contains("không tồn tại")
                            || msg.toLowerCase().contains("not found")
                            || msg.toLowerCase().contains("không tìm thấy")
                            || msg.toLowerCase().contains("user"))) {
                        setFieldError(lblUsernameError, hboxUsername, "Tài khoản không tồn tại");
                        NotificationService.get().error("❌ Tài khoản không tồn tại", rootPane);
                    } else if (msg != null && msg.toLowerCase().contains("khóa")) {
                        setFieldError(lblUsernameError, hboxUsername, "Tài khoản đã bị khóa");
                        NotificationService.get().toast("🔒 Tài khoản đã bị khóa bởi Admin", NotificationType.ERROR, rootPane, 5);
                    } else {
                        setFieldError(lblPasswordError, hboxPassword, "Sai mật khẩu");
                        NotificationService.get().error("❌ Sai mật khẩu. Vui lòng thử lại.", rootPane);
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
            NotificationService.get().error("Không thể mở trang đăng ký!", rootPane);
        }
    }

    /** Xử lý khi nhấn "Quên mật khẩu?" */
    @FXML
    private void onForgotPasswordClick(ActionEvent event) {
        // Thay thế Alert mặc định bằng dark theme alert
        NotificationService.get().alert(
                ((Node) event.getSource()).getScene().getWindow(),
                "Hướng dẫn khôi phục tài khoản",
                "Các bước đặt lại mật khẩu:\n\n" +
                        "1️⃣  Liên hệ Admin hệ thống BidPrecision\n" +
                        "2️⃣  Cung cấp tên đăng nhập và email đã đăng ký\n" +
                        "3️⃣  Admin sẽ reset và gửi mật khẩu mới cho bạn\n\n" +
                        "📧 admin@bidprecision.vn\n" +
                        "📞 Hotline: 1800-BIDPRECISION",
                NotificationType.INFO
        );
    }
}