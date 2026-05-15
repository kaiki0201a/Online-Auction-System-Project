package com.auction.model;
import com.auction.model.*;
public class AutoBidRule {
    // Dùng từ khóa 'final' để bảo vệ dữ liệu, chống việc bị sửa đổi sau khi đã tạo
    private final Bidder bidder;
    private final double maxBid;
    private final double increment;

    // THÊM MỚI: Biến kiểm soát trạng thái của lệnh Auto-Bid
    // Nếu hết tiền hoặc chạm trần maxBid, biến này sẽ bị tắt thành false
    private boolean active;

    public AutoBidRule(Bidder bidder, double maxBid, double increment) {
        this.bidder = bidder;
        this.maxBid = maxBid;
        this.increment = increment;
        
        // Mặc định khi vừa đăng ký xong, lệnh này luôn có hiệu lực
        this.active = true; 
    }

    // --- GETTERS CHO DỮ LIỆU CỐ ĐỊNH ---
    public Bidder getBidder() { return bidder; }
    public double getMaxBid() { return maxBid; }
    public double getIncrement() { return increment; }

    // --- GETTER & SETTER CHO TRẠNG THÁI ACTIVE (KHỚP VỚI TRIGGER_AUTO_BID) ---
    public boolean isActive() { 
        return active; 
    }

    public void setActive(boolean active) { 
        this.active = active; 
    }

    // (Tùy chọn) Thêm hàm toString để dễ in ra Console kiểm tra (Debug)
    @Override
    public String toString() {
        return "AutoBidRule{" +
                "bidder=" + bidder.getUserName() +
                ", maxBid=" + maxBid +
                ", increment=" + increment +
                ", active=" + active +
                '}';
    }
}