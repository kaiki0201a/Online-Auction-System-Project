package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.Seller;
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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class DashboardController {

    @FXML private BorderPane rootPane;
    @FXML private Label lblUsername, lblRole, lblAvatarInitials, lblWelcome, lblBalance, lblSectionTitle;
    @FXML private Button btnCreateAuction;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> comboCategory;
    @FXML private FlowPane auctionGrid; // Lưới chứa các thẻ sản phẩm
    @FXML private Button btnSearch;
    private User currentUser;
    private ObservableList<Auction> auctionData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        currentUser = AppContext.getCurrentUser();
        setupUserInfo();

        comboCategory.setItems(FXCollections.observableArrayList("Tất cả", "Art", "Electronics", "Vehicle"));
        comboCategory.getSelectionModel().selectFirst();
        comboCategory.setOnAction(event -> filterAuctions());

        txtSearch.setOnAction(event -> filterAuctions());

        // Lắng nghe dữ liệu mạng
        NetworkClient.getInstance().setOnResponseReceived(response -> {
            Platform.runLater(() -> {
                if (response.getStatus() == StatusType.SUCCESS && response.getData() instanceof List) {
                    List<Auction> auctions = (List<Auction>) response.getData();
                    auctionData.setAll(auctions);
                    filterAuctions();
                }
                if ("UPDATE_AUCTION".equals(response.getMessage())) {
                    NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
                }
            });
        });

        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
    }

    private void setupUserInfo() {
        lblUsername.setText(currentUser.getUserName());
        lblAvatarInitials.setText(currentUser.getUserName().substring(0, 1).toUpperCase());
        lblWelcome.setText("Chào mừng trở lại, " + currentUser.getUserName() + ".");

        if (currentUser instanceof Bidder) {
            lblRole.setText("Bidder Account");
            lblBalance.setText(String.format("$%,.2f", ((Bidder) currentUser).getBalance()));
            btnCreateAuction.setVisible(false);
            btnCreateAuction.setManaged(false);
            lblSectionTitle.setText("• Đấu Giá Trực Tiếp");
        } else if (currentUser instanceof Seller) {
            lblRole.setText("Elite Seller");
            lblBalance.setText("Kho: Vô hạn");
            btnCreateAuction.setVisible(true);
            btnCreateAuction.setManaged(true);
            lblSectionTitle.setText("• Quản Lý Kho Hàng Của Bạn");
        } else {
            lblRole.setText("Administrator");
            lblBalance.setText("System");
        }
    }

    @FXML
    public void onSearchClick(ActionEvent event) {
        filterAuctions();
    }

    private void filterAuctions() {
        String keyword = txtSearch.getText().toLowerCase().trim();
        String selectedCategory = comboCategory.getValue();

        auctionGrid.getChildren().clear(); // Xóa lưới cũ

        for (Auction auction : auctionData) {
            boolean matchesSearch = auction.getItem().getNameItem().toLowerCase().contains(keyword);
            boolean matchesCategory = true;
            if (!"Tất cả".equals(selectedCategory)) {
                matchesCategory = auction.getItem().getClass().getSimpleName().equals(selectedCategory);
            }

            if (matchesSearch && matchesCategory) {
                // Tạo thẻ VBox cho từng sản phẩm và nhét vào lưới
                auctionGrid.getChildren().add(createAuctionCard(auction));
            }
        }

        if (auctionGrid.getChildren().isEmpty()) {
            Label emptyLbl = new Label("Không tìm thấy phiên đấu giá nào.");
            emptyLbl.setStyle("-fx-text-fill: #A0A0A0; -fx-font-size: 16px;");
            auctionGrid.getChildren().add(emptyLbl);
        }
    }

    // --- CỖ MÁY TẠO THẺ SẢN PHẨM (CARD) ---
    private Node createAuctionCard(Auction auction) {
        VBox card = new VBox();
        card.getStyleClass().add("auction-card");
        card.setPrefWidth(320); // Chiều rộng cố định của thẻ giống Shopee/eBay

        // 1. Vùng chứa ảnh giả lập (Image Placeholder)
        StackPane imageBox = new StackPane();
        imageBox.getStyleClass().add("card-image-placeholder");
        imageBox.setPrefHeight(200);

        Label lblTag = new Label("• " + auction.getStatus().toString());
        lblTag.getStyleClass().add("tag-live");
        StackPane.setAlignment(lblTag, Pos.TOP_LEFT);
        StackPane.setMargin(lblTag, new Insets(15));

        Label lblCategory = new Label(auction.getItem().getClass().getSimpleName());
        lblCategory.setStyle("-fx-text-fill: #555555; -fx-font-weight: bold; -fx-font-size: 24px;");

        imageBox.getChildren().addAll(lblCategory, lblTag);

        // 2. Vùng thông tin (Info Box)
        VBox infoBox = new VBox(10);
        infoBox.setPadding(new Insets(20));

        Label lblTitle = new Label(auction.getItem().getNameItem());
        lblTitle.setStyle("-fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 16px;");
        lblTitle.setWrapText(true);

        HBox priceRow = new HBox();
        priceRow.setAlignment(Pos.CENTER_LEFT);

        VBox priceCol = new VBox(3);
        Label lblPriceTitle = new Label("GIÁ HIỆN TẠI");
        lblPriceTitle.setStyle("-fx-text-fill: #A0A0A0; -fx-font-size: 10px;");
        Label lblPrice = new Label(String.format("$%,.0f", auction.getCurrentHighestBid()));
        lblPrice.setStyle("-fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 18px;");
        priceCol.getChildren().addAll(lblPriceTitle, lblPrice);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnAction = new Button(currentUser instanceof Seller ? "Sửa/Xem" : "ĐẶT GIÁ");
        btnAction.getStyleClass().add("btn-gold");
        btnAction.setOnAction(e -> openAuctionDetail(auction)); // Click nút để mở chi tiết

        priceRow.getChildren().addAll(priceCol, spacer, btnAction);
        infoBox.getChildren().addAll(lblTitle, priceRow);

        card.getChildren().addAll(imageBox, infoBox);

        // Click cả thẻ cũng mở chi tiết
        card.setOnMouseClicked(e -> openAuctionDetail(auction));

        return card;
    }

    private void openAuctionDetail(Auction auction) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/AuctionDetail.fxml"));
            Parent root = loader.load();
            AuctionDetailController detailController = loader.getController();
            detailController.setAuctionData(auction);
            Stage stage = (Stage) rootPane.getScene().getWindow();
            double _w = stage.getWidth();
            double _h = stage.getHeight();
            double _x = stage.getX();
            double _y = stage.getY();
            double _w2 = stage.getWidth();
            double _h2 = stage.getHeight();
            double _x2 = stage.getX();
            double _y2 = stage.getY();
            stage.setScene(new Scene(root));
            stage.setWidth(_w2);
            stage.setHeight(_h2);
            stage.setX(_x2);
            stage.setY(_y2);
            stage.setWidth(_w);
            stage.setHeight(_h);
            stage.setX(_x);
            stage.setY(_y);
        } catch (IOException e) {
            showError("Lỗi", "Không thể mở chi tiết phiên đấu giá.");
        }
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
    public void onProfileClick(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/UserProfile.fxml"));
            Parent root = loader.load();
            UserProfileController profileController = loader.getController();
            if (currentUser instanceof Bidder) {
                profileController.setUserData((Bidder) currentUser);
            }
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            double _w3 = stage.getWidth();
            double _h3 = stage.getHeight();
            double _x3 = stage.getX();
            double _y3 = stage.getY();
            stage.setScene(new Scene(root));
            stage.setWidth(_w3);
            stage.setHeight(_h3);
            stage.setX(_x3);
            stage.setY(_y3);
            stage.setTitle("Trang cá nhân");
        } catch (IOException e) {
            showError("Lỗi", "Không thể mở trang cá nhân.");
        }
    }

    @FXML
    public void onLogoutClick(ActionEvent event) {
        try {
            AppContext.logout();
            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            double _w4 = stage.getWidth();
            double _h4 = stage.getHeight();
            double _x4 = stage.getX();
            double _y4 = stage.getY();
            stage.setScene(new Scene(root));
            stage.setWidth(_w4);
            stage.setHeight(_h4);
            stage.setX(_x4);
            stage.setY(_y4);
        } catch (Exception e) {
            showError("Lỗi", "Lỗi đăng xuất.");
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}