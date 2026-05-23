package com.auction.model;

import com.auction.exception.AuctionException;
import com.auction.exception.InvalidAuctionException;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Seller extends User implements Serializable {
    // ID phiên bản để tránh lỗi khi nâng cấp code sau này
    private static final long serialVersionUID = 1L;

    private float rating;
    private double balance; // Số dư của Seller (nhận tiền khi bán thành công)

    // đã sửa level 3: Thêm trường để lưu số lượng rating và tổng điểm
    private int ratingCount;
    private float totalRatingScore;

    private List<Item> inventory;   // Kho hàng lưu trữ các món hàng của người bán quản lý

    // Constructor
    public Seller(String userName, String passWord, String email) {
        super(userName, passWord, email);
        this.rating = 5.0f; // Mặc định ban đầu là 5 sao
        this.ratingCount = 0;
        this.totalRatingScore = 0.0f;
        this.balance = 0.0;
        this.inventory = new ArrayList<>();  // Khởi tạo kho rỗng
    }

    // Các chức năng, nghiệp vụ

    // Tạo auction trực tiếp qua AuctionManager (không cần item trong kho)
    public void createAuction(Item item, LocalDateTime start, LocalDateTime end) throws InvalidAuctionException {
        if (item == null) {
            throw new InvalidAuctionException("Lỗi: Sản phẩm không hợp lệ!");
        }
        System.out.println("Người bán " + this.getUserName() + " đang tạo phiên đấu giá cho sản phẩm: " + item.getNameItem());
        com.auction.utils.AuctionManager.getInstance().createAuction(item, this, start, end);
    }

    public void addItem(Item item) {
        this.inventory.add(item);
        System.out.println("Đã thêm thành công: " + item.getNameItem() + " vào kho hàng.");
    }

    public void removeItem(Item item) {
        if (this.inventory.remove(item)) {
            System.out.println("Đã xoá: " + item.getNameItem() + " khỏi kho hàng");
        } else {
            System.out.println("Không thể xoá vì món hàng không tồn tại trong kho");
        }
    }

    public void updateItem(Item item) {
        System.out.println("Đã cập nhật thông tin cho sản phẩm: " + item.getNameItem());
    }

    // Getter & Setter
    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public List<Item> getInventory() {
        return inventory;
    }
}
