package com.auction.protocol;

import java.io.Serializable;

public enum ActionType implements Serializable {
    LOGIN,
    REGISTER,           // Đăng ký tài khoản mới
    GET_AUCTION_LIST,   // Lấy danh sách sản phẩm đang đấu giá
    UPDATE_AUCTION,     // Server chủ động đẩy thông tin cập nhật giá hoặc thời gian
    PLACE_BID,          // Đặt giá
    LOGOUT
}