package com.auction.model;

import com.auction.exception.InvalidBidException;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.InsufficientBalanceException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Auction extends Entity {
    // ID phiên bản để tránh lỗi khi nâng cấp code sau này
    private static final long serialVersionUID = 1L;

    private List<AutoBidRule> autoBidRules;
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
        this.autoBidRules = new ArrayList<>();
    }


    // XỬ LÝ ĐẶT GIÁ

    public synchronized void processBid(BidTransaction transaction) throws InvalidBidException, AuctionClosedException, InsufficientBalanceException {
        // Trích xuất thông tin từ tờ biên lai để kiểm tra
        Bidder bidder = transaction.getBidder();
        double bidAmount = transaction.getBidAmount();

        // Kiểm tra trạng thái
        if (this.status != AuctionStatus.RUNNING) {
            throw new AuctionClosedException("Lỗi: Phiên đấu giá hiện không diễn ra (Trạng thái: " + this.status + ").");
        }

        // Kiểm tra thời gian
        if (LocalDateTime.now().isAfter(this.endTime)) {
            this.status = AuctionStatus.FINISHED;
            throw new AuctionClosedException("Lỗi: Phiên đấu giá đã kết thúc vào lúc " + this.endTime);
        }

        // 3. Chống gian lận: Người bán không được tự đặt giá
        if (bidder.getId().equals(this.seller.getId())) {
            // Dùng InvalidBidException để báo lỗi này luôn cho tiện
            throw new InvalidBidException("Lỗi gian lận: Người bán không được phép tự đặt giá cho sản phẩm của mình!");
        }

        // 4. Kiểm tra giá đặt
        if (bidAmount <= this.currentHighestBid) {
            throw new InvalidBidException(
                    "Lỗi: Giá đặt ($" + bidAmount + ") phải lớn hơn mức giá cao nhất hiện tại ($" + this.currentHighestBid + ").",
                    this.currentHighestBid,
                    bidAmount
            );
        }
        // 5. Kiểm tra số dư tài khoản
        if (bidAmount > bidder.getBalance()) {
            throw new InsufficientBalanceException(
                    "Lỗi: Số dư không đủ để thực hiện giao dịch này!",
                    bidder.getBalance(),
                    bidAmount
            );
        }

        // Cập nhật người dẫn đầu
        this.currentHighestBid = bidAmount;
        this.highestBidder = bidder;

        // Lưu lại lịch sử
        this.bidHistory.add(transaction);
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
    public synchronized boolean registerAutoBid(Bidder bidder, double maxBid, double increment) {
        if(this.status == AuctionStatus.FINISHED){
            return false;
        }
        AutoBidRule newRule = new AutoBidRule(bidder,maxBid,increment);
        this.autoBidRules.add(newRule);
        return true; 

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

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }
}