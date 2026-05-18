package com.auction.protocol;

import java.io.Serializable;

public class BidRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String auctionId;
    private String username;
    private double bidAmount;

    public BidRequest(String auctionId, String username, double bidAmount) {
        this.auctionId = auctionId;
        this.username = username;
        this.bidAmount = bidAmount;
    }

    public String getAuctionId() { return auctionId; }
    public String getUsername() { return username; }
    public double getBidAmount() { return bidAmount; }
}