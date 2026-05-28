package com.auction.exception;

// đã sửa level 2: Tạo exception riêng để xử lý khi phiên đã kết thúc
public class AuctionFinishedException extends AuctionException {

    public AuctionFinishedException(String message) {
        super(message);
    }
}

