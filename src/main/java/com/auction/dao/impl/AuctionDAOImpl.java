package com.auction.dao.impl;

import com.auction.dao.FileDataManager;
import com.auction.dao.IAuctionDAO;
import com.auction.model.Auction;
import com.auction.utils.AuctionManager;

import java.util.List;

public class AuctionDAOImpl implements IAuctionDAO {
    // Tên file lưu trữ dữ liệu (nằm ở thư mục gốc của project)
    private static final String FILE_PATH = "auctions_data.dat";

    /**
     * LƯU DỮ LIỆU: Kéo data từ Manager đang chạy trên RAM và ghi đè xuống file.
     * Hàm này sẽ được gọi tự động khi Server tắt (Shutdown Hook).
     * ĐÃ SỬA: Đổi kiểu trả về thành boolean để khớp với Interface contract
     */
    public boolean saveDataToFile() {
        // Lấy danh sách đang chạy thực tế trên RAM
        List<Auction> currentAuctions = AuctionManager.getInstance().getAllAuctions();

        // Gọi Utility class vừa tạo để lưu
        boolean success = FileDataManager.saveToFile(currentAuctions, FILE_PATH);
        if (success) {
            System.out.println("💾 Đã lưu an toàn " + currentAuctions.size() + " phiên đấu giá xuống file.");
        } else {
            System.err.println("❌ Lỗi: Không thể lưu dữ liệu Auction!");
        }
        return success;
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

    @Override
    public boolean save(Auction auction) {
        return saveDataToFile();
    }

    @Override
    public List<Auction> findAll() {
        Object data = FileDataManager.loadFromFile(FILE_PATH);
        if (data != null && data instanceof List) {
            return (List<Auction>) data;
        }
        return new java.util.ArrayList<>();
    }

    @Override
    public Auction findById(String id) {
        List<Auction> list = findAll();
        for (Auction a : list) {
            if (String.valueOf(a.getId()).equals(id)) {
                return a;
            }
        }
        return null;
    }

    @Override
    public boolean update(Auction auction) {
        return saveDataToFile();
    }

    @Override
    public boolean delete(String id) {
        List<Auction> list = AuctionManager.getInstance().getAllAuctions();
        boolean removed = list.removeIf(a -> String.valueOf(a.getId()).equals(id));
        if (removed) {
            return saveDataToFile();
        }
        return false;
    }
}