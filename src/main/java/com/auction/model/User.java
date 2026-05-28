package com.auction.model;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public abstract class User extends Entity implements Serializable {
    // ID phiên bản để tránh lỗi khi nâng cấp code sau này
    private static final long serialVersionUID = 1L;

    // Fields
    private String userName, email;
    private String passWord;  // Lưu dưới dạng hash SHA-256 (đã sửa level 1)
    private boolean isBanned;

    // Constructor
    public User(String userName, String passWord, String email){
        super();    // Tự gọi để sinh ra id
        this.userName = userName;
        this.passWord = hashPassword(passWord);  // đã sửa level 1: hash password thay vì lưu plaintext
        this.email = email;
    }

    // đã sửa level 1: Thêm hàm hash password bằng SHA-256
    private static String hashPassword(String plainPassword) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(plainPassword.getBytes());
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    // Public wrapper để Controller/Server có thể hash password khi đổi mật khẩu
    public String hashPasswordPublic(String plainPassword) {
        return hashPassword(plainPassword);
    }


    // đã sửa level 1: Hàm verify password so sánh hash thay vì plaintext
    // đã sửa level 2: Ném AuthenticationException khi login thất bại
    public boolean login(String pass) throws com.auction.exception.AuthenticationException {
        if (this.isBanned) {
            throw new com.auction.exception.AuthenticationException("Tài khoản '" + this.userName + "' đã bị khóa!");
        }
        if (!this.passWord.equals(hashPassword(pass))) {
            throw new com.auction.exception.AuthenticationException("Sai mật khẩu cho tài khoản '" + this.userName + "'!");
        }
        return true;
    }
    // Hàm đăng xuất
    public void logout(){
        //Tạm thời in ra thông báo
        //Controller sẽ gọi hàm này để ngắt kết nối của người dùng
        System.out.println("Đã đăng xuất tài khoản: " + this.userName);
    }
    // Getter & Setters
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public boolean isBanned() {
        return isBanned;
    }

    public void setBanned(boolean banned) {
        isBanned = banned;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassWord() {
        return passWord;
    }

    public void setPassWord(String passWord) {
        this.passWord = passWord;
    }
}
