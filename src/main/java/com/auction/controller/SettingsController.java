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
    @FXML private TextField txtNewEmail;
    @FXML private PasswordField txtCurrentPassword;
    @FXML private PasswordField txtNewPassword;
    @FXML private PasswordField txtConfirmNewPassword;

    private User currentUser;

    @FXML
    public void initialize() {
        currentUser = AppContext.getCurrentUser();
        if (currentUser == null) return;

        if (lblUsername != null) lblUsername.setText(currentUser.getUserName());
        if (lblEmail != null) lblEmail.setText(currentUser.getEmail());
        if (lblRole != null) {
            String role = (currentUser instanceof com.auction.model.Admin) ? "Quản trị viên" :
                          (currentUser instanceof Seller) ? "Người bán" : "Người mua";
            lblRole.setText(role);
        }
        if (lblBalance != null) {
            if (currentUser instanceof Bidder) {
                lblBalance.setText(CurrencyFormatter.format(((Bidder) currentUser).getBalance()));
            } else if (currentUser instanceof Seller) {
                lblBalance.setText(CurrencyFormatter.format(((Seller) currentUser).getBalance()));
            } else {
                lblBalance.setText("N/A");
            }
        }

        NetworkClient.getInstance().setOnResponseReceived(response -> {
            Platform.runLater(() -> {
                if (response.getStatus() == StatusType.SUCCESS) {
                    if (response.getData() instanceof User) {
                        User updatedUser = (User) response.getData();
                        AppContext.setCurrentUser(updatedUser);
                        currentUser = updatedUser;
                        if (lblEmail != null) lblEmail.setText(currentUser.getEmail());
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
        NetworkClient.getInstance().removeOnResponseReceived();
        try {
            String fxmlPath;
            if (currentUser instanceof com.auction.model.Admin) {
                fxmlPath = "/com/auction/view/AdminDashboard.fxml";
            } else if (currentUser instanceof Seller) {
                fxmlPath = "/com/auction/view/SellerDashboard.fxml";
            } else {
                fxmlPath = "/com/auction/view/BidderDashboard.fxml";
            }
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1200, 800));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
