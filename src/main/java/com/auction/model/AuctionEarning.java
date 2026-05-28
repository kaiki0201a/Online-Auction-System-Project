package com.auction.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AuctionEarning — lưu lịch sử một lần Seller nhận tiền từ phiên đấu giá.
 * Khác với WalletTransaction (chỉ ghi nạp/rút), AuctionEarning gắn với 1 phiên cụ thể.
 */
public class AuctionEarning implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String auctionId;
    private final String itemName;
    private final String winnerId;       // Username của người thắng
    private final double amount;
    private final double balanceAfter;
    private final LocalDateTime timestamp;
    private final String status;         // "PAID" hoặc "REFUNDED" (nếu hủy sau khi đã settle)

    public AuctionEarning(String auctionId, String itemName, String winnerId,
                          double amount, double balanceAfter, String status) {
        this.auctionId   = auctionId;
        this.itemName    = itemName;
        this.winnerId    = winnerId;
        this.amount      = amount;
        this.balanceAfter = balanceAfter;
        this.status      = status;
        this.timestamp   = LocalDateTime.now();
    }

    public String getAuctionId()   { return auctionId; }
    public String getItemName()    { return itemName; }
    public String getWinnerId()    { return winnerId; }
    public double getAmount()      { return amount; }
    public double getBalanceAfter(){ return balanceAfter; }
    public String getStatus()      { return status; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public String getFormattedTime() {
        return timestamp.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    @Override
    public String toString() {
        return String.format("[%s] Nhận từ \"%s\" — %,.0f VNĐ (%s)",
                getFormattedTime(), itemName, amount, status);
    }
}
