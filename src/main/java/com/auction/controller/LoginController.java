package com.auction.controller; // Dòng này luôn ở đầu file

// Khu vực 1: Import các thư viện cần thiết
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
import java.io.IOException;

public class LoginController {
    @FXML private Node rootPane;
    // Khu vực 2: Khai báo biến ánh xạ từ giao diện (Thành viên A đặt fx:id)
    @FXML
    private TextField txtUsername; // Nơi nhập tên
    @FXML
    private PasswordField txtPassword; // Nơi nhập mật khẩu

    // Khu vực 3: Hàm xử lý khi người dùng nhấn nút Đăng nhập
    @FXML
    public void onLoginClick(ActionEvent event) {
        String username = txtUsername.getText();
        String password = txtPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            NotificationUtil.showToast("Vui lòng nhập tài khoản và mật khẩu!", rootPane, "warning");
            return;
        }

        // Đăng ký "tai nghe" đợi Server trả lời
        NetworkClient.getInstance().setOnResponseReceived(response -> {

            // 🚨 BẮT BUỘC: Đẩy việc cập nhật UI về luồng chính của JavaFX
            Platform.runLater(() -> {
                if (response.getStatus() == StatusType.SUCCESS) {
                    try {
                        User userFromServer = (User) response.getData();

                        // LƯU KÉT SẮT (SESSION)
                        AppContext.setCurrentUser(userFromServer);

                        NotificationUtil.showToast("Đăng nhập thành công!", rootPane, "success");
                        // Chuyển màn hình
                        navigateToDashboard(event, userFromServer);
                    } catch (Exception e) {
                        NotificationUtil.showToast("Lỗi nạp giao diện!", rootPane, "error");
                    }
                } else {
                    NotificationUtil.showToast(response.getMessage(), rootPane, "error");
                }
            });
        });

        // Hiện vòng xoay Loading chờ Server
        UIUtils.showLoadingSpinner((javafx.scene.layout.StackPane) rootPane, () -> {
            // Gọi hàm gửi yêu cầu mạng sau khi UI Loading hiện lên
            NetworkClient.getInstance().login(username, password);
        });
    }
    // Hàm bổ trợ - Phân quyền và Chuyển màn hình
    private void navigateToDashboard(ActionEvent event, com.auction.model.User user) throws IOException {
        String fxmlPath = (user instanceof Admin) ? "/com/auction/view/AdminDashboard.fxml" : "/com/auction/view/Dashboard.fxml";

        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Parent root = loader.load();

        // ĐÃ XÓA logic gọi dashboardController.setUser() vì đã có AppContext lo

        Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
    }

    // Khu vực 5: Hàm bổ trợ - Hiện thông báo (Alert)
    public void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}