package com.auction.utils;

import com.auction.model.BidTransaction;

/**
 * AuctionObserver — Observer interface cho phép các đối tượng nhận cập nhật từ Auction.
 * Biđder implements interface này để nhận thông báo khi có bid mới.
 */
public interface AuctionObserver {
    /** Nhận thông báo dạng văn bản (ví dụ: anti-sniping, đóng phiên). */
    void update(String message);
    /** Nhận sự kiện khi có bid mới được xử lý thành công. */
    void onNewBidPlaced(BidTransaction transaction);
}
