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
    @DisplayName("Test 5: Anti-Sniping gia hạn thêm 60 giây khi bid ở 10 giây cuối")
    public void testAntiSnipingExtendsTime() throws Exception {
        // 1. Chuẩn bị: Ép thời gian kết thúc của phiên đấu giá về còn ĐÚNG 10 GIÂY
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime shortEndTime = now.plusSeconds(10); 
        
        Auction urgentAuction = new Auction(item, seller, now.minusMinutes(10), shortEndTime);
        urgentAuction.setStatus(AuctionStatus.RUNNING);

        // 2. Hành động: Tung lệnh đặt giá
        BidTransaction quickTx = new BidTransaction(urgentAuction, bidder1, 200.0);
        urgentAuction.processBid(quickTx);

        // 3. Kiểm tra: Thời gian mới phải bằng shortEndTime + 60s
        LocalDateTime expectedNewEndTime = shortEndTime.plusSeconds(60);
        
        assertEquals(
            expectedNewEndTime, 
            urgentAuction.getEndTime(), 
            "Lỗi: Anti-Sniping không gia hạn đúng 60 giây!"
        );
    }

    // TEST 6: 2 ROBOT ĐẤNH NHAU (AUTO-BID)
    @Test
    @DisplayName("Test 6: Hai Robot Auto-Bid chiến đấu, Robot Max Bid cao hơn giành chiến thắng")
    public void testAutoBiddingTriggersCorrectly() throws Exception {
        // Đại gia 1 (Robot A): Max 1000$, Bước nhảy 50$
        auction.registerAutoBid(bidder1, 1000.0, 50.0);

        // Dân cày 2 (Robot B): Max 500$, Bước nhảy 20$
        auction.registerAutoBid(bidder2, 500.0, 20.0);

        // Người mồi nhử ném vào 150$ để đánh thức 2 robot
        Bidder mồiNhử = new Bidder("MoiNhu", "pass", "moi@gmail.com", 2000.0);
        BidTransaction manualTx = new BidTransaction(auction, mồiNhử, 150.0);
        
        // Cú processBid này sẽ kích hoạt chuỗi combat của 2 robot bên trong
        auction.processBid(manualTx);

        // Kiểm tra kết quả:
        // Robot A và B sẽ liên tục đè giá nhau. Khi giá đẩy lên trên 500$, Robot B sẽ bỏ cuộc.
        // Robot A sẽ chốt hạ ở mức giá 550$ (do 500$ + 50$ bước nhảy của nó).
        assertNotNull(auction.getHighestBidder());
        assertEquals(bidder1.getUserName(), auction.getHighestBidder().getUserName(), "Lỗi: Đại gia 1 phải là người chiến thắng");
        assertEquals(550.0, auction.getCurrentHighestBid(), "Lỗi: Robot tính sai số tiền combat cuối cùng!");
    }

    // TEST 7: ĐA LUỒNG - 10 NGƯỜI CÙNG ĐẶT GIÁ
    @Test
    @DisplayName("Test 7: Stress Test Đa Luồng (10 người cùng đặt giá 1 lúc)")
    public void testConcurrencySafeBidding() throws InterruptedException {
        int numberOfThreads = 10; // Chỉnh về đúng 10 theo task
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        
        // Latch dùng để đồng bộ: Bắt 10 luồng đứng chờ ở vạch xuất phát
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        for (int i = 1; i <= numberOfThreads; i++) {
            // Giá trị bid từ 101.0 đến 110.0
            final double bidAmount = 100.0 + i; 
            Bidder threadBidder = new Bidder("Clone_" + i, "pass", "clone" + i + "@gmail.com", 1000.0);

            executorService.submit(() -> {
                try {
                    readyLatch.countDown(); // Báo cáo đã nạp đạn xong
                    startLatch.await();     // Chờ tiếng súng lệnh
                    
                    // Thực hiện bắn request
                    BidTransaction tx = new BidTransaction(auction, threadBidder, bidAmount);
                    auction.processBid(tx);
                } catch (Exception ignored) {
                    // Luồng nào chạy chậm hơn sẽ bị ném InvalidBidException do giá bị đè,
                    // việc này là hoàn toàn bình thường trong đa luồng.
                } finally {
                    finishLatch.countDown(); 
                }
            });
        }

        readyLatch.await(); // Đợi 10 luồng sẵn sàng
        startLatch.countDown(); // Bóp cò cho 10 luồng chạy CÙNG MỘT TÍCH TẮC
        finishLatch.await(5, TimeUnit.SECONDS); // Chờ tối đa 5s cho chạy xong
        executorService.shutdown();

        // Kiểm tra GẮT GAO:
        // Do có chữ 'synchronized', dù 10 luồng đâm vào cùng lúc, dữ liệu không bị ghi đè lung tung.
        // Mức giá cuối cùng bắt buộc phải là mức giá lớn nhất được đẩy vào (110.0).
        assertEquals(110.0, auction.getCurrentHighestBid(), "Lỗi: Đồng bộ luồng (synchronized) bị hỏng, sai giá cuối!");
        assertTrue(auction.getBidHistory().size() > 0, "Lỗi: Lịch sử giao dịch bị hỏng do đa luồng!");
    }
}