package com.auction.utils;

import com.auction.model.Auction;
import com.auction.model.Item;
import com.auction.model.Seller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Singleton
public class AuctionManager {
    // 1. Biến static lưu trữ ĐÚNG MỘT instance duy nhất
    private static AuctionManager instance;

    // Danh sách trung tâm chứa tất cả các phiên đấu giá
    private List<Auction> activeAuctions;

    // 2. Constructor PRIVATE: Ngăn chặn dùng lệnh 'new AuctionManager()' ở nơi khác
    private AuctionManager() {
        this.activeAuctions = new ArrayList<>();
    }

    // 3. Hàm cấp quyền truy cập: Nơi duy nhất trả về instance
    // (Thêm 'synchronized' để an toàn khi đa luồng ở Tuần 7)
    // Double check
    public static AuctionManager getInstance() {
    // Kiểm tra lần 1: Nếu có rồi thì trả về luôn, KHÔNG cần xếp hàng (không bị block)
        if (instance == null) {
            // Chỉ khóa class lại khi instance thực sự chưa được tạo
            synchronized (AuctionManager.class) {
                // Kiểm tra lần 2: Đề phòng trường hợp có 2 luồng cùng lọt qua bài kiểm tra lần 1
                if (instance == null) {
                    instance = new AuctionManager();
                }
            }
        }
        return instance;
    }

    // --- CÁC HÀM NGHIỆP VỤ (BUSINESS LOGIC) ---

    // Seller gọi hàm này để tạo phiên đấu giá
    public Auction createAuction(Item item, Seller seller, LocalDateTime start, LocalDateTime end) {
        Auction newAuction = new Auction(item, seller, start, end);
        this.activeAuctions.add(newAuction);
        System.out.println(">>> [Hệ Thống] Đã tạo phiên đấu giá thành công cho sản phẩm: " + item.getNameItem());
        return newAuction;
    }

    // Lấy toàn bộ danh sách phiên đấu giá (để hiển thị lên GUI)
    public List<Auction> getAllAuctions() {
        return this.activeAuctions;
    }

    // Tìm một phiên đấu giá dựa vào ID
    public Auction getAuctionById(String auctionId) {
        for (Auction auction : this.activeAuctions) {
            if (auction.getId().equals(auctionId)) {
                return auction;
            }
        }
        return null;
    }
}
