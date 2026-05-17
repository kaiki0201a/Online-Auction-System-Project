package com.auction.utils;

import com.auction.model.Auction;
import com.auction.model.Item;
import com.auction.model.Seller;
import com.auction.server.ServerApp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lớp Quản lý Phiên đấu giá (Tầng Service/Manager).
 * Xử lý logic trung gian giữa Controller và DAO.
 */
public class AuctionManager {

    private static AuctionManager instance;

    private AuctionManager() {
    }

    // Khởi tạo Singleton an toàn với đa luồng (Double-Checked Locking)
    public static AuctionManager getInstance() {
        if (instance == null) {
            synchronized (AuctionManager.class) {
                if (instance == null) {
                    instance = new AuctionManager();
                }
            }
        }
        return instance;
    }

    // Tạo phiên đấu giá mới và lưu xuống DB
    public Auction createAuction(Item item, Seller seller, LocalDateTime start, LocalDateTime end) {
        Auction newAuction = new Auction(item, seller, start, end);
        ServerApp.getAuctionDAO().save(newAuction);
        return newAuction;
    }

    // Lấy danh sách toàn bộ phiên đấu giá
    public List<Auction> getAllAuctions() {
        return ServerApp.getAuctionDAO().findAll();
    }

    // Tìm phiên đấu giá theo ID
    public Auction getAuctionById(String auctionId) {
        return ServerApp.getAuctionDAO().findById(auctionId);
    }

    // Cập nhật thông tin phiên đấu giá (VD: khi có lượt bid mới)
    public void updateAuction(Auction auction) {
        ServerApp.getAuctionDAO().update(auction);
    }
}