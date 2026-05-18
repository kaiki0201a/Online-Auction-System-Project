package com.auction.model;

import com.auction.exception.AuctionClosedException;
import com.auction.exception.AuctionException;
import com.auction.exception.InvalidBidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class AuctionTest {

    private Auction auction;
    private Seller seller;
    private Bidder bidder1;
    private Bidder bidder2;
    private Item item;

    // 1. CHUẨN BỊ MÔI TRƯỜNG (Chạy trước mỗi hàm @Test)
    @BeforeEach
    public void setUp() {
        seller = new Seller("seller", "pass123", "seller@gmail.com");
        bidder1 = new Bidder("bidder1", "pass123", "b1@gmail.com", 5000.0); // Dư dả tiền
        bidder2 = new Bidder("bidder2", "pass123", "b2@gmail.com", 2000.0);  // Ít tiền

        // Tranh giá khởi điểm 100$
        item = new Art("Mona Lisa", "Tranh cổ", 100.0, "Da Vinci", 1500);

        // Tạo phiên đấu giá kéo dài 1 tiếng
        auction = new Auction(item, seller, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        auction.setStatus(AuctionStatus.RUNNING); // Bật trạng thái đang chạy
    }

    // ================= CÁC BÀI TEST =================

    // TEST 1: ĐẶT GIÁ HỢP LỆ (Valid Bid)
    @Test
    public void testProcessBid_Success_WhenBidIsValid() {
        BidTransaction validTransaction = new BidTransaction(auction, bidder1, 150.0);

        // Kỳ vọng: Không ném ra bất kỳ lỗi nào (assertDoesNotThrow)
        assertDoesNotThrow(() -> {
            auction.processBid(validTransaction);
        });

        // Kỳ vọng: Giá hiện tại cập nhật thành 150$, người dẫn đầu là bidder1
        assertEquals(150.0, auction.getCurrentHighestBid());
        assertEquals(bidder1, auction.getHighestBidder());
    }

    // TEST 2: ĐẶT GIÁ KHÔNG HỢP LỆ - GIÁ QUÁ THẤP (Invalid Bid)
    @Test
    public void testProcessBid_ThrowsInvalidBidException_WhenBidIsTooLow() {
        // Giá sàn đang là 100$, nhưng chỉ đặt 50$
        BidTransaction badTransaction = new BidTransaction(auction, bidder1, 50.0);

        // Kỳ vọng: Phải ném ra lỗi InvalidBidException
        assertThrows(InvalidBidException.class, () -> {
            auction.processBid(badTransaction);
        });
    }

    // TEST 3: ĐẶT GIÁ KHÔNG HỢP LỆ - GIAN LẬN TỰ ĐẶT GIÁ
    @Test
    public void testProcessBid_ThrowsInvalidBidException_WhenSellerBidsOnOwnItem() {
        // Ép kiểu Seller thành Bidder (giả lập người bán dùng clone account)
        Bidder cheatingSeller = new Bidder(seller.getUserName(), seller.getPassWord(), seller.getEmail(), 9999);
        cheatingSeller.setId(seller.getId()); // Copy ID để giống hệt người bán

        BidTransaction cheatTransaction = new BidTransaction(auction, cheatingSeller, 200.0);

        // Kỳ vọng: Hệ thống phải bắt được lỗi gian lận
        Exception exception = assertThrows(InvalidBidException.class, () -> {
            auction.processBid(cheatTransaction);
        });
        assertTrue(exception.getMessage().contains("Người bán không được tự đấu giá"));    }

    // TEST 4: KẾT THÚC PHIÊN - TRẢ GIÁ KHI PHIÊN ĐÃ ĐÓNG
    @Test
    public void testProcessBid_ThrowsAuctionClosedException_WhenStatusIsNotRunning() {
        // Chuyển trạng thái sang kết thúc
        auction.setStatus(AuctionStatus.FINISHED);

        BidTransaction lateTransaction = new BidTransaction(auction, bidder1, 500.0);

        // Kỳ vọng: Phải ném ra lỗi AuctionClosedException
        assertThrows(AuctionClosedException.class, () -> {
            auction.processBid(lateTransaction);
        });
    }
    // TEST 5: ANTI-SNIPING TỰ ĐỘNG GIA HẠN THỜI GIAN
    @Test
    @DisplayName("Test 5: Anti-Sniping tự động gia hạn thêm 60 giây khi bid ở phút chót")
    public void testAntiSnipingExtendsTime() throws Exception {
        // 1. Chuẩn bị (Arrange): Ép thời gian kết thúc của phiên đấu giá về còn đúng 15 giây nữa (Nằm trong khung 30s)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime shortEndTime = now.plusSeconds(15); 
        
        // Tạo một phiên đấu giá đặc biệt sắp kết thúc
        Auction urgentAuction = new Auction(item, seller, now.minusMinutes(10), shortEndTime);
        urgentAuction.setStatus(AuctionStatus.RUNNING);

        // 2. Hành động (Act): Tung lệnh đặt giá vào giây chót
        BidTransaction quickTx = new BidTransaction(urgentAuction, bidder1, 200.0);
        urgentAuction.processBid(quickTx);

        // 3. Kiểm tra (Assert): Đảm bảo thời gian kết thúc ban đầu (shortEndTime) phải được cộng đúng 60 giây
        LocalDateTime expectedNewEndTime = shortEndTime.plusSeconds(60);
        
        assertEquals(
            expectedNewEndTime, 
            urgentAuction.getEndTime(), 
            "Lỗi: Anti-Sniping phải gia hạn đúng 60 giây khi bid ở 30s cuối!"
        );
    }
    @Test
    @DisplayName("Test 6: Robot Auto-Bid tự động đè giá giành lại Top 1")
    public void testAutoBiddingTriggersCorrectly() throws Exception {
        // Đại gia 1 cài Auto-bid (Max: 1000$, Bước nhảy: 20$)
        bidder1.setupAutoBid(auction, 1000.0, 20.0);

        // Khách 2 vào đặt thủ công 300$
        BidTransaction manualTx = new BidTransaction(auction, bidder2, 300.0);
        auction.processBid(manualTx);

        // Kiểm tra: Robot của Đại gia 1 phải tự động đè giá 320$ (300 + 20)
        assertNotNull(auction.getHighestBidder());
        assertEquals(bidder1.getUserName(), auction.getHighestBidder().getUserName(), "Lỗi: Auto-bid không hoạt động");
        assertEquals(320.0, auction.getCurrentHighestBid(), "Lỗi: Robot không tính toán đúng bước nhảy!");
    }
    @Test
    @DisplayName("Test 7: Stress Test Đa Luồng (100 Request cùng lúc)")
    public void testConcurrencySafeBidding() throws InterruptedException {
        int numberOfThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        for (int i = 1; i <= numberOfThreads; i++) {
            final double bidAmount = 100.0 + i; // Giá tăng dần từ 101 đến 200
            Bidder threadBidder = new Bidder("Clone_" + i, "pass", "clone" + i + "@gmail.com", 1000.0);

            executorService.submit(() -> {
                try {
                    readyLatch.countDown(); // Sẵn sàng
                    startLatch.await();     // Chờ lệnh bắt đầu
                    
                    // Thực hiện bắn request
                    BidTransaction tx = new BidTransaction(auction, threadBidder, bidAmount);
                    auction.processBid(tx);
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown(); // Báo cáo chạy xong
                }
            });
        }

        readyLatch.await(); // Đợi 100 luồng nạp đạn
        startLatch.countDown(); // Bóp cò!
        finishLatch.await(5, TimeUnit.SECONDS); // Chờ tối đa 5s
        executorService.shutdown();

        // Kiểm tra xem hệ thống có bảo toàn được dữ liệu không
        assertTrue(auction.getCurrentHighestBid() <= 200.0);
        assertTrue(auction.getCurrentHighestBid() > 100.0);
        // Lưu ý: Không assert exact size là 100 vì trong môi trường đa luồng cực đoan, 
        // một số luồng có thể bị văng InvalidBid (giá thấp hơn) do luồng khác chạy quá nhanh.
        // Chỉ cần assert list > 0 và app không bị Crash là Pass xuất sắc!
        assertTrue(auction.getBidHistory().size() > 0, "Lỗi: Lịch sử giao dịch bị hỏng do đa luồng!");
    }
}