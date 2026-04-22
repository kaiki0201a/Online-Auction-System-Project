package com.auction.model;

public abstract class User extends Entity{
    // ID phiên bản để tránh lỗi khi nâng cấp code sau này
    private static final long serialVersionUID = 1L;

    // Fields
    private String userName, email;
    private String passWord;
    private boolean isBanned;

    // Constructor
    public User(String userName, String passWord, String email){
        super();    // Tự gọi để sinh ra id
        this.userName = userName;
        this.passWord = passWord;
        this.email = email;
    }

    // Method
    // Hàm đăng nhập
    public boolean login(String pass){
        return this.passWord.equals(pass);
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
