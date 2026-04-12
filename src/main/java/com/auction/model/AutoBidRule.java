package com.auction.model;
import com.auction.model.*;
public class AutoBidRule {
    private Bidder bidder;
    private double maxBid;
    private double increment;

    public AutoBidRule(Bidder bidder, double maxBid, double increment) {
        this.bidder = bidder;
        this.maxBid = maxBid;
        this.increment = increment;
    }

    // Getters
    public Bidder getBidder() { return bidder; }
    public double getMaxBid() { return maxBid; }
    public double getIncrement() { return increment; }
}