package com.auction.model;

import java.io.Serializable;

public class AutoBid implements Serializable {
    private String bidderId;
    private String auctionId;
    private double maxAmount;      // Giá tối đa sẵn sàng trả
    private double incrementAmount; // Mỗi lần tự bid tăng thêm bao nhiêu
    private boolean active;

    public AutoBid(String bidderId, String auctionId, double maxAmount, double incrementAmount) {
        this.bidderId = bidderId;
        this.auctionId = auctionId;
        this.maxAmount = maxAmount;
        this.incrementAmount = incrementAmount;
        this.active = true;
    }

    // Getters/Setters
    public String getBidderId()     { return bidderId; }
    public String getAuctionId()    { return auctionId; }
    public double getMaxAmount()    { return maxAmount; }
    public double getIncrementAmount() { return incrementAmount; }
    public boolean isActive()       { return active; }
    public void setActive(boolean a){ this.active = a; }
}