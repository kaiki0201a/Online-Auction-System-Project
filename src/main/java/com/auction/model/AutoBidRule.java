package com.auction.model;

import java.time.LocalDateTime; 
import java.io.Serializable;
public class AutoBidRule implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Bidder bidder;
    private final double maxBid;
    private final double increment;
    
    /** Thời điểm đăng ký rule — dùng làm tie-breaker khi hai bidder cùng maxBid. */
    private final LocalDateTime registerTime; 

    private boolean active;

    public AutoBidRule(Bidder bidder, double maxBid, double increment) {
        this.bidder = bidder;
        this.maxBid = maxBid;
        this.increment = increment;
        
        this.registerTime = LocalDateTime.now(); 
        
        this.active = true; 
    }

    // --- GETTERS ---
    public Bidder getBidder()           { return bidder; }
    public double getMaxBid()           { return maxBid; }
    public double getIncrement()        { return increment; }
    
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
                ", registerTime=" + registerTime +
                '}';
    }
}