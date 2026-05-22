package com.auction.model;
import com.auction.exception.AuctionException;
import com.auction.exception.InvalidAuctionException;
import com.auction.utils.AuctionManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Seller extends User{
    // ID phiên bản để tránh lỗi khi nâng cấp code sau này
    private static final long serialVersionUID = 1L;

    private float rating;

    // đã sửa level 3: Thêm trường để lưu số lượng rating và tổng điểm
    private int ratingCount;
    private float totalRatingScore;

    private List<Item> inventory;   // Kho hàng lưu trữ các món hàng của người bán quản lý
    // Constructor
    public Seller(String userName, String passWord, String email) {
        super(userName, passWord, email);
        this.rating = 5.0f; // Mặc định ban đầu là 5 sao
        this.ratingCount = 0;  // đã sửa level 3
        this.totalRatingScore = 0.0f;  // đã sửa level 3
        this.inventory = new ArrayList<>();  // Khời tạo kho rỗng
    }

    // Các chứng năng, nghiệp vụ

    // đã sửa level 1: Thêm kiểm tra item có trong inventory không
    public void createAuction(Item item, LocalDateTime start, LocalDateTime end) throws InvalidAuctionException {
        System.out.println("Người bán " + this.getUserName() + " đang tạo phiên đấu giá cho sản phẩm: " + item.getNameItem());

        // đã sửa level 1: Kiểm tra xem item có trong kho không
        if (!this.inventory.contains(item)) {
            throw new InvalidAuctionException("Lỗi: Sản phẩm '" + item.getNameItem() + "' không tồn tại trong kho của bạn!");
        }

        // Logic tạo đối tượng Auction sẽ được thêm sau khi Auction được làm...
        AuctionManager.getInstance().createAuction(item, this, start, end);
    }

    public void addItem(Item item) {
        this.inventory.add(item);
        System.out.println("Đã thêm thành công: " + item.getNameItem() + " vào kho hàng.");
    }

    public void removeItem(Item item) {
        if (this.inventory.remove(item)){   // Hàm remove trong java vừa xoá vừa trả về boolean
            System.out.println("Đã xoá: " + item.getNameItem() + " khỏi kho hàng");
        }
        else
            System.out.println("Không thể xoá vì món hàng không tồn tại trong kho");
    }

    public void updateItem(Item item) {
        // Sau này sẽ có thêm logic tìm item dựa trên Id, cập nhật các thông số (tên, giá...)
        System.out.println("Đã cập nhật thông tin cho sản phẩm: "+ item.getNameItem());
    }

    // Getter & Setter
    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

    public List<Item> getInventory() {
        return inventory;
    }
}
