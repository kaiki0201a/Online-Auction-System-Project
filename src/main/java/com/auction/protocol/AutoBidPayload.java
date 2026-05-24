package com.auction.protocol;

import java.io.Serializable;

public class AutoBidPayload implements Serializable {
    private String auctionId;
    private String bidderId;
    private double maxAmount;
    private double incrementAmount;
    private boolean enable;  // true = bật, false = tắt

    // Constructor + Getters
    public AutoBidPayload(String auctionId, String bidderId,
                          double maxAmount, double incrementAmount, boolean enable) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.maxAmount = maxAmount;
        this.incrementAmount = incrementAmount;
        this.enable = enable;
    }
    public String getAuctionId()       { return auctionId; }
    public String getBidderId()        { return bidderId; }
    public double getMaxAmount()       { return maxAmount; }
    public double getIncrementAmount() { return incrementAmount; }
    public boolean isEnable()          { return enable; }
}