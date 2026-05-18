package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.User;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.StatusType;
import com.auction.utils.AppContext;
import com.auction.utils.AuctionManager;
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

    // 1. Ánh xạ các linh kiện từ bảng (Thành viên A sẽ đặt fx:id tương ứng)
    @FXML private TableView<Auction> tableAuctions;
    @FXML private TableColumn<Auction, String> colId;
    @FXML private TableColumn<Auction, String> colProductName;
    @FXML private TableColumn<Auction, Double> colCurrentPrice;
    @FXML private TableColumn<Auction, String> colStatus;
    @FXML private Button btnCreateAuction;

    // Thêm vào phần khai báo biến @FXML
    @FXML private TableColumn<Auction, java.time.LocalDateTime> colEndTime;
    // Thêm một biến Node để gắn Toast (Cần đảm bảo Dashboard.fxml có id này ở phần tử gốc)
    @FXML private Node rootPane;

    private User currentUser; // Lấy từ Session

    // 2. Danh sách quan sát (ObservableList) - Tự động cập nhật bảng khi dữ liệu thay đổi
    private ObservableList<Auction> auctionData = FXCollections.observableArrayList();

    /**
     * Hàm initialize() chạy ngay khi màn hình Dashboard hiện lên
     */
    @FXML
    public void initialize() {
        // 1. LẤY SESSION NGƯỜI DÙNG (Không cần hàm setUser bên ngoài truyền vào nữa)
        currentUser = AppContext.getCurrentUser();

        // 2. PHÂN QUYỀN GIAO DIỆN
        if (currentUser instanceof Bidder) {
            btnCreateAuction.setVisible(false);
            btnCreateAuction.setManaged(false);
        } else {
            btnCreateAuction.setVisible(true);
            btnCreateAuction.setManaged(true);
        }

        // 3. CẤU HÌNH BẢNG
        colId.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
        colProductName.setCellValueFactory(new PropertyValueFactory<>("item"));
        colCurrentPrice.setCellValueFactory(new PropertyValueFactory<>("currentHighestBid"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colEndTime.setCellValueFactory(new PropertyValueFactory<>("endTime"));

        // 4. LẮNG NGHE MẠNG
        NetworkClient.getInstance().setOnResponseReceived(response -> {
            // LƯU Ý 1: Dùng Platform.runLater để đổi giao diện
            Platform.runLater(() -> {
                // Sửa thành getPayload() theo chuẩn Protocol của C
                if (response.getStatus() == StatusType.SUCCESS && response.getData() instanceof List) {
                    List<Auction> auctions = (List<Auction>) response.getData();
                    auctionData.setAll(auctions);
                }

                // Nếu có người đặt giá mới, load lại bảng
                if ("UPDATE_AUCTION".equals(response.getMessage())) {
                    NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
                }
            });
        });

        // 5. GỬI YÊU CẦU LẤY DANH SÁCH LÚC VỪA MỞ MÀN HÌNH
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/AddProduct.fxml"));
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