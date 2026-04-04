package com.auction.model;

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


    // CONSTRUCTOR

    public Auction(Item item, Seller seller, LocalDateTime startTime, LocalDateTime endTime) {
        super(); // Gọi Entity để sinh ID
        this.item = item;
        this.seller = seller;
        this.startTime = startTime;
        this.endTime = endTime;

        this.currentHighestBid = item.getStartingPrice();
        this.status = AuctionStatus.OPEN;
        this.bidHistory = new ArrayList<>();
    }


    // ĐẶT GIÁ

    public boolean placeBid(Bidder bidder, double bidAmount) {
        // Kiểm tra trạng thái
        if (this.status != AuctionStatus.RUNNING) {
            System.out.println("Lỗi: Phiên đấu giá hiện không diễn ra.");
            return false;
        }

        // Kiểm tra thời gian
        if (LocalDateTime.now().isAfter(this.endTime)) {
            this.status = AuctionStatus.FINISHED;
            System.out.println("Lỗi: Phiên đấu giá đã kết thúc.");
            return false;
        }

        // Chống gian lận: Người bán không được tự đặt giá
        if (bidder.getId().equals(this.seller.getId())) {
            System.out.println("Lỗi gian lận: Người bán không được phép tự đặt giá!");
            return false;
        }

        // Kiểm tra giá đặt
        if (bidAmount <= this.currentHighestBid) {
            System.out.println("Lỗi: Giá đặt phải lớn hơn " + this.currentHighestBid);
            return false;
        }

        // Kiểm tra số dư tài khoản
        if (bidAmount > bidder.getBalance()) {
            System.out.println("Lỗi: Số dư không đủ!");
            return false;
        }

        // Cập nhật người dẫn đầu
        this.currentHighestBid = bidAmount;
        this.highestBidder = bidder;

        // Lưu lịch sử
        BidTransaction transaction = new BidTransaction(this, bidder, bidAmount);
        this.bidHistory.add(transaction);
        bidder.addTransaction(transaction);

        System.out.println(transaction.toString());
        return true;
    }

    // QUẢN LÝ THÔNG TIN & PHÂN QUYỀN

    public boolean updateAuctionDetails(User requestor, Item newItem, LocalDateTime newStart, LocalDateTime newEnd) {
        // TODO: Cần viết thân hàm xử lý phân quyền (Admin/Owner)
        return false;
    }

    public boolean cancelAuction(User requestor, String reason) {
        // TODO: Cần viết thân hàm xử lý đặc quyền hủy phiên đấu giá
        return false;
    }

    // QUẢN LÝ TRẠNG THÁI

    public void startAuction() {
        // TODO: Cần viết thân hàm đổi trạng thái sang RUNNING
    }

    public void closeAuction() {
        // TODO: Cần viết thân hàm đổi trạng thái sang FINISHED
    }

    // GETTERS

    public String getAuctionId() { return this.getId(); }
    public Item getItem() { return item; }
    public Seller getSeller() { return seller; }
    public double getCurrentHighestBid() { return currentHighestBid; }
    public Bidder getHighestBidder() { return highestBidder; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public AuctionStatus getStatus() { return status; }
    public List<BidTransaction> getBidHistory() { return bidHistory; }
}