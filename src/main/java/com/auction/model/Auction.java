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


    // XỬ LÝ ĐẶT GIÁ

    public boolean processBid(BidTransaction transaction) {
        // Trích xuất thông tin từ tờ biên lai để kiểm tra
        Bidder bidder = transaction.getBidder();
        double bidAmount = transaction.getBidAmount();

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

        // 3. Chống gian lận: Người bán không được tự đặt giá
        if (bidder.getId().equals(this.seller.getId())) {
            System.out.println("Lỗi gian lận: Người bán không được phép tự đặt giá!");
            return false;
        }

        // 4. Kiểm tra giá đặt
        if (bidAmount <= this.currentHighestBid) {
            System.out.println("Lỗi: Giá đặt phải lớn hơn " + this.currentHighestBid);
            return false;
        }

        // 5. Kiểm tra số dư tài khoản
        if (bidAmount > bidder.getBalance()) {
            System.out.println("Lỗi: Số dư không đủ!");
            return false;
        }

        // Cập nhật người dẫn đầu
        this.currentHighestBid = bidAmount;
        this.highestBidder = bidder;

        // Lưu lại lịch sử
        this.bidHistory.add(transaction);

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

    //KQ

    public void determineWinner() {
        if (this.highestBidder != null) {
            System.out.println("Người chiến thắng: " + this.highestBidder.getUserName() + " với mức giá: " + this.currentHighestBid);
        } else {
            System.out.println("Không có ai tham gia trả giá cho phiên đấu giá này.");
        }
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