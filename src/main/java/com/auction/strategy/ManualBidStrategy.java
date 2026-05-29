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

        // BUG-06 FIX: Dùng holdFunds() thay vì setBalance(balance - amount) trực tiếp.
        // holdFunds(amount) → balance -= amount; heldBalance += amount
        // → getTotalBalance() luôn đúng, heldBalance phản ánh tiền đang giữ cho phiên này.
        transaction.getBidder().holdFunds(transaction.getBidAmount());

        System.out.printf(
            "✋ [MANUAL BID] %s đặt $%.2f cho phiên \"%s\" (khả dụng còn: $%.2f)%n",
            transaction.getBidder().getUserName(),
            transaction.getBidAmount(),
            auction.getItem().getNameItem(),
            transaction.getBidder().getBalance()
        );
    }

    @Override
    public String getStrategyName() {
        return "ManualBidStrategy";
    }
}
