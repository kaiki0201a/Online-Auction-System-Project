package com.auction.utils;

import com.auction.model.BidTransaction;
// Hợp đồng "Lắng nghe cập nhật"
public interface AuctionObserver {
    // Hàm này sẽ bị ép phải chạy mỗi khi phiên đấu giá có biến động
    void update(String message);
    void onNewBidPlaced(BidTransaction transaction);
}
