package com.auction.dao.impl;

import com.auction.dao.FileDataManager;
import com.auction.model.Auction;
import com.auction.utils.AuctionManager;

import java.util.List;

public class AuctionDAOImpl {
    // Tên file lưu trữ dữ liệu (nằm ở thư mục gốc của project)
    private static final String FILE_PATH = "auctions_data.dat";

    /**
     * LƯU DỮ LIỆU: Kéo data từ Manager đang chạy trên RAM và ghi đè xuống file.
     * Hàm này sẽ được gọi tự động khi Server tắt (Shutdown Hook).
     */
    public void saveDataToFile() {
        // Lấy danh sách đang chạy thực tế trên RAM
        List<Auction> currentAuctions = AuctionManager.getInstance().getAllAuctions();

        // Gọi Utility class vừa tạo để lưu
        boolean success = FileDataManager.saveToFile(currentAuctions, FILE_PATH);
        if (success) {
            System.out.println("💾 Đã lưu an toàn " + currentAuctions.size() + " phiên đấu giá xuống file.");
        } else {
            System.err.println("❌ Lỗi: Không thể lưu dữ liệu Auction!");
        }
    }

    /**
     * TẢI DỮ LIỆU: Đọc từ file và nạp vào Manager.
     * Hàm này CHỈ gọi 1 lần duy nhất ở hàm main() khi vừa bật Server.
     */
    @SuppressWarnings("unchecked")
    public void loadDataFromFile() {
        // Gọi Utility class để đọc file
        Object data = FileDataManager.loadFromFile(FILE_PATH);

        // Kiểm tra xem dữ liệu đọc lên có hợp lệ không (có phải là List không)
        if (data != null && data instanceof List) {
            List<Auction> loadedAuctions = (List<Auction>) data;

            // Dọn sạch danh sách rác hiện tại và nạp dữ liệu xịn từ file vào
            AuctionManager.getInstance().getAllAuctions().clear();
            AuctionManager.getInstance().getAllAuctions().addAll(loadedAuctions);

            System.out.println("📦 Đã khôi phục " + loadedAuctions.size() + " phiên đấu giá từ file.");
        } else {
            System.out.println("⚠️ File dữ liệu Auction trống hoặc chưa tồn tại (Server chạy lần đầu).");
        }
    }

    /**
     * Hàm phụ trợ: Được gọi bên ClientHandler khi có 1 phiên đấu giá mới được tạo (CREATE_AUCTION).
     * Thực chất là ta sẽ lưu lại toàn bộ trạng thái danh sách mới nhất xuống file cho chắc cốp.
     */
    public void save(Auction auction) {
        saveDataToFile();
    }
    public List<Auction> findAll() {
        Object data = FileDataManager.loadFromFile(FILE_PATH);
        if (data != null && data instanceof List) {
            return (List<Auction>) data;
        }
        return new java.util.ArrayList<>();
    }

    public Auction findById(String id) {
        List<Auction> list = findAll();
        for (Auction a : list) {
            if (String.valueOf(a.getId()).equals(id)) {
                return a;
            }
        }
        return null;
    }

    public void update(Auction auction) {
        // Khi có thay đổi (update), chỉ cần ghi đè danh sách mới nhất xuống ổ cứng
        saveDataToFile();
    }
}