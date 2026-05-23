package com.auction.model;

public enum AuctionStatus {
    PENDING_APPROVAL, // Chờ Admin duyệt
    OPEN,
    RUNNING,
    FINISHED,
    PAID,
    CANCELED
}