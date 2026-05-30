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

/**
 * UserManager — Singleton quản lý toàn bộ User trong RAM.
 * Dữ liệu được nạp từ file lúc khời động, và flush xuống file sau mỗi thay đổi.
 */
public class UserManager {

    // ─── CONSTANTS ───────────────────────────────────────────────────────────────
    /** Mã xác nhận Admin — phải khớp khi đăng ký role Admin. */
    private static final String ADMIN_SECRET_CODE   = "ADMIN2024";
    /** Số dư khởi đầu cho mọi Bidder mới đăng ký. */
    private static final double INITIAL_BIDDER_BALANCE = 10_000.0;

    // ─── SINGLETON ──────────────────────────────────────────────────────────────
    private static UserManager instance;
    private final Map<String, User> users = new ConcurrentHashMap<>();

    private UserManager() {
        // Tài khoản mặc định — sẽ bị ghi đè bởi restoreUsers() nếu file dữ liệu tồn tại.
        users.put("bidder", new Bidder("bidder", "123", "bidder@gmail.com", INITIAL_BIDDER_BALANCE));
        users.put("admin",  new Admin("admin",  "123", "admin@gmail.com",  ADMIN_SECRET_CODE));
        users.put("seller", new Seller("seller", "123", "seller@gmail.com"));
    }

    /** Khởi tạo Singleton an toàn với đa luồng (Double-Checked Locking). */
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

    // ─── AUTHENTICATION ─────────────────────────────────────────────────────────

    /** Xảc thực thông tin đăng nhập; ném AuthenticationException nếu sai hoặc bị ban. */
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

    // ─── REGISTRATION ─────────────────────────────────────────────────────────

    /**
     * Đăng ký tài khoản với role cụ thể.
     * - Bidder: nhận {@value #INITIAL_BIDDER_BALANCE}$ khởi đầu.
     * - Seller: dùng ngay, không cần duyệt.
     * - Admin: cần adminCode = {@value #ADMIN_SECRET_CODE} để xác nhận.
     */
    public boolean register(String username, String password, String email, String role) {
        return register(username, password, email, role, null);
    }

    /** Overload hỗ trợ đăng ký Admin có mã xác nhận. adminCode chỉ cần khi role = "Admin". */
    public boolean register(String username, String password, String email, String role, String adminCode) {
        if (users.containsKey(username)) {
            return false;
        }
        if ("Admin".equalsIgnoreCase(role)) {
            if (!ADMIN_SECRET_CODE.equals(adminCode)) {
                return false;
            }
            users.put(username, new Admin(username, password, email, adminCode));
        } else if ("Seller".equalsIgnoreCase(role)) {
            users.put(username, new Seller(username, password, email));
        } else {
            users.put(username, new Bidder(username, password, email, INITIAL_BIDDER_BALANCE));
        }
        return true;
    }

    /** Backward-compatible: mặc định đăng ký Bidder không cần truyền role. */
    public boolean register(String username, String password, String email) {
        return register(username, password, email, "Bidder", null);
    }

    // ─── USER LOOKUP & MANAGEMENT ─────────────────────────────────────────────────

    public User getUser(String username) {
        return users.get(username);
    }

    /** Tìm User theo UUID (chậm hơn getUser do phải linear scan). */
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

    /**
     * Xóa toàn bộ user hiện tại và nạp lại từ danh sách mới.
     * Gọi bởi UserDAOImpl khi load dữ liệu từ file lúc server khởi động.
     */
    public void restoreUsers(List<User> restoredUsers) {
        users.clear();
        for (User user : restoredUsers) {
            users.put(user.getUserName(), user);
        }
    }
}