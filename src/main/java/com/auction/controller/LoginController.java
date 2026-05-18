package com.auction.controller; // Dòng này luôn ở đầu file

// Khu vực 1: Import các thư viện cần thiết
import com.auction.client.NetworkClient;
import com.auction.protocol.StatusType;
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

        // Đăng ký "tai nghe" để đợi Server trả lời
        NetworkClient.getInstance().setOnResponseReceived(response -> {
            if (response.getStatus() == StatusType.SUCCESS) {
                try {
                    // BÂY GIỜ ĐOẠN NÀY SẼ KHÔNG BỊ NULL NỮA
                    com.auction.model.User userFromServer = (com.auction.model.User) response.getData();
                    navigateToDashboard(event, userFromServer);
                } catch (IOException e) {
                    showError("Lỗi", "Lỗi nạp giao diện Dashboard.");
                }
            } else {
                showError("Thất bại", response.getMessage());
            }
        });

        // Gọi hàm gửi yêu cầu đăng nhập
        NetworkClient.getInstance().login(username, password);
    }

    // Khu vực 4: Hàm bổ trợ - Chuyển màn hình (Navigation)
    private void navigateToDashboard(ActionEvent event, com.auction.model.User user) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/Dashboard.fxml"));
        Parent root = loader.load();
        DashboardController dashboardController = loader.getController();

        dashboardController.setUser(user); // Truyền User thật

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