package com.auction.protocol;

import java.io.Serializable;

public enum ActionType implements Serializable {
    LOGIN,
    REGISTER,
    GET_AUCTION_LIST,   // Lấy danh sách sản phẩm đang đấu giá
    PLACE_BID,          // Đặt giá
    LOGOUT
}