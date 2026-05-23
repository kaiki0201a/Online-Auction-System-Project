package com.auction.dao.impl;

import com.auction.dao.FileDataManager;
import com.auction.dao.IAuctionDAO;
import com.auction.model.Auction;
import com.auction.utils.AuctionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * AuctionDAOImpl — FIX CRITICAL: findAll() và findById() giờ đọc từ RAM (AuctionManager),
 * không đọc lại từ file mỗi lần. File chỉ được đọc khi server khởi động (loadDataFromFile)
 * và được ghi khi server tắt hoặc khi có thay đổi quan trọng.
 *
 * Lý do: Trước đây findAll() gọi FileDataManager.loadFromFile() mỗi lần → dữ liệu mới
 * thêm vào RAM nhưng file chưa flush → findAll() trả về data cũ → inventory trống.
 */
public class AuctionDAOImpl implements IAuctionDAO {

    private static final String FILE_PATH = "auctions_data.dat";

    /**
     * Lưu toàn bộ danh sách từ RAM xuống file.
     * Gọi khi: server tắt, admin approve/reject, bid thành công.
     */
    public boolean saveDataToFile() {
        List<Auction> currentAuctions = AuctionManager.getInstance().getAllAuctions();
        boolean success = FileDataManager.saveToFile(currentAuctions, FILE_PATH);
        if (success) {
            System.out.println("💾 Đã lưu an toàn " + currentAuctions.size() + " phiên đấu giá xuống file.");
        } else {
            System.err.println("❌ Lỗi: Không thể lưu dữ liệu Auction!");
        }
        return success;
    }

    /**
     * Tải dữ liệu từ file vào RAM khi server khởi động.
     * CHỈ gọi 1 lần duy nhất trong ServerApp.main().
     */
    @SuppressWarnings("unchecked")
    public void loadDataFromFile() {
        Object data = FileDataManager.loadFromFile(FILE_PATH);
        if (data instanceof List) {
            List<Auction> loadedAuctions = (List<Auction>) data;
            AuctionManager.getInstance().getAllAuctions().clear();
            AuctionManager.getInstance().getAllAuctions().addAll(loadedAuctions);
            System.out.println("📦 Đã khôi phục " + loadedAuctions.size() + " phiên đấu giá từ file.");
        } else {
            System.out.println("⚠️ File dữ liệu Auction trống hoặc chưa tồn tại (Server chạy lần đầu).");
        }
    }

    /**
     * FIX: Lưu auction mới vào RAM (qua AuctionManager) và flush file.
     * Trước đây: gọi saveDataToFile() ngay lập tức (đúng).
     * Bây giờ: giống vậy, nhưng đảm bảo list RAM đã có item trước khi flush.
     */
    @Override
    public boolean save(Auction auction) {
        // Auction đã được add vào RAM list trước khi gọi save()
        // Chỉ cần flush file
        return saveDataToFile();
    }

    /**
     * FIX CRITICAL: Đọc từ RAM, không đọc từ file.
     * AuctionManager.getAllAuctions() trả về CopyOnWriteArrayList trực tiếp.
     */
    @Override
    public List<Auction> findAll() {
        // Trả về reference đến list RAM — luôn up-to-date
        return AuctionManager.getInstance().getAllAuctions();
    }

    /**
     * FIX: Tìm trong RAM thay vì đọc file.
     */
    @Override
    public Auction findById(String id) {
        for (Auction a : AuctionManager.getInstance().getAllAuctions()) {
            if (a.getId() != null && a.getId().equals(id)) {
                return a;
            }
        }
        return null;
    }

    /**
     * Cập nhật auction (đã cập nhật trực tiếp trên object trong RAM) và flush file.
     */
    @Override
    public boolean update(Auction auction) {
        return saveDataToFile();
    }

    /**
     * Xóa auction khỏi RAM và flush file.
     */
    @Override
    public boolean delete(String id) {
        List<Auction> list = AuctionManager.getInstance().getAllAuctions();
        boolean removed = list.removeIf(a -> a.getId() != null && a.getId().equals(id));
        if (removed) {
            return saveDataToFile();
        }
        return false;
    }
}