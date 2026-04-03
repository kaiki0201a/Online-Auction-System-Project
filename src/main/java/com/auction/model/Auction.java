package com.auction.model;

import com.auction.exception.AuctionClosedException;
import com.auction.exception.InvalidBidException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Auction extends Entity {
    private Item item;
    private Seller seller;
    private double currentHighestBid;
    private Bidder highestBidder;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;

    private List<BidTransaction> bidHistory;

    // Constructor
    public Auction(Item item, Seller seller, LocalDateTime startTime, LocalDateTime endTime) {
        super(); // Khởi tạo id UUID từ Entity
        this.item = item;
        this.seller = seller;
        this.startTime = startTime;
        this.endTime = endTime;

        // Giá cao nhất ban đầu chính là giá khởi điểm của Item
        this.currentHighestBid = item.getStartingPrice();
        this.status = AuctionStatus.OPEN; // Trạng thái mặc định
        this.bidHistory = new ArrayList<>();
    }

    /**
     * Đặt giá mới.
     * Sử dụng 'synchronized' để đảm bảo an toàn khi đấu giá đồng thời (tránh lost update).
     */
    public synchronized void placeBid(Bidder bidder, double bidAmount) throws AuctionClosedException, InvalidBidException {
        // Kiểm tra trạng thái phiên đấu giá

        if (this.status != AuctionStatus.RUNNING) {
            throw new AuctionClosedException("Phiên đấu giá hiện không diễn ra (Trạng thái: " + this.status + ").");
        }

        // Kiểm tra tính hợp lệ của giá đặt
        if (bidAmount <= this.currentHighestBid) {
            throw new InvalidBidException("Giá đặt (" + bidAmount + ") phải lớn hơn giá hiện tại (" + this.currentHighestBid + ").");
        }

        // Cập nhật dữ liệu người dẫn đầu
        this.currentHighestBid = bidAmount;
        this.highestBidder = bidder;

        // Khởi tạo đối tượng BidTransaction
        BidTransaction transaction = new BidTransaction(this, bidder, bidAmount);

        // Thêm vào lịch sử
        this.bidHistory.add(transaction);

        System.out.println(transaction.toString());

    }

    // QUẢN LÝ TRẠNG THÁI

    public void startAuction() {
        if (this.status == AuctionStatus.OPEN) {
            this.status = AuctionStatus.RUNNING;
            System.out.println("Phiên đấu giá [" + this.getId() + "] đã BẮT ĐẦU.");
        }
    }

    public void closeAuction() {
        if (this.status == AuctionStatus.RUNNING) {
            this.status = AuctionStatus.FINISHED;
            System.out.println("Phiên đấu giá [" + this.getId() + "] đã KẾT THÚC.");
            if (highestBidder != null) {
                System.out.println("--> Người chiến thắng: " + highestBidder.getUserName() + " với giá " + currentHighestBid);
            } else {
                System.out.println("--> Không có ai tham gia trả giá.");
            }
        }
    }

    // GETTERS & SETTERS
    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

    public Seller getSeller() { return seller; }
    public void setSeller(Seller seller) { this.seller = seller; }

    public double getCurrentHighestBid() { return currentHighestBid; }
    public void setCurrentHighestBid(double currentHighestBid) { this.currentHighestBid = currentHighestBid; }

    public Bidder getHighestBidder() { return highestBidder; }
    public void setHighestBidder(Bidder highestBidder) { this.highestBidder = highestBidder; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }

    public List<BidTransaction> getBidHistory() { return bidHistory; }
    public void setBidHistory(List<BidTransaction> bidHistory) { this.bidHistory = bidHistory; }
}