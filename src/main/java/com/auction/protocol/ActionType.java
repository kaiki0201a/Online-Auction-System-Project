package com.auction.protocol;

import java.io.Serializable;

public enum ActionType implements Serializable {
    LOGIN,
    CREATE_AUCTION,
    REGISTER,
    GET_AUCTION_LIST,
    UPDATE_AUCTION,
    PLACE_BID,
    LOGOUT,
    // --- THÊM 3 HÀNH ĐỘNG CỦA ADMIN VÀO ĐÂY ---
    GET_USER_LIST,      // Lấy danh sách người dùng
    BAN_USER,           // Khóa/Mở khóa tài khoản
    CANCEL_AUCTION      // Ép dừng phiên đấu giá
}