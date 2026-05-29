package com.auction.utils;

import com.auction.exception.AuthenticationException;
import com.auction.model.Admin;
import com.auction.model.Bidder;
import com.auction.model.Seller;
import com.auction.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserManagerTest {

    private UserManager userManager;

    /**
     * Setup 3 tài khoản mẫu trực tiếp qua addUser() thay vì dựa vào hardcode trong constructor.
     * Lý do: sau BUG-03 fix, UserManager constructor không còn tạo sẵn tài khoản nữa.
     * Các test phải tự quản lý data của mình để độc lập với implementation detail.
     */
    @BeforeEach
    void setUp() {
        userManager = UserManager.getInstance();

        // Seed 3 tài khoản mặc định cần thiết cho test
        // Dùng addUser() để không phụ thuộc vào constructor
        if (userManager.getUser("bidder") == null) {
            userManager.addUser(new Bidder("bidder", "123", "bidder@gmail.com", 50000.0));
        }
        if (userManager.getUser("admin") == null) {
            userManager.addUser(new Admin("admin", "123", "admin@gmail.com", "admin_code"));
        }
        if (userManager.getUser("seller") == null) {
            userManager.addUser(new Seller("seller", "123", "seller@gmail.com"));
        }
    }

    @Test
    void testAuthenticate_Success() throws AuthenticationException {
        // Kiểm tra xác thực thành công với tài khoản đã seed trong @BeforeEach
        assertTrue(userManager.authenticate("bidder", "123"));
        assertTrue(userManager.authenticate("admin", "123"));
    }

    @Test
    void testAuthenticate_WrongPassword() {
        // Kiểm tra sai mật khẩu -> authenticate ném AuthenticationException
        assertThrows(AuthenticationException.class, () ->
            userManager.authenticate("bidder", "wrongpass")
        );
    }

    @Test
    void testAuthenticate_NonExistentUser() {
        // Kiểm tra tài khoản không tồn tại -> ném AuthenticationException
        assertThrows(AuthenticationException.class, () ->
            userManager.authenticate("ghost", "123")
        );
    }

    @Test
    void testRegister_Success() {
        // Đăng ký tài khoản mới hợp lệ — dùng timestamp để tránh trùng giữa các lần chạy
        boolean result = userManager.register("newuser_test_" + System.currentTimeMillis(), "pass", "new@gmail.com");
        assertTrue(result);
    }

    @Test
    void testRegister_WithRole_Bidder() {
        String username = "bidder_test_" + System.currentTimeMillis();
        boolean result = userManager.register(username, "pass123", "bidder@test.com", "Bidder");
        assertTrue(result);
        User user = userManager.getUser(username);
        assertNotNull(user);
        assertInstanceOf(com.auction.model.Bidder.class, user);
    }

    @Test
    void testRegister_WithRole_Seller() {
        String username = "seller_test_" + System.currentTimeMillis();
        boolean result = userManager.register(username, "pass123", "seller@test.com", "Seller");
        assertTrue(result);
        User user = userManager.getUser(username);
        assertNotNull(user);
        assertInstanceOf(com.auction.model.Seller.class, user);
    }

    @Test
    void testRegister_DuplicateUsername() {
        // "seller" đã được seed trong @BeforeEach -> đăng ký lại phải thất bại
        boolean result = userManager.register("seller", "newpass", "test@gmail.com");
        assertFalse(result, "Không được phép đăng ký trùng username");
    }

    @Test
    void testGetAllUsers() {
        List<User> users = userManager.getAllUsers();
        assertNotNull(users);
        // Phải có ít nhất 3 user vì @BeforeEach đã seed bidder, admin, seller
        assertTrue(users.size() >= 3, "Phải có ít nhất 3 user mặc định được tạo ra");
    }
}