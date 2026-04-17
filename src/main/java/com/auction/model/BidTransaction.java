package com.auction.model;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.Serializable;
import java.io.Serializable;

public class BidTransaction implements Serializable{
    // ID phiên bản để tránh lỗi khi nâng cấp code sau này
    private static final long serialVersionUID = 1L;
    
    private Auction auction;
    private Bidder bidder;
    private double bidAmount;
    private LocalDateTime timestamp;

    public BidTransaction(Auction auction,Bidder bidder,double bidAmount){
        this.auction = auction;
        this.bidder = bidder;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now();
    }
    public Auction getAuction(){ 
        return this.auction;
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
    
    @Override
    public String toString(){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        String formattedTime = timestamp.format(formatter);
        return String.format("[%s] %s đã đặt $%.2f vào phiên đấu giá %s",
                formattedTime,
                bidder.getUserName(), 
                bidAmount, 
                auction.getAuctionId());
         
    }
}