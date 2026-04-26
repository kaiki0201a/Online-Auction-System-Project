package com.auction.network;
import java.io.Serializable;

public class UpdateMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private String highestBidder;
    private double highestBidAmount;
    private String systemMessage;
    public UpdateMessage(String highestBidder, double highestBidAmount, String systemMessage) {
        this.highestBidder = highestBidder;
        this.highestBidAmount = highestBidAmount;
        this.systemMessage = systemMessage;
    }
    public String getHighestBidder() {
        return highestBidder;
    }

    public double getHighestBidAmount() {
        return highestBidAmount;
    }

    public String getSystemMessage() {
        return systemMessage;
    }

}
