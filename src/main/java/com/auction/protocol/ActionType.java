package com.auction.protocol;

import java.io.Serializable;

public enum ActionType implements Serializable {
    LOGIN,
    REGISTER,
    LOGOUT,

    // Auction actions
    CREATE_AUCTION,
    GET_AUCTION_LIST,
    UPDATE_AUCTION,
    CANCEL_AUCTION,
    PLACE_BID,
    SET_AUTOBID,

    // User/Admin actions
    GET_USER_LIST,
    BAN_USER,
    APPROVE_AUCTION,   // Admin duyệt sản phẩm chờ
    REJECT_AUCTION,    // Admin từ chối sản phẩm
    DEPOSIT,           // Nạp tiền (giả lập)
    WITHDRAW,          // Rút tiền (giả lập)
    UPDATE_PROFILE,    // Cập nhật thông tin cá nhân / đổi mật khẩu

    // Broadcast từ Server
    BROADCAST_BID_UPDATE,
    BROADCAST_AUCTION_END,

    // Cập nhật số dư cho Seller sau khi phiên kết thúc và settlement xong
    SELLER_BALANCE_UPDATE
}