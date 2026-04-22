package com.auction.model;

import com.auction.exception.InvalidBidException;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class AuctionTest {

    @Test
    public void testProcessBid_ThrowsInvalidBidException_WhenBidIsTooLow() {
        // 1. ARRANGE (Chuẩn bị dữ liệu giả)
        Seller seller = new Seller("seller1", "pass", "seller@gmail.com");
        Bidder bidder = new Bidder("bidder1", "pass", "bidder@gmail.com", 5000.0); // Có 5000$

        // Tạo một bức tranh giá khởi điểm 100$
        Item monaLisa = new Art("Mona Lisa", "Tranh", 100.0, "Da Vinci", 1500);

        // Tạo phiên đấu giá
        Auction auction = new Auction(monaLisa, seller, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        auction.setStatus(AuctionStatus.RUNNING); // Ép nó chạy để test

        // Tạo một giao dịch lỗi (Giá khởi điểm 100$, nhưng chỉ trả 50$)
        BidTransaction badTransaction = new BidTransaction(auction, bidder, 50.0);

        // 2 & 3. ACT & ASSERT (Thực thi và Kiểm tra lỗi)
        // Chúng ta kỳ vọng (assertThrows) rằng khi chạy auction.processBid, nó SẼ NÉM RA InvalidBidException
        Exception exception = assertThrows(InvalidBidException.class, () -> {
            auction.processBid(badTransaction);
        });

        // (Tuỳ chọn) Kiểm tra xem câu thông báo lỗi có đúng ý mình không
        assertTrue(exception.getMessage().contains("phải lớn hơn mức giá cao nhất hiện tại"));
    }
}