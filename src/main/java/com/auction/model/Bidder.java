package com.auction.model;
import com.auction.exception.AuctionException;
import com.auction.model.Auction;
import com.auction.model.BidTransaction;
import com.auction.model.User;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.auction.exception.InvalidBidException;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.InsufficientBalanceException;
import com.auction.utils.AuctionObserver;
import java.util.concurrent.CopyOnWriteArrayList;

public class Bidder extends User implements AuctionObserver {
    // ID phiên bản để tránh lỗi khi nâng cấp code sau này
    private static final long serialVersionUID = 1L;

    private double balance; 
    private List<BidTransaction> transactionHistory; // Danh sách id các phiên đấu giá đá tham gia

    public Bidder(String userName, String passWord, String email,double balance){
        super(userName, passWord, email);
        this.balance = balance;
        this.transactionHistory = new CopyOnWriteArrayList<>();
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

    public void placeBid(Auction auction, double amount) throws AuctionException {
        if(amount > this.getBalance()){
            throw new InsufficientBalanceException(
                    "Số dư không đủ! Bạn còn: $" + this.getBalance(),
                    this.getBalance(),
                    amount
            );
        }
        BidTransaction newTransaction = new BidTransaction(auction,this,amount);
        auction.processBid(newTransaction);
        System.out.println("Bidder " + this.getUserName() + " đã đấu giá thành công " + amount + " vào phiên " + auction.getAuctionId());
        this.addTransaction(newTransaction);
    }
    public void setupAutoBid(Auction auction, double maxBid, double increment)
            throws AuctionException {

        if (maxBid > this.getBalance()){
            throw new InsufficientBalanceException(
                    "Lỗi cài đặt Auto-Bid: Số dư của bạn chỉ có thể cài mức tối đa là $" + this.balance,
                    this.balance,
                    maxBid
            );
        }
        auction.registerAutoBid(this, maxBid, increment);
        System.out.println("Thành công: " + this.getUserName() + " đã cài đặt Auto Bid cho phiên: " + auction.getAuctionId());
        System.out.println("Trả giá tự động lên tối đa: $" + maxBid + " với bước nhảy: $" + increment);
    }

    @Override
    public void update(String message) {
        // Tạm thời in ra màn hình.
        // Sau này ở Tuần 9 (Client-Server), hàm này sẽ dùng Socket đẩy text về màn hình của người dùng.
        System.out.println("[Thông báo tới " + this.getUserName() + "]: " + message);
    }
    @Override
    public void onNewBidPlaced(BidTransaction transaction){
        //Hello hàm này thêm vào để tránh lỗi khi up lên github
    }
    
}
