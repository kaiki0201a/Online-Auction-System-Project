package com.auction.model;
import java.util.ArrayList;
import java.util.List;

public class Seller extends User{
    private float rating;
    private List<Item> inventory;   // Kho hàng lưu trữ các món hàng của người bán quản lý
    // Constructor
    public Seller(String userName, String passWord, String email) {
        super(userName, passWord, email);
        this.rating = 5.0f; // Mặc định ban đầu là 5 sao
        this.inventory = new ArrayList<>();  // Khời tạo kho rỗng
    }

    // Các chứng năng, nghiệp vụ

    public void createAuction(Item item) {
        System.out.println("Người bán " + this.userName + " đang tạo phiên đấu giá cho sản phẩm: " + item.getNameItem());
        // Logic tạo đối tượng Auction sẽ được thêm sau khi Auction được làm...
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
