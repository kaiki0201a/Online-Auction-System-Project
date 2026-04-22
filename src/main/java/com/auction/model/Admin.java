package com.auction.model;

public class Admin extends User{
    // ID phiên bản để tránh lỗi khi nâng cấp code sau này
    private static final long serialVersionUID = 1L;

    //Fields
    // Admin có thể chia cấp bậc( ví dụ: SUPER_ADMIN, MODERATION)
    private String roleLevel;

    // Constructor
    public Admin(String userName, String passWord, String email, String roleLevel) {
        super(userName, passWord, email);
        this.roleLevel = roleLevel;
    }

    // Method 1: Huỷ một phiên đấu giá vi phạm
    public void cancelAuction(Auction auction, String reason) {
        boolean success = auction.cancelAuction(this, reason);
        //1. Huỷ đấu giá khi đấu giá chưa kết thúc
        if (success) {
            System.out.println("!!! Admin [" + this.getUserName() + "] ĐÃ HUỶ phiên đấu giá " + auction.getAuctionId());
        } else {
            System.out.println("Admin không thể huỷ phiên đấu giá này.");
        }
    }
    // Method 2: Khoá tài khoản người dùng vi phạm
    public void banUser(User user, String reason){
        // TODO: Cần thêm thuộc tính 'boolean isBanned" vào lớp cha User
        user.setBanned(true);
        System.out.println("!!! Admin [" + this.getUserName() + "] đã BAN tài khoản: " + user.getUserName());
        System.out.println("Lý do: " + reason);
    }

    public String getRoleLevel() {
        return roleLevel;
    }

    public void setRoleLevel(String roleLevel) {
        this.roleLevel = roleLevel;
    }
}
