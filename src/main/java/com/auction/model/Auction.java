package com.auction.model;

import com.auction.exception.AuctionException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.InsufficientBalanceException;
import com.auction.utils.AuctionObserver;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Auction extends Entity {
    // ID phiên bản để tránh lỗi khi nâng cấp code sau này
    private static final long serialVersionUID = 1L;

    // Chứa tất cả thông tin của một phiên đấu giá
    private List<AutoBidRule> autoBidRules;
    private Item item;
    private Seller seller;
    private double currentHighestBid;
    private Bidder highestBidder;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;

    private List<BidTransaction> bidHistory;
    // THÊM MỚI: Danh sách những người đang xem phiên đấu giá này
    private List<AuctionObserver> observers;

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
        this.observers = new ArrayList<>();
    }


    public synchronized void processBid(BidTransaction transaction)
            throws AuctionException {

        // 1. Tách logic kiểm tra ra một hàm riêng (SRP)
        validateBid(transaction);

        // 2. Cập nhật dữ liệu
        this.currentHighestBid = transaction.getBidAmount();
        this.highestBidder = transaction.getBidder();
        this.bidHistory.add(transaction);

        // 3. Thông báo (DIP - phụ thuộc vào Interface Observer)
        notifyObservers("🔥 Giá mới: $" + currentHighestBid + " bởi " + highestBidder.getUserName());

        // Nếu đặt giá khi phiên còn dưới 1 phút, tự động cộng thêm 2 phút
        if (Duration.between(LocalDateTime.now(), this.endTime).toMinutes() < 1) {
            this.endTime = this.endTime.plusMinutes(2);
            notifyObservers("⏰ Phiên đấu giá đã được tự động gia hạn thêm 2 phút để đảm bảo công bằng!");
        }
    }

    // Hàm hỗ trợ để làm sạch code (Clean Code)
    // Hàm hỗ trợ để làm sạch code (Clean Code)
    private void validateBid(BidTransaction transaction) throws AuctionException {
        // 1. THÊM LẠI ĐOẠN KIỂM TRA THỜI GIAN
        if (LocalDateTime.now().isAfter(this.endTime)) {
            this.status = AuctionStatus.FINISHED; // Tự động đóng phiên
            throw new AuctionClosedException("Phiên đấu giá đã kết thúc vào lúc " + this.endTime);
        }

        // 2. Các kiểm tra khác giữ nguyên
        if (this.status != AuctionStatus.RUNNING) {
            throw new AuctionClosedException("Phiên đấu giá không ở trạng thái RUNNING.");
        }

        if (transaction.getBidAmount() <= this.currentHighestBid) {
            throw new InvalidBidException("Giá thầu phải cao hơn giá hiện tại.",
                    this.currentHighestBid, transaction.getBidAmount());
        }

        if (transaction.getBidder().getBalance() < transaction.getBidAmount()) {
            throw new InsufficientBalanceException("Số dư không đủ.",
                    transaction.getBidder().getBalance(),
                    transaction.getBidAmount());
        }

        // Chống gian lận
        if (transaction.getBidder().getId().equals(this.seller.getId())) {
            throw new InvalidBidException("Người bán không được tự đấu giá!");
        }
    }
    // QUẢN LÝ THÔNG TIN & PHÂN QUYỀN
    public synchronized boolean updateAuctionDetails(User requestor, Item newItem, LocalDateTime newStart, LocalDateTime newEnd) {
        // Chỉ cho phép sửa khi phiên đấu giá chưa bắt đầu (đang OPEN)
        if (this.status != AuctionStatus.OPEN || this.status == AuctionStatus.FINISHED) {
            System.err.println("Lỗi: Không thể sửa thông tin khi phiên đấu giá đã chạy hoặc đã kết thúc");
            return false;
        }

        // Kiểm tra quyền: Người yêu cầu phải là Chủ sản phẩm (Seller) HOẶC là Quản trị viên (Admin)
        boolean isOwner = requestor.getId().equals(this.seller.getId());
        boolean isAdmin = requestor instanceof Admin;

        if (isOwner || isAdmin) {
            this.item = newItem;
            this.startTime = newStart;
            this.endTime = newEnd;
            System.out.println("Cập nhật thông tin phiên đấu giá thành công!");
            return true;
        } else {
            System.out.println("Từ chối truy cập: Bạn không có quyền truy cập!");
            return false;
        }
    }
    public synchronized boolean cancelAuction(User requestor, String reason) {
        // Phiên đã kết thúc thì không thể hủy
        if (this.status == AuctionStatus.FINISHED) {
            return false;
        }

        boolean isOwner = requestor.getId().equals(this.seller.getId());
        boolean isAdmin = requestor instanceof Admin;

        if (isOwner || isAdmin) {
            this.status = AuctionStatus.CANCELED;
            System.out.println("Phiên đấu giá đã bị hủy. Lý do: " + reason);
            return true;
        }
        return false;
    }

    // QUẢN LÝ TRẠNG THÁI

    public void startAuction() {
        if (this.status == AuctionStatus.OPEN) {
            this.status = AuctionStatus.RUNNING;
            System.out.println("Phiên đấu giá cho sản phẩm '" + this.item.getNameItem() + "' ĐÃ BẮT ĐẦU!");
        } else {
            System.out.println("Không thể bắt đầu. Trạng thái hiện tại: " + this.status);
        }
    }

    public void closeAuction() {
        if (this.status == AuctionStatus.RUNNING) {
            this.status = AuctionStatus.FINISHED;
            System.out.println("Phiên đấu giá ĐÃ KẾT THÚC!");
            determineWinner(); // Gọi luôn hàm công bố người thắng cuộc của bạn
        }
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
    // THÊM MỚI 3 HÀM CỦA OBSERVER PATTERN:

    // 1. Cho phép người dùng tham gia xem (Đăng ký nhận thông báo)
    public synchronized void addObserver(AuctionObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
            System.out.println("Một người dùng vừa vào xem phiên đấu giá " + this.item.getNameItem());
        }
    }

    // 2. Cho phép người dùng thoát ra (Hủy nhận thông báo)
    public synchronized void removeObserver(AuctionObserver observer) {
        observers.remove(observer);
    }

    // 3. Hàm cầm loa thông báo cho tất cả mọi người
    private synchronized void notifyObservers(String message) {
        for (AuctionObserver obs : observers) {
            obs.update(message);
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

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }
}