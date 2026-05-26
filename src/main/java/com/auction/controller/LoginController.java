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

        if (username.isEmpty()) {
            setFieldError(lblUsernameError, hboxUsername, "Vui lòng nhập tên đăng nhập");
            return;
        }
        if (password.isEmpty()) {
            setFieldError(lblPasswordError, hboxPassword, "Vui lòng nhập mật khẩu");
            return;
        }

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
                    String msg = response.getMessage();
                    // Phân biệt loại lỗi để hiện đúng chỗ
                    if (msg != null && (msg.toLowerCase().contains("không tồn tại")
                            || msg.toLowerCase().contains("not found")
                            || msg.toLowerCase().contains("không tìm thấy"))) {
                        setFieldError(lblUsernameError, hboxUsername, "Tài khoản không tồn tại");
                    } else {
                        setFieldError(lblPasswordError, hboxPassword, "Sai mật khẩu");
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