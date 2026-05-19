package com.auction.model;

import java.time.LocalDateTime; 
import java.io.Serializable;
public class AutoBidRule implements Serializable {

    public static final long serialVersionUID = 1L;

    // --- CÁC HẰNG SỐ CHO ANTI-SNIPING ---
    private static final int SNIPE_THRESHOLD_SECONDS = 30;
    private static final int EXTENSION_SECONDS = 60;    

    // Dùng từ khóa 'final' để bảo vệ dữ liệu, chống việc bị sửa đổi sau khi đã tạo
    private final Bidder bidder;
    private final double maxBid;
    private final double increment;
    
    // 2. THÊM BIẾN LƯU THỜI GIAN ĐĂNG KÝ (Dùng cho luật Tie-Breaker)
    private final LocalDateTime registerTime; 

    private boolean active;

    public AutoBidRule(Bidder bidder, double maxBid, double increment) {
        this.bidder = bidder;
        this.maxBid = maxBid;
        this.increment = increment;
        
        // 3. TỰ ĐỘNG CHỐT GIỜ NGAY KHI VỪA TẠO LUẬT
        this.registerTime = LocalDateTime.now(); 
        
        // Mặc định khi vừa đăng ký xong, lệnh này luôn có hiệu lực
        this.active = true; 
    }

    // --- GETTERS CHO DỮ LIỆU CỐ ĐỊNH ---
    public Bidder getBidder() { return bidder; }
    public double getMaxBid() { return maxBid; }
    public double getIncrement() { return increment; }
    
    // 4. THÊM GETTER CHO BIẾN THỜI GIAN ĐỂ HÀM BÊN KIA GỌI ĐƯỢC
    public LocalDateTime getRegisterTime() { return registerTime; }

    // --- GETTER & SETTER CHO TRẠNG THÁI ACTIVE ---
    public boolean isActive() { 
        return active; 
    }

    public void setActive(boolean active) { 
        this.active = active; 
    }

    @Override
    public String toString() {
        return "AutoBidRule{" +
                "bidder=" + bidder.getUserName() +
                ", maxBid=" + maxBid +
                ", increment=" + increment +
                ", active=" + active +
                ", registerTime=" + registerTime + // Update thêm vào toString cho dễ debug
                '}';
    }
}