package com.auction.dao.impl;

import com.auction.dao.FileDataManager;
import com.auction.dao.IAuctionDAO;
import com.auction.model.Auction;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AuctionDAOImpl — Đã fix lỗi StackOverflow.
 * Quản lý trực tiếp danh sách dữ liệu trong RAM bằng CopyOnWriteArrayList
 * để đảm bảo an toàn đa luồng (Concurrency Bidding).
 */
public class AuctionDAOImpl implements IAuctionDAO {

    private static final String FILE_PATH = "auctions_data.dat";

    // FIX CRITICAL: Lưu trữ dữ liệu RAM trực tiếp tại DAO.
    // Sử dụng CopyOnWriteArrayList để tránh ConcurrentModificationException khi có nhiều luồng đọc/ghi.
    private List<Auction> auctionsCache = new CopyOnWriteArrayList<>();

    /**
     * Lưu toàn bộ danh sách từ RAM xuống file.
     */
    public boolean saveDataToFile() {
        boolean success = FileDataManager.saveToFile(auctionsCache, FILE_PATH);
        if (success) {
            System.out.println("💾 Đã lưu an toàn " + auctionsCache.size() + " phiên đấu giá xuống file.");
        } else {
            System.err.println("❌ Lỗi: Không thể lưu dữ liệu Auction!");
        }
        return success;
    }

    /**
     * Tải dữ liệu từ file vào RAM khi server khởi động.
     */
    @SuppressWarnings("unchecked")
    public void loadDataFromFile() {
        Object data = FileDataManager.loadFromFile(FILE_PATH);
        if (data instanceof List) {
            List<Auction> loadedAuctions = (List<Auction>) data;
            auctionsCache.clear();
            auctionsCache.addAll(loadedAuctions);
            System.out.println("📦 Đã khôi phục " + loadedAuctions.size() + " phiên đấu giá từ file.");
        } else {
            System.out.println("⚠️ File dữ liệu Auction trống hoặc chưa tồn tại (Server chạy lần đầu).");
        }
    }

    @Override
    public boolean save(Auction auction) {
        // Thêm vào cache trên RAM trước khi lưu xuống file
        auctionsCache.add(auction);
        return saveDataToFile();
    }

    @Override
    public List<Auction> findAll() {
        // FIX CRITICAL: Trả về trực tiếp danh sách RAM nội bộ, cắt đứt đệ quy với AuctionManager.
        return auctionsCache;
    }

    @Override
    public Auction findById(String id) {
        for (Auction a : auctionsCache) {
            if (a.getId() != null && a.getId().equals(id)) {
                return a;
            }
        }
        return null;
    }

    @Override
    public boolean update(Auction auction) {
        // Auction truyền vào vốn đã được tham chiếu và cập nhật trong RAM
        // Nên ở đây chỉ cần flush data xuống file.
        return saveDataToFile();
    }

    @Override
    public boolean delete(String id) {
        boolean removed = auctionsCache.removeIf(a -> a.getId() != null && a.getId().equals(id));
        if (removed) {
            return saveDataToFile();
        }
        return false;
    }
}