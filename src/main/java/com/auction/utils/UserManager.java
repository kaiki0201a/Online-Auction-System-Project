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
        // Khởi tạo một số tài khoản có sẵn tiền để test (Thay thế cho DB thật nếu chưa làm)
        // ĐÃ FIX: Đồng nhất tên key và username bên trong Object để lúc hiển thị lên bảng không bị lệch
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

    // đã sửa level 2: Xử lý đăng nhập với exception handling
    public boolean authenticate(String username, String password) throws AuthenticationException {
        User user = users.get(username);
        if (user == null) {
            throw new AuthenticationException("Tài khoản '" + username + "' không tồn tại!");
        }
        // Hàm login() của User tự động ném AuthenticationException nếu thất bại
        return user.login(password);
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

    // đã sửa level 2: Thêm method để tìm user theo ID
    public User getUserById(String userId) {
        for (User user : users.values()) {
            if (user.getId().equals(userId)) {
                return user;
            }
        }
        return null;
    }

    // đã sửa level 2: Thêm method để thêm user mới
    public void addUser(User user) {
        users.put(user.getUserName(), user);
    }

    // đã sửa level 2: Thêm method để xóa user
    public boolean removeUser(String username) {
        return users.remove(username) != null;
    }

    // THÊM MỚI: Hàm lấy toàn bộ danh sách người dùng cho Admin
    public List<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    // THÊM HÀM NÀY ĐỂ DAO CÓ THỂ NẠP DỮ LIỆU TỪ FILE LÊN RAM
    public void restoreUsers(List<User> loadedUsers) {
        users.clear(); // Xóa các tài khoản mặc định (như admin, bidder test)
        for (User user : loadedUsers) {
            // Lưu ý: Đảm bảo class User của bạn có hàm getUserName()
            users.put(user.getUserName(), user);
        }
    }

}