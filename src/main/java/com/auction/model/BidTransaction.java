package com.auction.model;
import java.time.LocalDateTime;
public class BidTransaction {
    private Bidder bidder;
    private double bidAmount;
    private LocalDateTime timestamp;

    public BidTransaction(Bidder bidder,double bidAmount){
        this.bidder = bidder;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now();
    }

    public Bidder getBidder(){
        return this.bidder;
    }

    public double getBidAmount(){
        return bidAmount;
    }

    public LocalDateTime getTimestamp(){
        return timestamp;
    }
}
