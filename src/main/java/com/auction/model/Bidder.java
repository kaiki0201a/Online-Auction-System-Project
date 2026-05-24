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
    private static final long serialVersionUID = 2L;

    private double balance;
    private double frozenBalance; // Tiền đang bị khóa vì bid chưa thắng
    private List<BidTransaction> transactionHistory;

    public Bidder(String userName, String passWord, String email, double balance) {
        super(userName, passWord, email);
        this.balance = balance;
        this.frozenBalance = 0.0;
        this.transactionHistory = new CopyOnWriteArrayList<>();
    }

    public double getBalance() { return this.balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public double getFrozenBalance() { return this.frozenBalance; }

    /** Số dư có thể rút/dùng = tổng - frozen */
    public double getAvailableBalance() { return this.balance - this.frozenBalance; }

    /**
     * Khóa tiền khi đặt giá — không rút được tiền đang bị freeze.
     * @return false nếu không đủ số dư khả dụng
     */
    public synchronized boolean freeze(double amount) {
        if (getAvailableBalance() < amount) return false;
        this.frozenBalance += amount;
        return true;
    }

    /** Hoàn trả khi bị vượt giá hoặc không thắng */
    public synchronized void unfreeze(double amount) {
        this.frozenBalance = Math.max(0, this.frozenBalance - amount);
    }

    /** Thanh toán khi thắng: gọi từ Settlement — trừ cả balance lấn frozen */
    public synchronized void consumeFrozen(double amount) {
        this.frozenBalance = Math.max(0, this.frozenBalance - amount);
        this.balance = Math.max(0, this.balance - amount);
    }

    public List<BidTransaction> getTransactionHistory() { return transactionHistory; }
    public void addTransaction(BidTransaction transaction) { this.transactionHistory.add(transaction); }

    public void getBidderInfo(){
        System.out.println("========== BIDDER DASHBOARD ==========");
        System.out.println("Xin chào, " + this.getUserName());
        System.out.println("Email: " + this.getEmail());
        System.out.println("Số dư hiện tại: $" + this.balance);
        System.out.println("======================================");
    }

    /**
     * Đặt giá vào một phiên đấu giá.
     * FIX: Dùng freeze thay vì trừ balance ngay — để hỗ trợ refund khi bị vượt giá.
     */
    public void placeBid(Auction auction, double amount) throws AuctionException {
        if (amount > getAvailableBalance()) {
            throw new InsufficientBalanceException(
                    "Số dư khả dụng không đủ! Khả dụng: " + getAvailableBalance()
                    + " (Tổng: " + balance + ", Đang khóa: " + frozenBalance + ")",
                    getAvailableBalance(), amount);
        }
        BidTransaction newTransaction = new BidTransaction(auction, this, amount);
        // processBid sẽ gọi auction.refundPreviousBidder() trước khi set highestBidder mới
        auction.processBid(newTransaction);
        // Khóa tiền thay vì trừ ngay — chỉ trừ khi thắng auction
        this.freeze(amount);
        System.out.println("Bidder " + this.getUserName() + " freeze " + amount + " vào phiên " + auction.getAuctionId());
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
        // đã sửa level 2: Implement logic thông báo khi có bid mới
        if (transaction.getBidder().getId().equals(this.getId())) {
            System.out.println("✅ [Thông báo tới " + this.getUserName() + "] Đặt giá thành công: $" + transaction.getBidAmount());
        } else if (transaction.getAuction().getHighestBidder() != null &&
                   transaction.getAuction().getHighestBidder().getId().equals(this.getId())) {
            System.out.println("📈 [Thông báo tới " + this.getUserName() + "] Bạn dẫn đầu! Mức giá: $" + transaction.getBidAmount());
        } else {
            System.out.println("📉 [Thông báo tới " + this.getUserName() + "] Bị vượt giá! Giá hiện tại: $" + transaction.getBidAmount());
        }
    }
    
}
