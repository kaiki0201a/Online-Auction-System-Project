package com.auction.strategy;

import com.auction.exception.AuctionException;
import com.auction.exception.AuctionClosedException;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.AutoBidRule;
import com.auction.model.BidTransaction;
import com.auction.model.Bidder;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoBidStrategy — Chiến lược đặt giá tự động (robot).
 *
 * Server kích hoạt strategy này thay cho Bidder khi:
 *  - Có bid mới vào phiên mà Bidder đang cài AutoBid
 *  - Bidder chưa dẫn đầu AND giá mục tiêu vẫn <= maxBid
 *
 * Logic cốt lõi:
 *  1. Lấy danh sách AutoBidRule đang active của phiên
 *  2. Sắp xếp: maxBid cao nhất được ưu tiên; ngang nhau → đăng ký sớm hơn thắng
 *  3. Đặt giá tuần tự theo priority cho đến khi không còn rule nào hợp lệ
 *  4. Deactivate rule khi chạm maxBid
 *
 * NOTE: Strategy này được gọi bên trong Auction.processBid() (đã synchronized),
 *       nên KHÔNG cần thêm synchronized ở đây để tránh deadlock.
 */
public class AutoBidStrategy implements BidStrategy {

    private static final AutoBidStrategy INSTANCE = new AutoBidStrategy();

    private AutoBidStrategy() {}

    public static AutoBidStrategy getInstance() {
        return INSTANCE;
    }

    @Override
    public void execute(Auction auction, BidTransaction transaction) throws AuctionException {
        if (auction.getStatus() != AuctionStatus.RUNNING
                && auction.getStatus() != AuctionStatus.OPEN
                && auction.getStatus() != AuctionStatus.APPROVED) {
            throw new AuctionClosedException("AutoBid: Phiên đấu giá không ở trạng thái hoạt động.");
        }

        List<AutoBidRule> activeRules = getSortedActiveRules(auction);
        if (activeRules.isEmpty()) return;

        boolean hasAction;
        do {
            hasAction = false;

            for (AutoBidRule rule : activeRules) {
                if (!rule.isActive()) continue;

                Bidder ruleBidder = rule.getBidder();

                // Bỏ qua nếu bidder này đang dẫn đầu
                if (auction.getHighestBidder() != null
                        && ruleBidder.getId().equals(auction.getHighestBidder().getId())) {
                    continue;
                }

                double targetPrice = auction.getCurrentHighestBid() + rule.getIncrement();

                if (targetPrice <= rule.getMaxBid()) {
                    try {
                        BidTransaction autoTx = new BidTransaction(auction, ruleBidder, targetPrice);
                        // Gọi processBid trực tiếp (đã có synchronized trong Auction)
                        auction.processBid(autoTx);

                        System.out.printf(
                            "🤖 [AUTO BID] %s đặt $%.2f (max $%.2f) cho \"%s\"%n",
                            ruleBidder.getUserName(),
                            targetPrice,
                            rule.getMaxBid(),
                            auction.getItem().getNameItem()
                        );

                        hasAction = true;
                        break; // Bắt đầu lại vòng lặp sau khi có bid mới

                    } catch (AuctionException e) {
                        System.out.printf(
                            "⚠️ [AUTO BID SKIP] %s bị từ chối: %s%n",
                            ruleBidder.getUserName(), e.getMessage()
                        );
                        rule.setActive(false);
                    }
                } else {
                    System.out.printf(
                        "🏳️ [AUTO BID MAX] %s đã chạm trần $%.2f%n",
                        ruleBidder.getUserName(), rule.getMaxBid()
                    );
                    rule.setActive(false);
                }
            }
        } while (hasAction);
    }

    /**
     * Lấy các AutoBidRule đang active và sắp xếp theo priority:
     *  Priority 1: maxBid cao hơn → được ưu tiên đặt giá trước.
     *  Priority 2 (tie-breaker): đăng ký sớm hơn → được ưu tiên.
     */
    private List<AutoBidRule> getSortedActiveRules(Auction auction) {
        List<AutoBidRule> result = new ArrayList<>();
        if (auction.getAutoBidRules() == null) return result;

        for (AutoBidRule rule : auction.getAutoBidRules()) {
            if (rule.isActive()) result.add(rule);
        }

        result.sort((r1, r2) -> {
            int cmp = Double.compare(r2.getMaxBid(), r1.getMaxBid()); // Giảm dần
            if (cmp != 0) return cmp;
            return r1.getRegisterTime().compareTo(r2.getRegisterTime()); // Cũ hơn lên trước
        });

        return result;
    }

    @Override
    public String getStrategyName() {
        return "AutoBidStrategy";
    }
}
