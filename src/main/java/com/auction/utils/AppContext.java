package com.auction.utils;

import com.auction.model.User;

public class AppContext {
    // Biến static lưu trữ thông tin người dùng đang đăng nhập
    private static User currentUser;

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    // Hàm gọi khi nhấn Đăng xuất
    public static void logout() {
        currentUser = null;
    }
}