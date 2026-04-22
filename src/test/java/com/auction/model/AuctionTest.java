package com.auction.model;

import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

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
        bidder2 = new Bidder("bidder2", "pass123", "b2@gmail.com", 100.0);  // Ít tiền

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
}