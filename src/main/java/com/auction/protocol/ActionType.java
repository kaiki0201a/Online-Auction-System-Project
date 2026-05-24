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
    SETUP_AUTOBID,     // Đăng ký autobid
    CANCEL_AUTOBID,    // Hủy autobid

    // User/Admin actions
    GET_USER_LIST,
    BAN_USER,
    APPROVE_AUCTION,
    REJECT_AUCTION,
    DEPOSIT,
    WITHDRAW,
    UPDATE_PROFILE,

    // Broadcast từ Server
    BROADCAST_BID_UPDATE,
    BROADCAST_AUCTION_END
}