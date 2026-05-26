package com.auction.strategy;

import com.auction.exception.AuctionException;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;

/**
 * ManualBidStrategy — Chiến lược đặt giá thủ công.
 *
 * Bidder trực tiếp nhập số tiền và xác nhận đặt giá.
 * Server nhận request PLACE_BID, tạo BidTransaction và gọi strategy này.
 *
 * Logic:
 *  1. Validate giá (thông qua Auction.processBid — có synchronized)
 *  2. Trừ tiền từ ví Bidder ngay khi đặt thành công
 *  3. Ghi lịch sử giao dịch
 */
public class ManualBidStrategy implements BidStrategy {

    private static final ManualBidStrategy INSTANCE = new ManualBidStrategy();

    private ManualBidStrategy() {}

    /** Lấy instance duy nhất (không cần tạo object mới mỗi request). */
    public static ManualBidStrategy getInstance() {
        return INSTANCE;
    }

    @Override
    public void execute(Auction auction, BidTransaction transaction) throws AuctionException {
        // Delegate toàn bộ validation + concurrency vào processBid() đã synchronized
        auction.processBid(transaction);

        // Trừ tiền từ ví bidder sau khi đặt thành công
        transaction.getBidder().setBalance(
            transaction.getBidder().getBalance() - transaction.getBidAmount()
        );

        System.out.printf(
            "✋ [MANUAL BID] %s đặt $%.2f cho phiên \"%s\"%n",
            transaction.getBidder().getUserName(),
            transaction.getBidAmount(),
            auction.getItem().getNameItem()
        );
    }

    @Override
    public String getStrategyName() {
        return "ManualBidStrategy";
    }
}
