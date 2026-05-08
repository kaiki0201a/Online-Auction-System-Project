package com.auction.model;

import com.auction.exception.AuctionClosedException;
import com.auction.exception.AuctionException;
import com.auction.exception.InsufficientBalanceException;
import com.auction.exception.InvalidBidException;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConcurrencyTest {
    public static void main(String[] args) {
        System.out.println("Bắt đầu test thử 100 luồng bidder chạy cùng lúc! ");
        Seller seller = new Seller("seller1", "pass", "seller@mail.com");
        Art item = new Art("Tranh Mona Lisa", "Đồ cổ", 1000.0, "Da Vinci", 1500);
        Auction auction = new Auction(item, seller, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        auction.startAuction(); 
        int numberOfThreads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        // BÍ KÍP ĐIỂM A+: Dùng chốt chặn để ép 100 luồng chạy cùng 1 mili-giây
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(numberOfThreads);

        // Nạp 100 luồng vào Executor
        for (int i = 1; i <= numberOfThreads; i++) {
            final int index = i; // Copy biến i để dùng trong lambda
            
            executor.submit(() -> {
                try {
                    // Tạo Bidder và mức giá tăng dần theo Chi tiết công việc #3
                    Bidder bidder = new Bidder("Bidder_" + index, "pass", "b" + index + "@mail.com", 50000.0);
                    double bidAmount = 1000.0 + (index * 10.0); // Mức giá: 1010, 1020, ... 2000
                    BidTransaction transaction = new BidTransaction(auction, bidder, bidAmount);

                    startGate.await();

                    // Gọi chung vào hàm auction.processBid(...) theo đúng Chi tiết công việc #3
                    auction.processBid(transaction);
                    System.out.println("✅ " + bidder.getUserName() + " đặt " + bidAmount + " thành công!");

                } catch (InvalidBidException | InsufficientBalanceException | AuctionClosedException e) {
                    // Những người chậm chân sẽ bị văng lỗi InvalidBidException (Giá quá thấp)
                    System.out.println("❌ " + "Bidder_" + index + " trượt: " + e.getMessage());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (AuctionException e) {
                    throw new RuntimeException(e);
                } finally {
                    endGate.countDown(); // Báo cáo luồng này đã xong
                }
            });
        }

        System.out.println("\nBắt đầu khởi tạo 100 luồng chạy ");
        // Mở cổng cho 100 luồng lao vào CÙNG 1 MILI-GIÂY
        startGate.countDown();

        // Đợi tất cả 100 luồng đánh nhau xong thì mới nghiệm thu
        try {
            endGate.await(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        executor.shutdown();

        // 3. TIÊU CHÍ NGHIỆM THU
        System.out.println("\nRESULT: ");
        
        // Tiêu chí 1: Kiểm tra size của History
        int finalSize = auction.getBidHistory().size();
        System.out.println("1. Số lượt đặt giá thành công (getBidHistory().size()): " + finalSize);
        
        // Tiêu chí 2: highestBidder phải là người cao nhất
        if (auction.getHighestBidder() != null) {
            System.out.println("2. Người DẪN ĐẦU (highestBidder): " + auction.getHighestBidder().getUserName());
            System.out.println("3. Mức giá chốt sổ: $" + auction.getCurrentHighestBid());
        } else {
            System.out.println("Lỗi: Không có ai đặt giá thành công!");
        }
    }
}