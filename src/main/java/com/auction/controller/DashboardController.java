package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.StatusType;
import com.auction.utils.AuctionManager;
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

    // 1. Ánh xạ các linh kiện từ bảng (Thành viên A sẽ đặt fx:id tương ứng)
    @FXML private TableView<Auction> tableAuctions;
    @FXML private TableColumn<Auction, String> colId;
    @FXML private TableColumn<Auction, String> colProductName;
    @FXML private TableColumn<Auction, Double> colCurrentPrice;
    @FXML private TableColumn<Auction, String> colStatus;
    @FXML private Button btnCreateAuction;

    // Thêm vào phần khai báo biến @FXML
    @FXML private TableColumn<Auction, java.time.LocalDateTime> colEndTime;

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
        colEndTime.setCellValueFactory(new PropertyValueFactory<>("endTime"));

        // XÓA loadMockData();
        // THAY BẰNG: Đăng ký nhận danh sách từ mạng
        NetworkClient.getInstance().setOnResponseReceived(response -> {
            // SỬA LẠI: Kiểm tra qua getMessage thay vì getAction
            if ("Danh sách phiên đấu giá".equals(response.getMessage())) {
                List<Auction> auctions = (List<Auction>) response.getData();
                auctionData.setAll(auctions);
            }

            // Nếu ai đó đặt giá thành công (Server broadcast UPDATE_AUCTION), cập nhật lại bảng
            if ("UPDATE_AUCTION".equals(response.getMessage())) {
                NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
            }
        });

        // Gửi lệnh lên Server đòi danh sách
        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));

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

    // --- HÀM MỚI BỔ SUNG ĐỂ MỞ MÀN HÌNH TẠO ĐẤU GIÁ ---
    @FXML
    public void onCreateAuctionClick(ActionEvent event) {
        try {
            // Bạn cần bảo bạn A làm thêm file CreateAuctionView.fxml này hoặc tự tạo file trống để test
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/CreateAuctionView.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Đăng sản phẩm đấu giá mới");
            stage.initModality(Modality.APPLICATION_MODAL); // Hiện pop-up khóa màn hình chính
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            showError("Lỗi", "Không thể mở màn hình đăng sản phẩm. Kiểm tra file CreateAuctionView.fxml");
        }
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/AuctionDetail.fxml"));
            Parent root = loader.load();

            // BƯỚC QUYẾT ĐỊNH: Lấy Controller của màn hình Detail và truyền dữ liệu sang
            AuctionDetailController detailController = loader.getController();
            if (currentUser instanceof Bidder) {
                detailController.setAuctionData(auction, (Bidder) currentUser);
            } else {
                detailController.setAuctionData(auction, null); // Hoặc xử lý theo logic Seller xem hàng
            }

            Stage stage = (Stage) tableAuctions.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (IOException e) {
            e.printStackTrace(); // Tạm thời in lỗi ra console để dễ debug nếu vẫn bị sai đường dẫn
            showError("Lỗi", "Không thể mở chi tiết phiên đấu giá." + e.getMessage());
        }
    }
    // Thêm dòng này vào DashboardController
    private com.auction.model.User currentUser;

    // Thêm hàm này để LoginController có thể "gửi" User sang cho Dashboard
    public void setUser(com.auction.model.User user) {
        this.currentUser = user;

        // Phân quyền hiển thị
        if (user instanceof com.auction.model.Bidder) {
            btnCreateAuction.setVisible(false); // Người mua không thấy nút đăng bài
            btnCreateAuction.setManaged(false); // Xóa khoảng trống của nút
        } else {
            btnCreateAuction.setVisible(true);
            btnCreateAuction.setManaged(true);
        }
        System.out.println("Dashboard đã nhận người dùng: " + user.getUserName());
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    public void onProfileClick(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/Userprofile.fxml"));
            Parent root = loader.load();

            // Lấy controller của màn hình Profile và truyền User hiện tại sang
            UserProfileController profileController = loader.getController();

            // Ép kiểu currentUser về Bidder để hiển thị lịch sử (vì User chung không có lịch sử đấu giá)
            if (currentUser instanceof Bidder) {
                profileController.setUserData((Bidder) currentUser);
            }

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Thông tin tài khoản - " + currentUser.getUserName());
        } catch (IOException e) {
            e.printStackTrace();
            showError("Lỗi", "Không thể mở trang cá nhân.");
        }
    }
}