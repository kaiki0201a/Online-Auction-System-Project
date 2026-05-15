package com.auction.view;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class AdminView extends BorderPane {
    
    public AdminView() {
        setPadding(new Insets(20));

        TabPane tabPane = new TabPane();
        
        // User Management Tab
        Tab userTab = new Tab("Quản lý người dùng");
        userTab.setClosable(false);
        userTab.setContent(createUserManagementView());

        // Auction Management Tab
        Tab auctionTab = new Tab("Phiên đấu giá đang diễn ra");
        auctionTab.setClosable(false);
        auctionTab.setContent(createAuctionManagementView());

        tabPane.getTabs().addAll(userTab, auctionTab);
        
        setCenter(tabPane);
        
        Label title = new Label("Admin Dashboard");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        BorderPane.setMargin(title, new Insets(0, 0, 20, 0));
        setTop(title);
    }

    private VBox createUserManagementView() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        
        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<String[], String> nameCol = new TableColumn<>("Tên người dùng");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[0]));
        
        TableColumn<String[], String> statusCol = new TableColumn<>("Trạng thái");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[1]));
        
        TableColumn<String[], String> actionCol = new TableColumn<>("Hành động");
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button();
            {
                btn.setOnAction(e -> {
                    String[] rowData = getTableView().getItems().get(getIndex());
                    if ("Active".equals(rowData[1])) {
                        rowData[1] = "Banned";
                        btn.setText("Unban");
                        com.auction.utils.NotificationUtil.showToast("Đã khóa tài khoản: " + rowData[0], (StackPane) AdminView.this.getScene().getRoot(), "warning");
                    } else {
                        rowData[1] = "Active";
                        btn.setText("Ban");
                        com.auction.utils.NotificationUtil.showToast("Đã mở khóa tài khoản: " + rowData[0], (StackPane) AdminView.this.getScene().getRoot(), "success");
                    }
                    getTableView().refresh();
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    String[] rowData = getTableView().getItems().get(getIndex());
                    btn.setText("Active".equals(rowData[1]) ? "Ban" : "Unban");
                    if ("Active".equals(rowData[1])) {
                        btn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-cursor: hand;");
                    } else {
                        btn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-cursor: hand;");
                    }
                    setGraphic(btn);
                }
            }
        });

        table.getColumns().addAll(nameCol, statusCol, actionCol);
        
        ObservableList<String[]> data = FXCollections.observableArrayList(
            new String[]{"Nguyen Van A", "Active"},
            new String[]{"Tran Thi B", "Banned"},
            new String[]{"Le Van C", "Active"}
        );
        table.setItems(data);
        
        vbox.getChildren().add(table);
        return vbox;
    }

    private VBox createAuctionManagementView() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        
        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<String[], String> itemCol = new TableColumn<>("Sản phẩm");
        itemCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[0]));
        
        TableColumn<String[], String> currentBidCol = new TableColumn<>("Giá hiện tại");
        currentBidCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()[1]));

        TableColumn<String[], String> actionCol = new TableColumn<>("Can thiệp");
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Dừng phiên");
            {
                btn.setStyle("-fx-background-color: #ff9800; -fx-text-fill: white; -fx-cursor: hand;");
                btn.setOnAction(e -> {
                    String[] rowData = getTableView().getItems().get(getIndex());
                    com.auction.utils.NotificationUtil.showToast("Đã dừng phiên đấu giá: " + rowData[0], (StackPane) AdminView.this.getScene().getRoot(), "error");
                    getTableView().getItems().remove(rowData);
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(btn);
                }
            }
        });

        table.getColumns().addAll(itemCol, currentBidCol, actionCol);
        
        ObservableList<String[]> data = FXCollections.observableArrayList(
            new String[]{"Bức tranh Mona Lisa", "$5000"},
            new String[]{"iPhone 15 Pro Max", "$1200"},
            new String[]{"Xe đạp điện Vinfast", "$800"}
        );
        table.setItems(data);
        
        vbox.getChildren().add(table);
        return vbox;
    }
}
