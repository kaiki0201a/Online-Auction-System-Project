package com.auction.controller;

import com.auction.model.Bidder;
import com.auction.model.BidTransaction;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.event.ActionEvent;
import java.io.IOException;

public class UserProfileController {

    @FXML private Label lblUsername;
    @FXML private Label lblBalance;
    @FXML private TableView<BidTransaction> tblHistory;
    @FXML private TableColumn<BidTransaction, String> colHisTime;
    @FXML private TableColumn<BidTransaction, String> colHisItem;
    @FXML private TableColumn<BidTransaction, Double> colHisAmount;
    @FXML private TableColumn<BidTransaction, String> colHisStatus;

    private Bidder currentUser;

    @FXML
    public void initialize() {
        colHisTime.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        colHisItem.setCellValueFactory(new PropertyValueFactory<>("auction"));
        colHisAmount.setCellValueFactory(new PropertyValueFactory<>("bidAmount"));
        colHisStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // BỔ SUNG: Custom lại cách hiển thị của cột tiền
        colHisAmount.setCellFactory(tc -> new TableCell<BidTransaction, Double>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText(com.auction.utils.CurrencyFormatter.format(price));
                }
            }
        });

        // BỔ SUNG: Trạng thái rỗng khi bảng không có dữ liệu
        Label emptyLabel = new Label("Hiện chưa có lịch sử đấu giá nào");
        emptyLabel.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
        tblHistory.setPlaceholder(emptyLabel);
    }

    // Hàm quan trọng để Dashboard "đẩy" dữ liệu sang
    public void setUserData(Bidder user) {
        this.currentUser = user;
        refreshUI();
    }

    private void refreshUI() {
        if (currentUser != null) {
            lblUsername.setText(currentUser.getUserName());

            // SỬ DỤNG HÀM TIỆN ÍCH ĐỂ FORMAT SỐ DƯ
            lblBalance.setText(com.auction.utils.CurrencyFormatter.format(currentUser.getBalance()));

            ObservableList<BidTransaction> history = FXCollections.observableArrayList(currentUser.getTransactionHistory());
            tblHistory.setItems(history);
        }
    }

    @FXML
    public void onLogoutClick(ActionEvent event) {
        try {
            // Quay lại màn hình Login
            Parent loginView = FXMLLoader.load(getClass().getResource("/com/auction/view/Login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(loginView));
            stage.setTitle("Đăng nhập hệ thống");
            stage.show();
            System.out.println("Đã đăng xuất thành công.");
        } catch (IOException e) {
            System.err.println("Lỗi khi chuyển sang màn hình Login: " + e.getMessage());
        }
    }
}