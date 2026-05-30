package com.auction.utils;

import com.auction.model.User;

/**
 * AppContext — lưu trạng thái phiên đăng nhập hiện tại trên client.
 *
 * Lưu ý: currentUser là static field, chỉ nên truy cập từ JavaFX Application Thread.
 */
public class AppContext {

    /** Utility class — không cho phép khởi tạo. */
    private AppContext() {}

    /** User đang đăng nhập; null nếu chưa đăng nhập hoặc đã đăng xuất. */
    private static User currentUser;

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    /** Xóa currentUser — gọi khi người dùng nhấn Đăng xuất. */
    public static void logout() {
        currentUser = null;
    }
}