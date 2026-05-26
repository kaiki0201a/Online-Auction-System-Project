package com.auction.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm tra các chức năng cơ bản của lớp App (tích hợp smoke test).
 */
public class AppTest {

    /**
     * Smoke test: Đảm bảo Entity tự tạo UUID duy nhất không null.
     */
    @Test
    @DisplayName("Smoke Test: Entity tự tạo UUID duy nhất khi khởi tạo")
    public void testEntityAutoGeneratesUniqueId() {
        Seller s1 = new Seller("sellerA", "pass", "a@mail.com");
        Seller s2 = new Seller("sellerB", "pass", "b@mail.com");

        assertNotNull(s1.getId(), "ID không được null");
        assertNotNull(s2.getId(), "ID không được null");
        assertNotEquals(s1.getId(), s2.getId(), "Hai entity khác nhau phải có ID khác nhau");
    }

    /**
     * Smoke test: AuctionStatus enum có đủ các trạng thái nghiệp vụ.
     */
    @Test
    @DisplayName("Smoke Test: AuctionStatus enum có đủ trạng thái")
    public void testAuctionStatusEnumValues() {
        // Đảm bảo tất cả trạng thái quan trọng tồn tại
        assertNotNull(AuctionStatus.PENDING_APPROVAL);
        assertNotNull(AuctionStatus.APPROVED);
        assertNotNull(AuctionStatus.RUNNING);
        assertNotNull(AuctionStatus.FINISHED);
        assertNotNull(AuctionStatus.PAID);
        assertNotNull(AuctionStatus.CANCELED);
    }
}