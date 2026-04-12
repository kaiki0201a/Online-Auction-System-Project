package com.auction.exception;

public class InvalidBidException extends AuctionException {
    private double currentBid;
    private double attemptedBid;

    public InvalidBidException(String message) {
        super(message);
    }

    public InvalidBidException(String message, double currentBid, double attemptedBid) {
        super(message);
        this.currentBid = currentBid;
        this.attemptedBid = attemptedBid;
    }

    public double getCurrentBid() { return currentBid; }
    public double getAttemptedBid() { return attemptedBid; }
}
