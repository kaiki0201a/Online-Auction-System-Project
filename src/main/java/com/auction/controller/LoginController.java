package com.auction.controller; // Dòng này luôn ở đầu file

// Khu vực 1: Import các thư viện cần thiết
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

        // Giả lập logic kiểm tra (sau này sẽ gọi bạn C)
        if (username.equals("admin") && password.equals("123")) {
            try {
                // Nếu đúng, gọi hàm chuyển sang màn hình Dashboard
                navigateToDashboard(event);
            } catch (IOException e) {
                showError("Lỗi hệ thống", "Không thể tải màn hình Dashboard.");
            }
        } else {
            // Nếu sai, hiện thông báo lỗi
            showError("Đăng nhập thất bại", "Tài khoản hoặc mật khẩu không đúng!");
        }
    }

    // Khu vực 4: Hàm bổ trợ - Chuyển màn hình (Navigation)
    private void navigateToDashboard(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/DashboardView.fxml"));
        Parent root = loader.load(); // Phải load trước khi lấy Controller

        // BƯỚC QUAN TRỌNG: Lấy DashboardController và truyền User sang
        DashboardController dashboardController = loader.getController();

        // Giả lập tạo một đối tượng Bidder (Vì login admin/123 thành công)
        com.auction.model.Bidder mockUser = new com.auction.model.Bidder(txtUsername.getText(), "", "admin@test.com", 5000.0);
        dashboardController.setUser(mockUser);

        Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
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