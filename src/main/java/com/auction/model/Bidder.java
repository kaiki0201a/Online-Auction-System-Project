package com.auction.model;
import java.util.ArrayList;
import java.util.List;

import com.auction.model.User;
public class Bidder extends User{
    private double balance;
    private List<BidTransaction> transactionHistory; // Danh sách id các phiên đấu giá đá tham gia
    public Bidder(String userName, String passWord, String email,double balance){
        super(userName, passWord, email);
        this.balance = balance;
        this.transactionHistory = new ArrayList<>();
    }
    public double getBalance(){
        return this.balance;
    }
    public void setBalance(double balance){
        this.balance = balance;
    }
    public List<BidTransaction> getTransactionHistory(){
        return transactionHistory;
    }
    public void addTransaction(BidTransaction transaction) {
        this.transactionHistory.add(transaction);
    }
    public void getBidderInfo(){
        System.out.println("========== BIDDER DASHBOARD ==========");
        System.out.println("Xin chào, " + this.getUserName());
        System.out.println("Email: " + this.getEmail());
        System.out.println("Số dư hiện tại: $" + this.balance);
        System.out.println("======================================");
    }
    public boolean placeBid(Auction auction, double amount)
    {
        if(amount > this.getBalance()){
            System.out.println("Số dư không đủ! Bạn còn : " + this.getBalance());
            return false;
        }
        boolean isSuccess = auction.placeBid(this,amount);
        if(isSuccess){
            System.out.println("Bidder "+this.getUserName()+" đã đấu giá thành công " + amount + "vào phiên " + auction.getAuctionId());
        }
        return isSuccess;
    }
    public void setupAutoBid(Auction auction,double maxBid, double increment){
        if(maxBid > this.getBalance()){
            System.out.println("Lỗi : Với số dư của bạn chỉ có thể cài Auto Bid với mức tối đa là :" +this.balance);
            return;
        }
        System.out.println("Thành công: " + this.getUserName() + " đã cài đặt Auto Bid thành công cho phiên giao dịch: " + auction.getAuctionId());
        System.out.println("Trả giá tự động lên tối đa: "+ maxBid +" với bước nhảy: "+ increment);
    }

}