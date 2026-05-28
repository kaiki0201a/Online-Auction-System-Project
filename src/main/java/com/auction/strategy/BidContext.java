package com.auction.strategy;

import com.auction.exception.AuctionException;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;

/**
 * BidContext — Lớp ngữ cảnh trong Strategy Pattern.
 *
 * Thay vì ClientHandler gọi trực tiếp auction.processBid(), nó sẽ:
 *  1. Tạo BidContext với strategy phù hợp (Manual hoặc Auto)
 *  2. Gọi BidContext.executeBid() — context tự delegate sang strategy đúng
 *
 * Điều này cho phép ClientHandler không biết strategy nào đang chạy,
 * chỉ biết "có một chiến lược đặt giá" (Open/Closed Principle).
 *
 * Ví dụ sử dụng:
 * <pre>
 *   // Đặt giá thủ công
 *   BidContext ctx = new BidContext(ManualBidStrategy.getInstance());
 *   ctx.executeBid(auction, transaction);
 *
 *   // Đặt giá tự động (server trigger)
 *   BidContext ctx = new BidContext(AutoBidStrategy.getInstance());
 *   ctx.executeBid(auction, transaction);
 * </pre>
 */
public class BidContext {

    private BidStrategy strategy;

    public BidContext(BidStrategy strategy) {
        this.strategy = strategy;
    }

    /** Thay đổi strategy tại runtime nếu cần. */
    public void setStrategy(BidStrategy strategy) {
        this.strategy = strategy;
    }

    public BidStrategy getStrategy() {
        return strategy;
    }

    /**
     * Thực thi đặt giá theo strategy hiện tại.
     *
     * @param auction     Phiên đấu giá mục tiêu
     * @param transaction Giao dịch đặt giá
     * @throws AuctionException Nếu đặt giá không hợp lệ
     */
    public void executeBid(Auction auction, BidTransaction transaction) throws AuctionException {
        System.out.printf(
            "📋 [BID CONTEXT] Thực thi chiến lược: %s%n",
            strategy.getStrategyName()
        );
        strategy.execute(auction, transaction);
    }
}
