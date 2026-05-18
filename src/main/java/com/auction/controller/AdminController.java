package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.Auction;
import com.auction.model.User;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.protocol.StatusType;
import com.auction.utils.NotificationUtil;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

import java.util.List;

public class AdminController {

    @FXML private StackPane rootPane;

    // --- Bảng User (Đã đổi String[] thành User) ---
    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, String> userNameCol;
    @FXML private TableColumn<User, String> userStatusCol;
    @FXML private TableColumn<User, Void> userActionCol;

    // --- Bảng Auction (Đã đổi String[] thành Auction) ---
    @FXML private TableView<Auction> auctionTable;
    @FXML private TableColumn<Auction, String> auctionItemCol;
    @FXML private TableColumn<Auction, String> auctionBidCol;
    @FXML private TableColumn<Auction, Void> auctionActionCol;

    private ObservableList<User> userData = FXCollections.observableArrayList();
    private ObservableList<Auction> auctionData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupUserTable();
        setupAuctionTable();

        userTable.setItems(userData);
        auctionTable.setItems(auctionData);

        // ĐĂNG KÝ "TAI NGHE" MẠNG
        NetworkClient.getInstance().setOnResponseReceived(response -> {
            Platform.runLater(() -> {
                if (response.getStatus() == StatusType.SUCCESS) {
                    // Xử lý nạp danh sách
                    if ("Danh sách User".equals(response.getMessage())) {
                        userData.setAll((List<User>) response.getData());
                    }
                    else if ("Danh sách".equals(response.getMessage())) { // Chữ "Danh sách" là chuẩn từ ClientHandler của C
                        auctionData.setAll((List<Auction>) response.getData());
                    }
                    // Xử lý hành động thành công (Ban/Unban, Dừng phiên)
                    else if (response.getMessage().contains("Đã cập nhật") || response.getMessage().contains("Đã ép dừng")) {
                        NotificationUtil.showToast(response.getMessage(), rootPane, "success");
                        // Yêu cầu Server gửi lại danh sách mới nhất để update bảng
                        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_USER_LIST, null));
                        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
                    }
                    // Nếu có người khác đặt giá hoặc tạo mới phiên, load lại bảng Auction
                    else if ("UPDATE_AUCTION".equals(response.getMessage())) {
                        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
                    }
                } else {
                    NotificationUtil.showToast(response.getMessage(), rootPane, "error");
                }
            });
        });

        // VỪA MỞ MÀN HÌNH LÀ GỌI SERVER XIN DATA NGAY
        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_USER_LIST, null));
        NetworkClient.getInstance().sendRequest(new Request(ActionType.GET_AUCTION_LIST, null));
    }

    private void setupUserTable() {
        // Cột lấy dữ liệu từ Model
        userNameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUserName()));
        userStatusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isBanned() ? "Bị khóa (Banned)" : "Đang hoạt động"));

        // Cột chèn Nút bấm
        userActionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button();
            {
                btn.setOnAction(e -> {
                    User user = getTableView().getItems().get(getIndex());
                    // GỬI LỆNH LÊN SERVER: Payload là tên của thằng bị Ban
                    NetworkClient.getInstance().sendRequest(new Request(ActionType.BAN_USER, user.getUserName()));
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableView().getItems().get(getIndex()) == null) {
                    setGraphic(null);
                } else {
                    User user = getTableView().getItems().get(getIndex());
                    btn.setText(user.isBanned() ? "Unban" : "Ban");

                    btn.getStyleClass().removeAll("button", "btn-danger", "btn-success");
                    btn.getStyleClass().add("button");
                    btn.getStyleClass().add(user.isBanned() ? "btn-success" : "btn-danger");

                    setGraphic(btn);
                }
            }
        });
    }

    private void setupAuctionTable() {
        // Cột lấy dữ liệu từ Model
        auctionItemCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getItem().getNameItem()));
        auctionBidCol.setCellValueFactory(data -> new SimpleStringProperty("$" + data.getValue().getCurrentHighestBid()));

        // Cột chèn Nút bấm
        auctionActionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Dừng phiên");
            {
                btn.getStyleClass().addAll("button", "btn-warning");
                btn.setOnAction(e -> {
                    Auction auction = getTableView().getItems().get(getIndex());
                    // GỬI LỆNH LÊN SERVER: Payload là ID của phiên đấu giá
                    NetworkClient.getInstance().sendRequest(new Request(ActionType.CANCEL_AUCTION, auction.getAuctionId()));
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableView().getItems().get(getIndex()) == null) {
                    setGraphic(null);
                } else {
                    // Tùy chọn nâng cao: Ẩn nút nếu phiên đã kết thúc
                    Auction auction = getTableView().getItems().get(getIndex());
                    if (auction.getStatus() == com.auction.model.AuctionStatus.FINISHED ||
                            auction.getStatus() == com.auction.model.AuctionStatus.CANCELED) {
                        setGraphic(null);
                    } else {
                        setGraphic(btn);
                    }
                }
            }
        });
    }
}