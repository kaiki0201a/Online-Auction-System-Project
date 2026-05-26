package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.StatusType;
import com.auction.utils.AppContext;
import com.auction.utils.CurrencyFormatter;
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
import javafx.stage.Stage;

import java.io.IOException;

public class SettingsController {

    @FXML private StackPane rootPane;
    @FXML private Label lblUsername;
    @FXML private Label lblEmail;
    @FXML private Label lblRole;
    @FXML private Label lblBalance;
    @FXML private Label lblAvatarInitials; // Avatar initials circle
    @FXML private TextField txtNewEmail;
    @FXML private PasswordField txtCurrentPassword;
    @FXML private PasswordField txtNewPassword;
    @FXML private PasswordField txtConfirmNewPassword;

    private static final String LISTENER_KEY = "settings";
    private User currentUser;

    @FXML
    public void initialize() {
        currentUser = AppContext.getCurrentUser();
        if (currentUser == null) return;

        String name = currentUser.getUserName();
        if (lblUsername    != null) lblUsername.setText(name);
        if (lblEmail       != null) lblEmail.setText(currentUser.getEmail());
        if (lblAvatarInitials != null)
            lblAvatarInitials.setText(name.substring(0, 1).toUpperCase());

        if (lblRole != null) {
            String role = (currentUser instanceof com.auction.model.Admin) ? "Quản trị viên" :
                          (currentUser instanceof Seller) ? "Người bán" : "Người mua";
            lblRole.setText(role);
        }
        if (lblBalance != null) {
            if (currentUser instanceof Bidder bid) {
                lblBalance.setText(CurrencyFormatter.format(bid.getBalance()));
            } else if (currentUser instanceof Seller sel) {
                lblBalance.setText(CurrencyFormatter.format(sel.getBalance()));
            } else {
                lblBalance.setText("N/A (Admin)");
            }
        }

        // Dùng addEventListener (không ghi đè listener khác)
        NetworkClient.getInstance().addEventListener(LISTENER_KEY, response -> {
            Platform.runLater(() -> {
                if (response.getStatus() == StatusType.SUCCESS) {
                    if (response.getData() instanceof User updatedUser) {
                        AppContext.setCurrentUser(updatedUser);
                        currentUser = updatedUser;
                        if (lblEmail != null) lblEmail.setText(currentUser.getEmail());
                        // Cập nhật avatar initials sau khi đổi tên (nếu có)
                        if (lblAvatarInitials != null)
                            lblAvatarInitials.setText(currentUser.getUserName().substring(0,1).toUpperCase());
                    }
                    NotificationUtil.showToast(response.getMessage(), rootPane, "success");
                } else {
                    NotificationUtil.showToast(response.getMessage(), rootPane, "error");
                }
            });
        });
    }

    @FXML
    public void onUpdateEmailClick(ActionEvent event) {
        String newEmail = txtNewEmail != null ? txtNewEmail.getText().trim() : "";
        if (newEmail.isEmpty() || !newEmail.contains("@")) {
            NotificationUtil.showToast("Email không hợp lệ!", rootPane, "warning");
            return;
        }
        // Gửi cập nhật lên server: username|newEmail|<password_rỗng>
        String payload = currentUser.getUserName() + "|" + newEmail + "|";
        NetworkClient.getInstance().sendRequest(new Request(ActionType.UPDATE_PROFILE, payload));
        if (txtNewEmail != null) txtNewEmail.clear();
    }

    @FXML
    public void onChangePasswordClick(ActionEvent event) {
        String currentPass = txtCurrentPassword != null ? txtCurrentPassword.getText() : "";
        String newPass = txtNewPassword != null ? txtNewPassword.getText() : "";
        String confirmPass = txtConfirmNewPassword != null ? txtConfirmNewPassword.getText() : "";

        if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            NotificationUtil.showToast("Vui lòng điền đầy đủ thông tin mật khẩu!", rootPane, "warning");
            return;
        }
        if (newPass.length() < 6) {
            NotificationUtil.showToast("Mật khẩu mới phải có ít nhất 6 ký tự!", rootPane, "warning");
            return;
        }
        if (!newPass.equals(confirmPass)) {
            NotificationUtil.showToast("Mật khẩu xác nhận không khớp!", rootPane, "error");
            return;
        }

        // Gửi cập nhật: username|<email_rỗng>|newPassword
        String payload = currentUser.getUserName() + "||" + newPass;
        NetworkClient.getInstance().sendRequest(new Request(ActionType.UPDATE_PROFILE, payload));

        if (txtCurrentPassword != null) txtCurrentPassword.clear();
        if (txtNewPassword != null) txtNewPassword.clear();
        if (txtConfirmNewPassword != null) txtConfirmNewPassword.clear();
    }

    @FXML
    public void onBackClick(ActionEvent event) {
        NetworkClient.getInstance().removeEventListener(LISTENER_KEY);
        try {
            String fxmlPath;
            int w = 1280, h = 800;
            if (currentUser instanceof com.auction.model.Admin) {
                fxmlPath = "/com/auction/view/AdminDashboard.fxml";
                w = 1350; h = 900;
            } else if (currentUser instanceof Seller) {
                fxmlPath = "/com/auction/view/SellerDashboard.fxml";
            } else {
                fxmlPath = "/com/auction/view/BidderDashboard.fxml";
            }
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, w, h));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
