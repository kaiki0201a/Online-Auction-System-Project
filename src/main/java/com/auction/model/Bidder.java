package com.auction.model;
import com.auction.exception.AuctionException;


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

    private double balance;       // Số dư khả dụng (có thể dùng để đặt giá mới)
    private double heldBalance;   // Số dư đang bị giữ bởi bid hiện tại
    private List<BidTransaction> transactionHistory;
    private List<WalletTransaction> walletHistory; // Lịch sử nạp/rút tiền

    public Bidder(String userName, String passWord, String email, double balance){
        super(userName, passWord, email);
        this.balance = balance;
        this.heldBalance = 0.0;
        this.transactionHistory = new CopyOnWriteArrayList<>();
        this.walletHistory      = new CopyOnWriteArrayList<>();
    }

    // ── Số dư khả dụng (tiền còn được dùng) ──────────────────────────────────
    public double getBalance(){
        return this.balance;
    }
    public void setBalance(double balance){
        this.balance = balance;
    }

    // ── Số dư đang bị giữ bởi bid hiện tại ────────────────────────────────────
    public double getHeldBalance() {
        if (Double.isNaN(heldBalance)) heldBalance = 0.0; // safety guard
        return heldBalance;
    }
    public void setHeldBalance(double heldBalance) {
        this.heldBalance = Math.max(0, heldBalance);
    }

    /** Tổng tài sản = khả dụng + đang bị giữ. Dùng để kiểm tra affordability. */
    public double getTotalBalance() {
        return this.balance + this.heldBalance;
    }

    /**
     * Khóa một khoản tiền từ balance → heldBalance.
     * Gọi khi bidder thắng một lần đặt giá.
     */
    public void holdFunds(double amount) {
        this.balance -= amount;
        this.heldBalance += amount;
    }

    /**
     * Giải phóng toàn bộ heldBalance → balance (hoàn tiền khi bị vượt hoặc hủy).
     */
    public double releaseHeldFunds() {
        double released = this.heldBalance;
        this.balance    += released;
        this.heldBalance = 0.0;
        return released;
    }
    public List<BidTransaction> getTransactionHistory(){
        return transactionHistory;
    }
    public void addTransaction(BidTransaction transaction) {
        this.transactionHistory.add(transaction);
    }
    public List<WalletTransaction> getWalletHistory() {
        if (walletHistory == null) walletHistory = new CopyOnWriteArrayList<>();
        return walletHistory;
    }
    public void addWalletTransaction(WalletTransaction wt) {
        if (walletHistory == null) walletHistory = new CopyOnWriteArrayList<>();
        walletHistory.add(0, wt); // thêm vào đầu để hiển thị mới nhất trước
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
        BidTransaction transaction = new BidTransaction(auction, this, amount);
        auction.processBid(transaction);
        // đã sửa level 1: Trừ balance khi đặt giá thành công
        this.balance -= amount;
        System.out.println("Bidder " + this.getUserName() + " đã đấu giá thành công " + amount + " vào phiên " + auction.getAuctionId());
        this.addTransaction(transaction);
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
        // Observer callback: nhận thông báo dạng văn bản từ Auction.
        // Phía client, NetworkClient sẽ push message này xuống màn hình tương ứng.
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
