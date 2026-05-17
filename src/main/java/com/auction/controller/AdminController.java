package com.auction.controller; // (Hoặc package com.auction.view; tùy theo cách bạn gom file lúc nãy)

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

public class AdminController {

    @FXML private StackPane rootPane;

    // --- Bảng User ---
    @FXML private TableView<String[]> userTable;
    @FXML private TableColumn<String[], String> userNameCol;
    @FXML private TableColumn<String[], String> userStatusCol;
    @FXML private TableColumn<String[], String> userActionCol;

    // --- Bảng Auction ---
    @FXML private TableView<String[]> auctionTable;
    @FXML private TableColumn<String[], String> auctionItemCol;
    @FXML private TableColumn<String[], String> auctionBidCol;
    @FXML private TableColumn<String[], String> auctionActionCol;

    @FXML
    public void initialize() {
        setupUserTable();
        setupAuctionTable();
    }

    private void setupUserTable() {
        userNameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[0]));
        userStatusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[1]));

        userActionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button();
            {
                btn.setOnAction(e -> {
                    String[] rowData = getTableView().getItems().get(getIndex());
                    if ("Active".equals(rowData[1])) {
                        rowData[1] = "Banned";
                        // SỬA: Truyền trực tiếp 'btn' vào hàm Toast
                        com.auction.utils.NotificationUtil.showToast("Đã khóa tài khoản: " + rowData[0], btn, "warning");
                    } else {
                        rowData[1] = "Active";
                        com.auction.utils.NotificationUtil.showToast("Đã mở khóa tài khoản: " + rowData[0], btn, "success");
                    }
                    getTableView().refresh();

                    // TODO: GỌI API SERVER Ở ĐÂY
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableView().getItems().get(getIndex()) == null) {
                    setGraphic(null);
                } else {
                    String[] rowData = getTableView().getItems().get(getIndex());
                    btn.setText("Active".equals(rowData[1]) ? "Ban" : "Unban");

                    // SỬA: Xóa style cũ, gán styleClass từ CSS
                    btn.getStyleClass().removeAll("button", "btn-danger", "btn-success");
                    btn.getStyleClass().add("button"); // Class gốc (bo góc, chữ đậm...)

                    if ("Active".equals(rowData[1])) {
                        btn.getStyleClass().add("btn-danger");
                    } else {
                        btn.getStyleClass().add("btn-success");
                    }
                    setGraphic(btn);
                }
            }
        });
    }

    private void setupAuctionTable() {
        auctionItemCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[0]));
        auctionBidCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[1]));

        auctionActionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Dừng phiên");
            {
                // SỬA: Gán styleClass thay cho CSS cứng
                btn.getStyleClass().addAll("button", "btn-warning");

                btn.setOnAction(e -> {
                    String[] rowData = getTableView().getItems().get(getIndex());
                    // SỬA: Truyền trực tiếp 'btn' vào hàm Toast
                    com.auction.utils.NotificationUtil.showToast("Đã dừng phiên đấu giá: " + rowData[0], btn, "error");
                    getTableView().getItems().remove(rowData);

                    // TODO: GỌI API SERVER Ở ĐÂY
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
    }

    public void setUserData(ObservableList<String[]> users) {
        userTable.setItems(users);
    }

    public void setAuctionData(ObservableList<String[]> auctions) {
        auctionTable.setItems(auctions);
    }
}