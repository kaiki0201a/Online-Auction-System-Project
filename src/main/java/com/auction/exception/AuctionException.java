package com.auction.exception;

public class AuctionException extends Exception{
    // Constructor
    public AuctionException() {
        super("Đã xảy ra lỗi trong hệ thống đấu giá.");
    }
    // Another Constructor to save messages
    public AuctionException(String message) {
        super(message);
    }
}
