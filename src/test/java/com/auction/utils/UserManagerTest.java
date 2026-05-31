package com.auction.utils;

import com.auction.exception.AuthenticationException;
import com.auction.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserManagerTest {

    private UserManager userManager;

    @BeforeEach
    void setUp() {
        userManager = UserManager.getInstance();
    }

    @Test
    void testAuthenticate_Success() throws AuthenticationException {
        // Kiểm tra tài khoản có sẵn trong constructor
        assertTrue(userManager.authenticate("bidder", "123456"));
        assertTrue(userManager.authenticate("admin", "123456"));
    }

    @Test
    void testAuthenticate_WrongPassword() {
        // Kiểm tra sai mật khẩu → authenticate ném AuthenticationException
        assertThrows(AuthenticationException.class, () ->
            userManager.authenticate("bidder", "wrongpass")
        );
    }

    @Test
    void testAuthenticate_NonExistentUser() {
        // Kiểm tra tài khoản không tồn tại → ném AuthenticationException
        assertThrows(AuthenticationException.class, () ->
            userManager.authenticate("ghost", "123")
        );
    }

    @Test
    void testRegister_Success() {
        // Đăng ký tài khoản mới hợp lệ
        boolean result = userManager.register("newuser_test_" + System.currentTimeMillis(), "pass24", "new@gmail.com");
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
        // Cố tình đăng ký trùng tài khoản "seller" đã có sẵn
        boolean result = userManager.register("seller", "newpass", "test@gmail.com");
        assertFalse(result, "Không được phép đăng ký trùng username");
    }

    @Test
    void testGetAllUsers() {
        List<User> users = userManager.getAllUsers();
        assertNotNull(users);
        assertTrue(users.size() >= 3, "Phải có ít nhất 3 user mặc định được tạo ra");
    }
}