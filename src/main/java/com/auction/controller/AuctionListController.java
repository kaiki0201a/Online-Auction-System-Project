package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bidder;
import com.auction.model.User;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.StatusType;
import com.auction.utils.AppContext;
import com.auction.utils.CurrencyFormatter;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
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
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

public class AuctionListController {
    @FXML private BorderPane rootPane;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> comboCategory;
    @FXML private ComboBox<String> comboStatus;
    @FXML private FlowPane auctionGrid;
    @FXML private Label lblCount;

    private List<Auction> allAuctions;
    private User currentUser;

    @FXML public void initialize() {
        currentUser = AppContext.getCurrentUser();
        comboCategory.setItems(FXCollections.observableArrayList("Tất cả", "Art", "Electronics", "Vehicle"));
        comboCategory.setValue("Tất cả");
        comboStatus.setItems(FXCollections.observableArrayList("Tất cả", "Đang diễn ra", "Đã kết thúc"));
        comboStatus.setValue("Tất cả");

        comboCategory.setOnAction(e -> applyFilter());
        comboStatus.setOnAction(e -> applyFilter());
        if (txtSearch != null) txtSearch.textProperty().addListener((o, ov, nv) -> applyFilter());

        NetworkClient.getInstance().setOnResponseReceived(response -> {
            Platform.runLater(() -> {
                if (response.getStatus() == StatusType.SUCCESS && response.getData() instanceof List) {
                    List<?> data = (List<?>) response.getData();
                    if (!data.isEmpty() && data.get(0) instanceof Auction) {
                        allAuctions = (List<Auction>) data;
                        applyFilter();
                    }
                }
                // Phản ứng các loại broadcast
                String msg = response.getMessage();
                if ("UPDATE_AUCTION".equals(msg) || "AUCTION_APPROVED".equals(msg)
                        || "AUCTION_REJECTED".equals(msg) || "AUCTION_CREATED".equals(msg)) {
                    if (!(response.getData() instanceof List)) {
                        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
                    }
                }
            });
        });
        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
    }

    private void applyFilter() {
        if (allAuctions == null) return;
        String kw = txtSearch != null ? txtSearch.getText().toLowerCase() : "";
        String cat = comboCategory.getValue();
        String status = comboStatus.getValue();

        List<Auction> filtered = allAuctions.stream()
            // Không hiển thị sản phẩm chưa được Admin duyệt
            .filter(a -> a.getStatus() != AuctionStatus.PENDING_APPROVAL)
            .filter(a -> a.getItem().getNameItem().toLowerCase().contains(kw))
            .filter(a -> "Tất cả".equals(cat) || a.getItem().getClass().getSimpleName().equals(cat))
            .filter(a -> {
                if ("Đang diễn ra".equals(status)) return a.getStatus() == AuctionStatus.RUNNING || a.getStatus() == AuctionStatus.OPEN;
                if ("Đã kết thúc".equals(status)) return a.getStatus() == AuctionStatus.FINISHED || a.getStatus() == AuctionStatus.PAID;
                return true;
            }).collect(Collectors.toList());

        if (lblCount != null) lblCount.setText("(" + filtered.size() + " phiên)");
        renderGrid(filtered);
    }

    private void renderGrid(List<Auction> list) {
        auctionGrid.getChildren().clear();
        if (list.isEmpty()) {
            Label empty = new Label("Không có phiên đấu giá nào.");
            empty.setStyle("-fx-text-fill: #555; -fx-font-size: 16px;");
            auctionGrid.getChildren().add(empty);
            return;
        }
        for (Auction a : list) auctionGrid.getChildren().add(createCard(a));
    }

    private VBox createCard(Auction auction) {
        VBox card = new VBox();
        card.getStyleClass().add("auction-card");
        card.setPrefWidth(280);

        StackPane imgBox = new StackPane();
        imgBox.getStyleClass().add("card-image-placeholder");
        imgBox.setPrefHeight(170);

        // Ảnh sản phẩm nếu có
        if (auction.getItem().getImagePath() != null && !auction.getItem().getImagePath().isEmpty()) {
            try {
                javafx.scene.image.Image img = new javafx.scene.image.Image(
                    "file:" + auction.getItem().getImagePath(), 280, 170, true, true);
                javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
                iv.setFitWidth(280); iv.setFitHeight(170); iv.setPreserveRatio(false);
                imgBox.getChildren().add(iv);
            } catch (Exception ignored) {}
        } else {
            Label lbl = new Label(auction.getItem().getClass().getSimpleName());
            lbl.setStyle("-fx-text-fill: #333; -fx-font-size: 20px; -fx-font-weight: bold;");
            imgBox.getChildren().add(lbl);
        }

        Label badge = new Label(getStatusLabel(auction));
        badge.setStyle(getStatusStyle(auction));
        StackPane.setAlignment(badge, Pos.TOP_LEFT);
        StackPane.setMargin(badge, new Insets(10));
        imgBox.getChildren().add(badge);

        // Timer
        Label timer = new Label();
        updateTimer(timer, auction);
        timer.setStyle("-fx-background-color: rgba(0,0,0,0.65); -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 3 7; -fx-background-radius: 4;");
        StackPane.setAlignment(timer, Pos.TOP_RIGHT);
        StackPane.setMargin(timer, new Insets(10));
        imgBox.getChildren().add(timer);

        VBox info = new VBox(10);
        info.setPadding(new Insets(14));

        Label lblCat = new Label(auction.getItem().getClass().getSimpleName().toUpperCase() + " • LÔ " + auction.getAuctionId().substring(0, 6).toUpperCase());
        lblCat.setStyle("-fx-text-fill: #666; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label lblTitle = new Label(auction.getItem().getNameItem());
        lblTitle.setStyle("-fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 14px;");
        lblTitle.setWrapText(true);

        Label lblSeller = new Label("Người bán: " + auction.getSeller().getUserName());
        lblSeller.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        HBox botRow = new HBox();
        botRow.setAlignment(Pos.CENTER_LEFT);
        VBox priceCol = new VBox(2);
        Label lp = new Label("GIÁ HIỆN TẠI");
        lp.setStyle("-fx-text-fill: #666; -fx-font-size: 9px;");
        Label lv = new Label(CurrencyFormatter.format(auction.getCurrentHighestBid()));
        lv.setStyle("-fx-text-fill: #F5C518; -fx-font-weight: bold; -fx-font-size: 15px;");
        priceCol.getChildren().addAll(lp, lv);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button btn = new Button();
        boolean isSeller = currentUser instanceof com.auction.model.Seller;
        boolean isOwn = isSeller && auction.getSeller().getUserName().equals(currentUser.getUserName());

        if (isOwn) {
            btn.setText("Của tôi");
            btn.setDisable(true);
            btn.setStyle("-fx-background-color: #2A2A2A; -fx-text-fill: #666; -fx-background-radius: 4; -fx-padding: 6 12;");
        } else {
            btn.setText("Xem →");
            btn.getStyleClass().add("btn-outline");
            btn.setOnAction(e -> openDetail(auction));
        }
        botRow.getChildren().addAll(priceCol, sp, btn);
        info.getChildren().addAll(lblCat, lblTitle, lblSeller, botRow);
        card.getChildren().addAll(imgBox, info);

        card.setOnMouseEntered(e -> { ScaleTransition st = new ScaleTransition(Duration.millis(180), card); st.setToX(1.02); st.setToY(1.02); st.play(); });
        card.setOnMouseExited(e -> { ScaleTransition st = new ScaleTransition(Duration.millis(180), card); st.setToX(1.0); st.setToY(1.0); st.play(); });
        if (!isOwn) card.setOnMouseClicked(e -> openDetail(auction));
        return card;
    }

    private void updateTimer(Label lbl, Auction a) {
        long secs = ChronoUnit.SECONDS.between(LocalDateTime.now(), a.getEndTime());
        if (secs <= 0) lbl.setText("KẾT THÚC");
        else { long h = secs/3600, m=(secs%3600)/60, s=secs%60; lbl.setText(String.format("%02d:%02d:%02d",h,m,s)); }
    }

    private String getStatusLabel(Auction a) {
        return switch (a.getStatus()) {
            case RUNNING, OPEN       -> "• ĐANG DIỄN RA";
            case FINISHED, PAID      -> "• ĐÃ KẾT THÚC";
            case CANCELED            -> "• ĐÃ HỦY";
            case PENDING_APPROVAL    -> "• CHờ DUYỆT";
        };
    }
    private String getStatusStyle(Auction a) {
        String base = "-fx-padding: 3 8; -fx-background-radius: 4; -fx-font-size: 10px; -fx-font-weight: bold;";
        return switch (a.getStatus()) {
            case RUNNING, OPEN -> base + "-fx-background-color: rgba(245,197,24,0.2); -fx-text-fill: #F5C518;";
            case FINISHED, PAID -> base + "-fx-background-color: rgba(39,174,96,0.2); -fx-text-fill: #27ae60;";
            default -> base + "-fx-background-color: rgba(231,76,60,0.2); -fx-text-fill: #e74c3c;";
        };
    }

    private void openDetail(Auction auction) {
        try {
            NetworkClient.getInstance().removeOnResponseReceived();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/view/AuctionDetail.fxml"));
            Parent root = loader.load();
            AuctionDetailController ctrl = loader.getController();
            ctrl.setAuctionData(auction);
            Stage stage = (Stage) rootPane.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML public void onBackClick(ActionEvent event) {
        NetworkClient.getInstance().removeOnResponseReceived();
        try {
            User u = AppContext.getCurrentUser();
            String path = (u instanceof com.auction.model.Seller) ? "/com/auction/view/SellerDashboard.fxml" : "/com/auction/view/BidderDashboard.fxml";
            Parent root = FXMLLoader.load(getClass().getResource(path));
            Stage stage = (Stage) ((Node)event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 800));
        } catch (IOException e) { e.printStackTrace(); }
    }
}
