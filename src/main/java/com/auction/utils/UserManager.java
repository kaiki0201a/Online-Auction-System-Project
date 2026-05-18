package com.auction.utils;

import com.auction.model.Bidder;
import com.auction.model.User;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {
    private static UserManager instance;
    private final Map<String, User> users = new ConcurrentHashMap<>();

    private UserManager() {
        // Khởi tạo một số tài khoản có sẵn tiền để test (Thay thế cho DB thật nếu chưa làm)
        users.put("hieu", new Bidder("hieu", "123", "hieu@gmail.com", 50000.0));
        users.put("bidder1", new Bidder("bidder1", "123", "bidder1@gmail.com", 50000.0));
        users.put("bidder2", new Bidder("bidder2", "123", "bidder2@gmail.com", 30000.0));
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

    // Xử lý đăng nhập
    public boolean authenticate(String username, String password) {
        User user = users.get(username);
        return user != null && user.login(password);
    }

    // Xử lý đăng ký
    public boolean register(String username, String password, String email) {
        if (users.containsKey(username)) {
            return false; // Tài khoản đã tồn tại
        }
        // Đăng ký mặc định là Bidder với số dư 0$ (hoặc cho sẵn tiền để test)
        users.put(username, new Bidder(username, password, email, 10000.0));
        return true;
    }

    public User getUser(String username) {
        return users.get(username);
    }
}