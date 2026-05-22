package com.auction.exception;

// đã sửa level 1: Tạo exception mới để xử lý lỗi liên quan đến auction không hợp lệ
public class InvalidAuctionException extends AuctionException {

    public InvalidAuctionException(String message) {
        super(message);
    }
}

