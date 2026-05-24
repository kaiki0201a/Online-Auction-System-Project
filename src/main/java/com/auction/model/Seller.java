package com.auction.model;

import com.auction.exception.AuctionException;
import com.auction.exception.InvalidAuctionException;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Seller extends User implements Serializable {
    // ID phiên bản để tránh lỗi khi nâng cấp code sau này
    private static final long serialVersionUID = 1L;

    private float rating;
    private double balance;
    private int ratingCount;
    private float totalRatingScore;
    private List<Item> inventory;
    private List<WalletTransaction> walletHistory; // Lịch sử nạp/rút tiền

    public Seller(String userName, String passWord, String email) {
        super(userName, passWord, email);
        this.rating = 5.0f;
        this.ratingCount = 0;
        this.totalRatingScore = 0.0f;
        this.balance = 0.0;
        this.inventory   = new ArrayList<>();
        this.walletHistory = new CopyOnWriteArrayList<>();
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

    public List<WalletTransaction> getWalletHistory() {
        if (walletHistory == null) walletHistory = new CopyOnWriteArrayList<>();
        return walletHistory;
    }
    public void addWalletTransaction(WalletTransaction wt) {
        if (walletHistory == null) walletHistory = new CopyOnWriteArrayList<>();
        walletHistory.add(0, wt);
    }
}
