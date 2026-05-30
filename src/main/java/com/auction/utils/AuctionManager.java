package com.auction.utils;

import com.auction.dao.IAuctionDAO;
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

    // Thuộc tính để lưu trữ đối tượng Mock DAO khi chạy Unit Test (Áp dụng DIP)
    private IAuctionDAO auctionDAO;

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

    /**
     * Tiêm (Inject) Interface DAO vào Manager.
     * Hàm này được gọi trong @BeforeEach của AuctionManagerTest để truyền Mock DAO vào.
     */
    public void setAuctionDAO(IAuctionDAO auctionDAO) {
        this.auctionDAO = auctionDAO;
    }

    /**
     * Trả về DAO được dùng hiện tại:
     * - Nếu đã gọi setAuctionDAO() (ví dụ: trong Unit Test): dùng DAO đó.
     * - Trong App thật: tự động lấy từ ServerApp (thực thể sống duy nhất).
     */
    private IAuctionDAO getDAO() {
        if (this.auctionDAO != null) {
            return this.auctionDAO;
        }
        return ServerApp.getAuctionDAO();
    }

    // ─── CRUD OPERATIONS ────────────────────────────────────────────────────────────

    /**
     * Tạo phiên đấu giá mới và lưu xuống DAO.
     *
     * @return Auction đã được tạo và gán ID
     */
    public Auction createAuction(Item item, Seller seller, LocalDateTime start, LocalDateTime end) {
        Auction newAuction = new Auction(item, seller, start, end);
        getDAO().save(newAuction);
        return newAuction;
    }

    /**
     * Trả về toàn bộ danh sách phiên đấu giá (trực tiếp từ cache RAM).
     *
     * @return Live list — lưu ý thay đổi sẽ ảnh hưởng trực tiếp.
     */
    public List<Auction> getAllAuctions() {
        return getDAO().findAll();
    }

    /**
     * Tìm phiên đấu giá theo ID.
     *
     * @return Auction nếu tìm thấy, null nếu không tồn tại
     */
    public Auction getAuctionById(String auctionId) {
        return getDAO().findById(auctionId);
    }

    /**
     * Cập nhật thông tin phiên đấu giá (flush xuống file).
     * Trong trường hợp thường được gọi sau mỗi bid đặt giá thành công.
     */
    public void updateAuction(Auction auction) {
        getDAO().update(auction);
    }
}