package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.User;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.StatusType;
import com.auction.utils.AppContext;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class DashboardController {

    @FXML private TableView<Auction> tableAuctions;
    @FXML private TableColumn<Auction, String> colId;
    @FXML private TableColumn<Auction, String> colProductName;
    @FXML private TableColumn<Auction, Double> colCurrentPrice;
    @FXML private TableColumn<Auction, String> colStatus;
    @FXML private TableColumn<Auction, java.time.LocalDateTime> colEndTime;
    @FXML private Button btnCreateAuction;
    @FXML private Node rootPane;

    private User currentUser;
    private ObservableList<Auction> auctionData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // LẤY SESSION NGƯỜI DÙNG TỪ APPCONTEXT
        currentUser = AppContext.getCurrentUser();

        // Phân quyền
        boolean isBidder = currentUser instanceof Bidder;
        btnCreateAuction.setVisible(!isBidder);
        btnCreateAuction.setManaged(!isBidder);

        // Cấu hình bảng
        colId.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
        colProductName.setCellValueFactory(new PropertyValueFactory<>("item"));
        colCurrentPrice.setCellValueFactory(new PropertyValueFactory<>("currentHighestBid"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colEndTime.setCellValueFactory(new PropertyValueFactory<>("endTime"));

        // Lắng nghe mạng
        NetworkClient.getInstance().setOnResponseReceived(response -> {
            Platform.runLater(() -> {
                if (response.getStatus() == StatusType.SUCCESS && response.getData() instanceof List) {
                    List<Auction> auctions = (List<Auction>) response.getData();
                    auctionData.setAll(auctions);
                }
                if ("UPDATE_AUCTION".equals(response.getMessage())) {
                    NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
                }
            });
        });

        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
        tableAuctions.setItems(auctionData);
    }

    @FXML
    public void onCreateAuctionClick(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/AddProduct.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Đăng sản phẩm đấu giá mới");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            showError("Lỗi", "Không thể mở màn hình đăng sản phẩm.");
        }
    }

    @FXML
    public void onTableClick(MouseEvent event) {
        if (event.getClickCount() == 2) {
            Auction selected = tableAuctions.getSelectionModel().getSelectedItem();
            if (selected != null) {
                openAuctionDetail(selected);
            }
        }
    }

    private void openAuctionDetail(Auction auction) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/AuctionDetail.fxml"));
            Parent root = loader.load();

            AuctionDetailController detailController = loader.getController();
            detailController.setAuctionData(auction); // Gửi duy nhất Auction, Detail sẽ tự móc User từ AppContext

            Stage stage = (Stage) tableAuctions.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (IOException e) {
            showError("Lỗi", "Không thể mở chi tiết phiên đấu giá: " + e.getMessage());
        }
    }

    @FXML
    public void onProfileClick(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/UserProfile.fxml")); // Viết hoa chữ P cho chuẩn
            Parent root = loader.load();

            UserProfileController profileController = loader.getController();
            if (currentUser instanceof Bidder) {
                profileController.setUserData((Bidder) currentUser);
            }

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Thông tin tài khoản - " + currentUser.getUserName());
        } catch (IOException e) {
            showError("Lỗi", "Không thể mở trang cá nhân.");
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}