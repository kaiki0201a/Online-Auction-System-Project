package com.auction.strategy;

import com.auction.exception.AuctionException;
import com.auction.exception.InvalidBidException;
import com.auction.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho Strategy Pattern (ManualBidStrategy, AutoBidStrategy, BidContext).
 * Xác nhận: Strategy đúng được gọi, có thể hoán đổi tại runtime, logic bid đúng.
 */
public class BidStrategyTest {

    private Auction auction;
    private Seller seller;
    private Bidder bidder1;
    private Bidder bidder2;

    @BeforeEach
    void setUp() {
        seller  = new Seller("stratSeller", "pass", "seller@test.com");
        bidder1 = new Bidder("stratBidder1", "pass", "b1@test.com", 5000.0);
        bidder2 = new Bidder("stratBidder2", "pass", "b2@test.com", 5000.0);

        Art item = new Art("Strategy Test Item", "Mô tả test", 500.0, "Artist", 2024);
        auction  = new Auction(item, seller,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(2));
        auction.setStatus(AuctionStatus.RUNNING);
    }

    // ─── TEST 1: ManualBidStrategy đặt giá thành công ────────────────────────

    @Test
    @DisplayName("ManualBidStrategy: Đặt giá hợp lệ → thành công, trừ tiền bidder")
    void testManualBidStrategy_Success() throws AuctionException {
        double bidAmount = 600.0;
        double balanceBefore = bidder1.getBalance();

        BidTransaction tx = new BidTransaction(auction, bidder1, bidAmount);
        BidContext ctx = new BidContext(ManualBidStrategy.getInstance());
        ctx.executeBid(auction, tx);

        assertEquals(bidAmount, auction.getCurrentHighestBid(),
                "Giá cao nhất phải được cập nhật thành " + bidAmount);
        assertEquals(bidder1.getUserName(), auction.getHighestBidder().getUserName(),
                "Bidder1 phải là người dẫn đầu sau khi đặt giá");
        assertEquals(balanceBefore - bidAmount, bidder1.getBalance(), 0.001,
                "Số dư bidder phải bị trừ đúng sau khi đặt giá");
    }

    // ─── TEST 2: ManualBidStrategy từ chối giá quá thấp ─────────────────────

    @Test
    @DisplayName("ManualBidStrategy: Giá thấp hơn hiện tại → InvalidBidException")
    void testManualBidStrategy_InvalidBid_TooLow() {
        BidTransaction tx = new BidTransaction(auction, bidder1, 100.0); // thấp hơn startingPrice 500

        BidContext ctx = new BidContext(ManualBidStrategy.getInstance());
        assertThrows(InvalidBidException.class, () -> ctx.executeBid(auction, tx),
                "Phải throw InvalidBidException khi giá thấp hơn giá hiện tại");
    }

    // ─── TEST 3: BidContext hoán đổi Strategy tại runtime ────────────────────

    @Test
    @DisplayName("BidContext: Hoán đổi strategy tại runtime (Manual → Auto)")
    void testBidContext_SwitchStrategy_AtRuntime() {
        BidContext ctx = new BidContext(ManualBidStrategy.getInstance());
        assertEquals("ManualBidStrategy", ctx.getStrategy().getStrategyName());

        ctx.setStrategy(AutoBidStrategy.getInstance());
        assertEquals("AutoBidStrategy", ctx.getStrategy().getStrategyName(),
                "BidContext phải phản ánh strategy mới sau khi setStrategy()");
    }

    // ─── TEST 4: AutoBidStrategy kích hoạt tự động khi có bid thủ công ───────

    @Test
    @DisplayName("AutoBidStrategy: Robot tự đặt giá khi bị vượt")
    void testAutoBidStrategy_TriggersWhenOutbid() throws AuctionException {
        // Cài Auto-Bid cho bidder1: max=1000, step=50
        auction.registerAutoBid(bidder1, 1000.0, 50.0);

        // Bidder2 đặt thủ công 600 → phải trigger robot của bidder1
        BidTransaction manualTx = new BidTransaction(auction, bidder2, 600.0);
        BidContext ctx = new BidContext(ManualBidStrategy.getInstance());
        ctx.executeBid(auction, manualTx);

        // Robot bidder1 phải đã phản ứng: giá phải > 600
        assertTrue(auction.getCurrentHighestBid() > 600.0,
                "Robot của bidder1 phải đã đặt giá cao hơn 600");
        assertEquals(bidder1.getUserName(), auction.getHighestBidder().getUserName(),
                "Bidder1 (robot) phải là người dẫn đầu sau khi auto-bid");
    }

    // ─── TEST 5: ManualBidStrategy là Singleton ───────────────────────────────

    @Test
    @DisplayName("ManualBidStrategy và AutoBidStrategy là Singleton")
    void testStrategies_AreSingleton() {
        assertSame(ManualBidStrategy.getInstance(), ManualBidStrategy.getInstance(),
                "ManualBidStrategy.getInstance() phải trả về cùng 1 object");
        assertSame(AutoBidStrategy.getInstance(), AutoBidStrategy.getInstance(),
                "AutoBidStrategy.getInstance() phải trả về cùng 1 object");
    }
}
