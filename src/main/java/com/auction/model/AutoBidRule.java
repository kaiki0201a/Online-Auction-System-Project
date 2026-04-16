package com.auction.model;
import com.auction.model.*;
public class AutoBidRule {
    private Bidder bidder;
    private Auction auction;
    private double maxBid;
    private double increment;

    public AutoBidRule(Bidder bidder, Auction auction, double maxBid, double increment) {
        this.bidder = bidder;
        this.maxBid = maxBid;
        this.increment = increment;
        this.auction = auction;
    }

    // Getters
    public Bidder getBidder() { return bidder; }
    public double getMaxBid() { return maxBid; }
    public double getIncrement() { return increment; }
    public Auction getAuction() {
        return auction;
    }
}