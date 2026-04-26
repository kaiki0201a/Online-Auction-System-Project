package com.auction.network;
import java.io.Serializable;

public class BidRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private String bidderName;
    private double bidPrice;
    public BidRequest(String bidderName, double bidPrice) {
        this.bidderName = bidderName;
        this.bidPrice = bidPrice;
    }

    public double getBidPrice() {
        return bidPrice;
    }

    public String getBidderName() {
        return bidderName;
    }

    public double getBidAmount() {
        return bidPrice;
    }
}
