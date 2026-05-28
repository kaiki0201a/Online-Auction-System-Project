package com.auction.utils;

import com.auction.exception.AuthenticationException;
import com.auction.model.Admin;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {
    private static UserManager instance;
    private final Map<String, User> users = new ConcurrentHashMap<>();

    private UserManager() {
        // Tài khoản mặc định để test
        users.put("bidder", new Bidder("bidder", "123", "bidder@gmail.com", 50000.0));
        users.put("admin", new Admin("admin", "123", "admin@gmail.com", "admin_code"));
        users.put("seller", new Seller("seller", "123", "seller@gmail.com"));
    }

    public static UserManager getInstance() {
        if (instance == null) {
            synchronized (UserManager.class) {
                if (instance == null) {
                    instance = new UserManager();
                }
            }
        }
        return instance;
    }

    // Xử lý đăng nhập với exception handling
    public boolean authenticate(String username, String password) throws AuthenticationException {
        User user = users.get(username);
        if (user == null) {
            throw new AuthenticationException("Tài khoản '" + username + "' không tồn tại!");
        }
        // Fix #13: Chặn tài khoản bị ban đăng nhập
        if (user.isBanned()) {
            throw new AuthenticationException("Tài khoản '" + username + "' đã bị khóa bởi Admin!");
        }
        return user.login(password);
    }

    /**
     * Đăng ký tài khoản với role cụ thể.
     * - Bidder: nhận 10,000$ khởi đầu.
     * - Seller: dùng ngay, không cần duyệt.
     * - Admin: cần adminCode = "ADMIN2024" để xác nhận.
     */
    public boolean register(String username, String password, String email, String role) {
        return register(username, password, email, role, null);
    }

    /**
     * Overload hỗ trợ đăng ký Admin có mã xác nhận.
     * adminCode chỉ cần thiết khi role = "Admin".
     */
    public boolean register(String username, String password, String email, String role, String adminCode) {
        if (users.containsKey(username)) {
            return false; // Tài khoản đã tồn tại
        }
        if ("Admin".equalsIgnoreCase(role)) {
            if (!"ADMIN2024".equals(adminCode)) {
                return false; // Mã xác nhận admin sai — trả về false thay vì tạo sai role
            }
            users.put(username, new Admin(username, password, email, adminCode));
        } else if ("Seller".equalsIgnoreCase(role)) {
            users.put(username, new Seller(username, password, email));
        } else {
            // Mặc định là Bidder với 10,000$ để bắt đầu
            users.put(username, new Bidder(username, password, email, 10000.0));
        }
        return true;
    }

    // Tương thích ngược (mặc định là Bidder)
    public boolean register(String username, String password, String email) {
        return register(username, password, email, "Bidder", null);
    }

    public User getUser(String username) {
        return users.get(username);
    }

    public User getUserById(String userId) {
        for (User user : users.values()) {
            if (user.getId().equals(userId)) {
                return user;
            }
        }
        return null;
    }

    public void addUser(User user) {
        users.put(user.getUserName(), user);
    }

    public boolean removeUser(String username) {
        return users.remove(username) != null;
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    public void restoreUsers(List<User> loadedUsers) {
        users.clear();
        for (User user : loadedUsers) {
            users.put(user.getUserName(), user);
        }
    }
}