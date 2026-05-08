package com.auction.protocol;

import java.io.Serializable;
public class BidPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private int itemId;
    private String username;
    private double bidAmount;

    public BidPayload(int itemId, String username, double bidAmount) {
        this.itemId = itemId;
        this.username = username;
        this.bidAmount = bidAmount;
    }

    public int getItemId() {
        return itemId;
    }

    public String getUsername() {
        return username;
    }

    public double getBidAmount() {
        return bidAmount;
    }
}
