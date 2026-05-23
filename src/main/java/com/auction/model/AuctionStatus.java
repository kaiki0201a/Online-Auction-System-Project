package com.auction.model;

public enum AuctionStatus {
    PENDING_APPROVAL, // Chờ Admin duyệt
    APPROVED,         // Đã duyệt - chờ đến giờ bắt đầu
    OPEN,             // Đang mở (tương thích cũ)
    RUNNING,          // Đang diễn ra (Live)
    REJECTED,         // Bị Admin từ chối
    FINISHED,         // Đã kết thúc
    PAID,             // Đã thanh toán
    CANCELED          // Đã hủy
}