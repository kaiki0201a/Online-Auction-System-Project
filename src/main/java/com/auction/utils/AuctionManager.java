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
     * Hàm nội bộ bổ trợ: Quyết định nguồn dữ liệu xử lý.
     * - Nếu đang chạy Unit Test (đã gọi setAuctionDAO): Trả về đối tượng Mock giả lập.
     * - Nếu chạy thật (App/Server bình thường): Tự động lấy nguồn thực tế từ ServerApp.getAuctionDAO().
     */
    private IAuctionDAO getDAO() {
        if (this.auctionDAO != null) {
            return this.auctionDAO;
        }
        return ServerApp.getAuctionDAO();
    }

    // Tạo phiên đấu giá mới và lưu xuống DB/File
    public Auction createAuction(Item item, Seller seller, LocalDateTime start, LocalDateTime end) {
        Auction newAuction = new Auction(item, seller, start, end);
        getDAO().save(newAuction);
        return newAuction;
    }

    // Lấy danh sách toàn bộ phiên đấu giá
    public List<Auction> getAllAuctions() {
        return getDAO().findAll();
    }

    // Tìm phiên đấu giá theo ID
    public Auction getAuctionById(String auctionId) {
        return getDAO().findById(auctionId);
    }

    // Cập nhật thông tin phiên đấu giá (VD: khi có lượt bid mới)
    public void updateAuction(Auction auction) {
        getDAO().update(auction);
    }
}