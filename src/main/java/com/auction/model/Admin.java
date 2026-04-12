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
        //1. Huỷ đấu giá khi đấu giá chưa kết thúc
        if (auction.getStatus() == AuctionStatus.OPEN || auction.getStatus() == AuctionStatus.RUNNING) {
            auction.setStatus(AuctionStatus.CANCELED);
            System.out.println("!!! Admin [" + this.getUserName() + "] ĐÃ HUỶ phiên đấu giá " + auction.getId());
            System.out.println("Lý do vi phạm: " + reason);
        } else {
            System.out.println("Không thể huỷ phiên đấu giá vì đang ở trạng thái: " + auction.getStatus());
        }
    }
    // Method 2: Khoá tài khoản người dùng vi phạm
    public void banUser(User user, String reason){
        // TODO: Cần thêm thuộc tính 'boolean isBanned" vào lớp cha User
        user.setBanned(false);
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
