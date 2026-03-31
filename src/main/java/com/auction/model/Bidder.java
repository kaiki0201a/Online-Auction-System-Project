package com.auction.model;
import java.util.ArrayList;
import java.util.List;
public class Bidder {
    private double balance; 
    private List<String> jAuction; // Danh sách id các phiên đấu giá đá tham gia
    public Bidder(String userName, String passWord, String email,double balance){
        super(userName, passWord, email);
        this.balance = balance;
        this.jAuction = new Arraylist<>();
    }
    public double getbalance(){
        return this.balance;
    }
    public void setbalance(double balance){
        this.balance = balance;
    }
    public List<String> getJoinedAuctions(){
        return jAuction;
    }
    public void setJoinedAuctions(List<String> jAuction){
        this.jAuction = jAuction;
    }
    public void getBidderInfo(){
        System.out.println("========== BIDDER DASHBOARD ==========");
        System.out.println("Xin chào, " + this.getUserName());
        System.out.println("Email: " + this.getEmail());
        System.out.println("Số dư hiện tại: $" + this.accountBalance);
        System.out.println("======================================");
    }
    public boolean placeBid(Auction auction, double amount)
    {
        if(amount > this.getbalance()){
            System.out.println("Số dư không đủ! Bạn còn : " + this.getbalance())
            return false;
        }
        boolean isSuccess = auction.placeBid(this,amount);
        if(isSucces){
            System.out.println("Bidder "+this.getUserName()+" đã đấu giá thành công " + amount + "vào phiên " + auction.getAuctionId());
        }
        return isSuccess;
    }
    public void setupAutoBid(Auction auction,double maxBid, double increment){
        if(maxBid > this.getbalance()){
            System.out.println("Lỗi : Với số dư của bạn chỉ có thể cài Auto Bid với mức tối đa là :" +this.balance);
            return;
        }
        System.out.println("Thành công: " this.getUserName() + " đã cài đặt Auto Bid thành công cho phiên giao dịch: " + auction.getAuctionId());
        System.out.println("Trả giá tự động lên tối đa: "+ maxBid +" với bước nhảy: "+ increment);
    }
    
}
