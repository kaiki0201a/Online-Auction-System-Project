package com.auction.controller;

import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.utils.AuctionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import java.io.IOException;

public class DashboardController {

    // 1. Ánh xạ các linh kiện từ bảng (Thành viên A sẽ đặt fx:id tương ứng)
    @FXML private TableView<Auction> tableAuctions;
    @FXML private TableColumn<Auction, String> colId;
    @FXML private TableColumn<Auction, String> colProductName;
    @FXML private TableColumn<Auction, Double> colCurrentPrice;
    @FXML private TableColumn<Auction, String> colStatus;

    // 2. Danh sách quan sát (ObservableList) - Tự động cập nhật bảng khi dữ liệu thay đổi
    private ObservableList<Auction> auctionData = FXCollections.observableArrayList();

    /**
     * Hàm initialize() chạy ngay khi màn hình Dashboard hiện lên
     */
    @FXML
    public void initialize() {
        // Bước A: Cấu hình các cột (Nói cho Java biết mỗi cột lấy dữ liệu từ hàm nào của lớp Auction)
        colId.setCellValueFactory(new PropertyValueFactory<>("auctionId")); // Gọi getAuctionId()
        colProductName.setCellValueFactory(new PropertyValueFactory<>("item")); // Sẽ hiển thị qua toString() của Item
        colCurrentPrice.setCellValueFactory(new PropertyValueFactory<>("currentHighestBid"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Bước B: Nạp dữ liệu (Tạm thời dùng dữ liệu giả để không phải đợi bạn C)
        loadMockData();

        // Bước C: Gắn danh sách vào bảng
        tableAuctions.setItems(auctionData);
    }

    private void loadMockData() {
        // Ở đây bạn tự tạo dữ liệu để test giao diện
        // Sau này khi Thành viên C xong, bạn sẽ thay bằng: auctionData.addAll(AuctionManager.getInstance().getAllAuctions());
        System.out.println("Đang nạp dữ liệu giả để kiểm tra giao diện...");
        // Tạo thử các vật phẩm giả
        com.auction.model.Art art = new com.auction.model.Art("Tranh sơn dầu", "Đẹp", 1000, "Picasso", 1920);
        com.auction.model.Seller seller = new com.auction.model.Seller("Seller01", "123", "seller@test.com");

        // Tạo phiên đấu giá giả
        Auction mockAuction = new Auction(art, seller, java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusDays(1));

        // Thêm vào danh sách hiển thị
        auctionData.add(mockAuction);
    }

    @FXML
    public void onTableClick(MouseEvent event) {
        if (event.getClickCount() == 2) { // Kiểm tra nếu người dùng click đúp
            Auction selected = tableAuctions.getSelectionModel().getSelectedItem();
            if (selected != null) {
                openAuctionDetail(selected);
            }
        }
    }

    private void openAuctionDetail(Auction auction) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/AuctionDetailView.fxml"));
            Parent root = loader.load();

            // BƯỚC QUYẾT ĐỊNH: Lấy Controller của màn hình Detail và truyền dữ liệu sang
            AuctionDetailController detailController = loader.getController();
            detailController.setAuctionData(auction, (Bidder) currentUser); // currentUser lấy từ lúc Login

            Stage stage = (Stage) tableAuctions.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (IOException e) {
            showError("Lỗi", "Không thể mở chi tiết phiên đấu giá.");
        }
    }
    // Thêm dòng này vào DashboardController
    private com.auction.model.User currentUser;

    // Thêm hàm này để LoginController có thể "gửi" User sang cho Dashboard
    public void setUser(com.auction.model.User user) {
        this.currentUser = user;
        System.out.println("Dashboard đã nhận người dùng: " + user.getUserName());
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}