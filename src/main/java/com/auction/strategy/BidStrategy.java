package com.auction.strategy;

import com.auction.exception.AuctionException;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;

/**
 * Strategy Pattern — Giao diện xử lý lượt đặt giá.
 *
 * Cho phép hoán đổi thuật toán đặt giá linh hoạt mà không thay đổi
 * code phía ClientHandler (Open/Closed Principle).
 *
 * Các Implementation:
 *  - ManualBidStrategy  : Bidder tự tay nhập số tiền và đặt giá.
 *  - AutoBidStrategy    : Server tự động đặt giá thay Bidder theo rule đã đăng ký.
 */
public interface BidStrategy {

    /**
     * Thực thi lượt đặt giá theo chiến lược cụ thể.
     *
     * @param auction     Phiên đấu giá mục tiêu
     * @param transaction Thông tin giao dịch đặt giá (bidder, amount, timestamp)
     * @throws AuctionException Khi giá không hợp lệ, phiên đã đóng, hoặc số dư không đủ
     */
    void execute(Auction auction, BidTransaction transaction) throws AuctionException;

    /**
     * Mô tả ngắn gọn chiến lược đang dùng — phục vụ logging và debugging.
     */
    String getStrategyName();
}
