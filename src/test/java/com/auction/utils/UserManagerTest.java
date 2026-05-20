package com.auction.utils;

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
    void testAuthenticate_Success() {
        // Kiểm tra tài khoản có sẵn trong constructor
        assertTrue(userManager.authenticate("bidder", "123"));
        assertTrue(userManager.authenticate("admin", "123"));
    }

    @Test
    void testAuthenticate_WrongPassword() {
        // Kiểm tra sai mật khẩu
        assertFalse(userManager.authenticate("bidder", "wrongpass"));
    }

    @Test
    void testAuthenticate_NonExistentUser() {
        // Kiểm tra tài khoản không tồn tại
        assertFalse(userManager.authenticate("ghost", "123"));
    }

    @Test
    void testRegister_Success() {
        // Đăng ký tài khoản mới hợp lệ
        boolean result = userManager.register("newuser", "pass", "new@gmail.com");
        assertTrue(result);
        assertNotNull(userManager.getUser("newuser"));
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