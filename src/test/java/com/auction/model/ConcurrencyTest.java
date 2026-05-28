package com.auction.model;

import com.auction.exception.AuctionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress test đa luồng cho Auction.processBid().
 * Đảm bảo không xảy ra: lost update, race condition, hai người cùng thắng.
 */
public class ConcurrencyTest {

    private Auction auction;
    private Seller seller;

    @BeforeEach
    public void setUp() {
        seller = new Seller("seller_stress", "pass", "seller@mail.com");
        Art item = new Art("Tranh Mona Lisa", "Đồ cổ", 1000.0, "Da Vinci", 1500);
        auction = new Auction(item, seller, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        auction.setStatus(AuctionStatus.RUNNING); // Bỏ qua luồng duyệt → test trực tiếp logic bid
    }

    /**
     * Test 1: 100 luồng cùng đặt giá song song.
     * Giá đặt: 1010, 1020, ..., 2000 (thread i đặt 1000 + i*10).
     * Kết quả kỳ vọng: giá cuối = 2000.0, không bị lost update.
     */
    @Test
    @DisplayName("Stress Test: 100 luồng bidder đặt giá đồng thời — giá cuối phải là 2000.0")
    public void testConcurrentBidding_100Threads_NoLostUpdate() throws InterruptedException {
        int numberOfThreads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        // Chốt chặn: ép 100 luồng chạy cùng 1 mili-giây
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 1; i <= numberOfThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    Bidder bidder = new Bidder("Bidder_" + index, "pass",
                            "b" + index + "@mail.com", 50000.0);
                    double bidAmount = 1000.0 + (index * 10.0); // 1010 → 2000
                    BidTransaction tx = new BidTransaction(auction, bidder, bidAmount);

                    startGate.await(); // Chờ tiếng súng lệnh
                    auction.processBid(tx);
                    successCount.incrementAndGet();

                } catch (AuctionException e) {
                    // Luồng chậm hơn bị InvalidBidException (giá đã bị đè) — bình thường
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown(); // Mở cổng: 100 luồng lao vào CÙNG 1 TÍCH TẮC
        boolean finished = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Tất cả 100 luồng phải hoàn thành trong 10 giây");

        // Kiểm tra kết quả nghiêm ngặt:
        // 1. Giá cuối phải là 2000.0 (cao nhất có thể)
        assertEquals(2000.0, auction.getCurrentHighestBid(),
                "Lỗi: synchronized bị hỏng — giá cuối phải là 2000.0 (cao nhất)!");

        // 2. Phải có ít nhất 1 giao dịch thành công
        assertTrue(auction.getBidHistory().size() > 0,
                "Lỗi: Không có lượt đặt giá nào thành công!");

        // 3. Người thắng phải là Bidder_100 (người đặt giá cao nhất)
        assertNotNull(auction.getHighestBidder(), "Phải có người dẫn đầu");
        assertEquals("Bidder_100", auction.getHighestBidder().getUserName(),
                "Lỗi: Người thắng phải là Bidder_100 (người đặt 2000.0)!");

        System.out.printf("✅ Stress test hoàn tất: %d lượt thành công, giá cuối: $%.1f%n",
                successCount.get(), auction.getCurrentHighestBid());
    }

    /**
     * Test 2: Hai luồng cùng đặt giá y hệt nhau — chỉ 1 người được chấp nhận.
     * Đảm bảo không xảy ra "hai người cùng thắng" (split-brain).
     */
    @Test
    @DisplayName("Race Condition: Hai luồng đặt cùng một mức giá — chỉ 1 người thắng")
    public void testConcurrentBidding_SamePrice_OnlyOneWins() throws InterruptedException {
        Bidder bidderA = new Bidder("RaceA", "pass", "a@mail.com", 50000.0);
        Bidder bidderB = new Bidder("RaceB", "pass", "b@mail.com", 50000.0);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);

        double samePrice = 1500.0;

        ExecutorService exec = Executors.newFixedThreadPool(2);
        for (Bidder bidder : new Bidder[]{bidderA, bidderB}) {
            exec.submit(() -> {
                try {
                    BidTransaction tx = new BidTransaction(auction, bidder, samePrice);
                    startGate.await();
                    auction.processBid(tx);
                    successCount.incrementAndGet();
                } catch (AuctionException e) {
                    // Người đến sau bị từ chối
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await(5, TimeUnit.SECONDS);
        exec.shutdown();

        // Chỉ đúng 1 người được chấp nhận
        assertEquals(1, successCount.get(),
                "Lỗi: Hai người không thể cùng thắng với mức giá y hệt nhau!");
        assertEquals(samePrice, auction.getCurrentHighestBid());
    }
}