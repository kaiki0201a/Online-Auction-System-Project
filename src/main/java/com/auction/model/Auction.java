package com.auction.model;

import com.auction.exception.AuctionException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.AuctionFinishedException;
import com.auction.exception.InsufficientBalanceException;
import com.auction.utils.AuctionObserver;

import java.util.concurrent.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class Auction extends Entity implements Serializable {
    private static final long serialVersionUID = 1L;

    // --- CÁC HẰNG SỐ CHO ANTI-SNIPING ---
    private static final int SNIPE_THRESHOLD_SECONDS = 30;
    private static final int EXTENSION_SECONDS = 60;

    private List<AutoBidRule> autoBidRules;
    private Item item;
    private Seller seller;
    private double currentHighestBid;
    private Bidder highestBidder;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;

    private List<BidTransaction> bidHistory;
    private List<AuctionObserver> observers;
    private transient ExecutorService notificationPool = Executors.newCachedThreadPool();
    private List<AutoBid> autoBids = new ArrayList<>(); // ← THÊM MỚI

    // Scheduler để tự động kết thúc phiên khi hết thời gian
    private transient ScheduledFuture<?> autoCloseTask;

    // CONSTRUCTOR
    public Auction(Item item, Seller seller, LocalDateTime startTime, LocalDateTime endTime) {
        super(); // Gọi Entity để sinh ID
        this.item = item;
        this.seller = seller;
        this.startTime = startTime;
        this.endTime = endTime;

        this.currentHighestBid = item.getStartingPrice();
        // Seller đăng sản phẩm → chờ Admin duyệt trước khi đưa vào đấu giá
        this.status = AuctionStatus.PENDING_APPROVAL;
        this.bidHistory = new CopyOnWriteArrayList<>();
        this.autoBidRules = new CopyOnWriteArrayList<>();
        this.observers = new CopyOnWriteArrayList<>();
    }

    public synchronized void processBid(BidTransaction transaction) throws AuctionException {
        // 1. Tách logic kiểm tra ra một hàm riêng (SRP)
        validateBid(transaction);

        // Kích hoạt anti-sniping để xem có cần gia hạn thời gian không
        applyAntiSniping();

        // 2. Cập nhật dữ liệu
        this.currentHighestBid = transaction.getBidAmount();
        this.highestBidder = transaction.getBidder();
        this.bidHistory.add(transaction);

        // 3. Thông báo (DIP - phụ thuộc vào Interface Observer)
        notifyObservers(transaction);

        // Trigger gọi auto bid xem robot có ai muốn đặt giá không
        triggerAutoBids();
    }

    // Hàm hỗ trợ để làm sạch code (Clean Code)
    private void validateBid(BidTransaction transaction) throws AuctionException {
        // 1. Kiểm tra thời gian
        if (LocalDateTime.now().isAfter(this.endTime)) {
            this.status = AuctionStatus.FINISHED;
            throw new AuctionFinishedException("Phiên đấu giá đã kết thúc vào lúc " + this.endTime);
        }

        // 2. Cho phép cả OPEN, RUNNING và APPROVED đặt giá
        if (this.status != AuctionStatus.RUNNING && this.status != AuctionStatus.OPEN
                && this.status != AuctionStatus.APPROVED) {
            throw new AuctionClosedException("Phiên đấu giá không ở trạng thái hoạt động. Trạng thái: " + this.status);
        }

        // 3. Chống đặt giá âm hoặc bằng 0
        if (transaction.getBidAmount() <= 0) {
            throw new InvalidBidException("Giá thầu không hợp lệ. Vui lòng nhập số tiền lớn hơn 0.");
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

        // 4. Chống gian lận: Người bán tự buff giá
        if (transaction.getBidder().getId().equals(this.seller.getId())) {
            throw new InvalidBidException("Người bán không được phép tự đấu giá sản phẩm của mình!");
        }
    }

    // Hàm Anti-Sniping
    private synchronized void applyAntiSniping() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thresholdTime = this.endTime.minusSeconds(SNIPE_THRESHOLD_SECONDS);

        if (now.isAfter(thresholdTime) && now.isBefore(this.endTime)) {
            this.endTime = this.endTime.plusSeconds(EXTENSION_SECONDS);
            notifyObservers("🛡️ Có người đặt giá phút chót! Phiên đấu giá gia hạn thêm " + EXTENSION_SECONDS
                    + " giây. Kết thúc lúc: " + this.endTime);
        }
    }

    private void triggerAutoBids() {
        java.util.List<AutoBidRule> sortedRules = new java.util.ArrayList<>();
        for (AutoBidRule rule : this.autoBidRules) {
            if (rule.isActive()) {
                sortedRules.add(rule);
            }
        }

        sortedRules.sort((r1, r2) -> {
            int priceCompare = Double.compare(r2.getMaxBid(), r1.getMaxBid());
            if (priceCompare == 0) {
                return r1.getRegisterTime().compareTo(r2.getRegisterTime());
            }
            return priceCompare;
        });

        boolean hasNewAction;
        do {
            hasNewAction = false;

            for (AutoBidRule topRule : sortedRules) {
                if (!topRule.isActive() || (this.highestBidder != null
                        && topRule.getBidder().getId().equals(this.highestBidder.getId()))) {
                    continue;
                }

                double targetPrice = this.currentHighestBid + topRule.getIncrement();

                if (targetPrice <= topRule.getMaxBid()) {
                    try {
                        BidTransaction autoTx = new BidTransaction(this, topRule.getBidder(), targetPrice);
                        validateBid(autoTx);

                        this.currentHighestBid = targetPrice;
                        this.highestBidder = topRule.getBidder();
                        this.bidHistory.add(autoTx);
                        topRule.getBidder().addTransaction(autoTx);

                        System.out.println("🤖 [AUTO-BID] Tự động trả giá $" + targetPrice + " thay cho "
                                + topRule.getBidder().getUserName());
                        notifyObservers(autoTx);

                        hasNewAction = true;
                        break;

                    } catch (AuctionException e) {
                        System.out.println("⚠️ [AUTO-BID TẮT] Hủy lệnh của " + topRule.getBidder().getUserName()
                                + " vì: " + e.getMessage());
                        topRule.setActive(false);
                    }
                } else {
                    System.out.println(
                            "🏳️ [AUTO-BID TẮT] " + topRule.getBidder().getUserName() + " đã chạm trần Max Bid.");
                    topRule.setActive(false);
                }
            }
        } while (hasNewAction);
    }

    // QUẢN LÝ THÔNG TIN & PHÂN QUYỀN
    public synchronized boolean updateAuctionDetails(User requestor, Item newItem, LocalDateTime newStart,
            LocalDateTime newEnd) {
        if (this.status != AuctionStatus.OPEN) {
            return false;
        }

        boolean isOwner = requestor.getId().equals(this.seller.getId());
        boolean isAdmin = requestor instanceof Admin;

        if (isOwner || isAdmin) {
            this.item = newItem;
            this.startTime = newStart;
            this.endTime = newEnd;
            return true;
        } else {
            return false;
        }
    }

    public synchronized boolean cancelAuction(User requestor, String reason) {
        if (this.status == AuctionStatus.FINISHED) {
            return false;
        }

        boolean isOwner = requestor.getId().equals(this.seller.getId());
        boolean isAdmin = requestor instanceof Admin;

        if (isOwner || isAdmin) {
            this.status = AuctionStatus.CANCELED;
            return true;
        }
        return false;
    }

    // QUẢN LÝ TRẠNG THÁI
    public synchronized void startAuction() {
        if (this.status == AuctionStatus.OPEN) {
            this.status = AuctionStatus.RUNNING;
            scheduleAutoClose();
        }
    }

    private void scheduleAutoClose() {
        try {
            long secondsUntilEnd = ChronoUnit.SECONDS.between(LocalDateTime.now(), this.endTime);

            if (secondsUntilEnd <= 0) {
                secondsUntilEnd = 1;
            }

            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            autoCloseTask = scheduler.schedule(() -> {
                System.out.println("⏰ [AUTO-CLOSE] Phiên đấu giá \"" + this.item.getNameItem()
                        + "\" hết thời gian, tự động kết thúc!");
                closeAuction();
            }, secondsUntilEnd, TimeUnit.SECONDS);

        } catch (Exception e) {
            System.err.println("❌ Lỗi khi lên lịch auto-close: " + e.getMessage());
        }
    }

    public synchronized void closeAuction() {
        if (this.status == AuctionStatus.RUNNING || this.status == AuctionStatus.OPEN) {
            this.status = AuctionStatus.FINISHED;
            determineWinner();
        }
    }

    // KQ
    public void determineWinner() {
        if (this.highestBidder != null) {
            System.out.println("Người chiến thắng: " + this.highestBidder.getUserName() + " với mức giá: "
                    + this.currentHighestBid);
        } else {
            System.out.println("Không có ai tham gia trả giá cho phiên đấu giá này.");
        }
    }

    // Settlement: xử lý thanh toán sau khi phiên kết thúc
    // FIX BUG #1: Bidder đã bị trừ tiền trong placeBid() khi đặt giá.
    // settleAuction() CHỈ cộng tiền cho Seller, KHÔNG trừ lại bidder tránh trừ 2 lần.
    public synchronized void settleAuction() {
        if (this.status != AuctionStatus.FINISHED) {
            System.out.println("⚠️ Lỗi: Phiên đấu giá phải ở trạng thái FINISHED mới có thể settlement!");
            return;
        }

        if (this.highestBidder == null) {
            System.out.println("ℹ️ Lỗi: Phiên đấu giá không có người thắng cuộc!");
            return;
        }

        try {
            if (this.status == AuctionStatus.PAID) {
                System.out.println("ℹ️ Phiên đấu giá " + this.id + " đã được settlement rồi!");
                return;
            }

            double bidAmount = this.currentHighestBid;

            // FIX: KHÔNG trừ tiền bidder — tiền đã bị trừ khi họ gọi placeBid().
            // Chỉ cộng tiền cho Seller.
            double newBalance = this.seller.getBalance() + bidAmount;
            this.seller.setBalance(newBalance);

            // Ghi lịch sử nhận tiền cho Seller
            AuctionEarning earning = new AuctionEarning(
                    this.getAuctionId(),
                    this.item.getNameItem(),
                    this.highestBidder.getUserName(),
                    bidAmount,
                    newBalance,
                    "PAID"
            );
            this.seller.addEarning(earning);

            System.out.println("💰 [Settlement] Cộng $" + bidAmount + " vào tài khoản Seller "
                    + this.seller.getUserName() + " (Bidder " + this.highestBidder.getUserName()
                    + " đã bị trừ tiền khi đặt giá)");

            this.status = AuctionStatus.PAID;
            System.out.println("✅ [Settlement] Hoàn tất! Trạng thái: " + this.status);
        } catch (Exception e) {
            System.err.println("❌ [Settlement Error] " + e.getMessage());
        }
    }

    /**
     * Hoàn tiền cho highestBidder khi phiên bị hủy giữa chừng.
     * Trả lại đúng số tiền đã bị trừ khi họ đặt giá thắng.
     * Không làm gì nếu không có ai đặt giá (highestBidder = null).
     */
    public synchronized void refundOnCancel() {
        if (this.highestBidder == null) {
            System.out.println("ℹ️ [CANCEL REFUND] Không có bidder nào để hoàn tiền.");
            return;
        }

        double refundAmount = this.currentHighestBid;
        double newBalance   = this.highestBidder.getBalance() + refundAmount;
        this.highestBidder.setBalance(newBalance);

        System.out.println("💸 [CANCEL REFUND] Hoàn " + refundAmount
                + " cho bidder " + this.highestBidder.getUserName()
                + " (Số dư mới: " + newBalance + ")");
    }


    // ĐÃ FIX: THÊM CÁC RÀO CHẮN BẢO MẬT (EDGE CASES)
    public synchronized void registerAutoBid(Bidder bidder, double maxBid, double increment) throws AuctionException {
        if (this.status == AuctionStatus.FINISHED) {
            throw new AuctionClosedException("Lỗi: Không thể cài Auto-bid vì phiên đấu giá đã kết thúc!");
        }

        if (maxBid <= this.currentHighestBid) {
            throw new InvalidBidException("Giá tối đa (Max Bid) phải lớn hơn giá hiện tại!");
        }

        if (increment <= 0) {
            throw new InvalidBidException("Bước giá (Increment) phải là một số lớn hơn 0!");
        }

        if (bidder.getBalance() < maxBid) {
            throw new InsufficientBalanceException("Số dư không đủ để thiết lập Auto-bid tới mức giá này.",
                    bidder.getBalance(), maxBid);
        }

        AutoBidRule newRule = new AutoBidRule(bidder, maxBid, increment);
        this.autoBidRules.add(newRule);
        System.out.println(
                "✅ " + bidder.getUserName() + " đã cài Auto-bid (Max: " + maxBid + ", Bước giá: " + increment + ")");
    }

    // OBSERVER PATTERN
    public synchronized void addObserver(AuctionObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public synchronized void removeObserver(AuctionObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers(String message) {
        for (AuctionObserver obs : observers) {
            CompletableFuture.runAsync(() -> {
                obs.update(message);
            }, notificationPool);
        }
    }

    private void notifyObservers(BidTransaction tx) {
        for (AuctionObserver obs : observers) {
            CompletableFuture.runAsync(() -> {
                obs.onNewBidPlaced(tx);
            }, notificationPool);
        }
    }

    // Fix bug transient sau khi deserialize
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.notificationPool = Executors.newCachedThreadPool();
        this.autoCloseTask = null;
    }

    // GETTERS
    public String getAuctionId() {
        return this.getId();
    }

    public Item getItem() {
        return item;
    }

    public Seller getSeller() {
        return seller;
    }

    public double getCurrentHighestBid() {
        return currentHighestBid;
    }

    public Bidder getHighestBidder() {
        return highestBidder;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public List<BidTransaction> getBidHistory() {
        return bidHistory;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    // FIX BUG #2: Thêm setter endTime để ServerApp có thể đọc thời gian mới
    // sau khi anti-sniping trong autobid thay đổi endTime
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public List<AutoBidRule> getAutoBidRules() {
        return autoBidRules;
    }

    // Setters for ban logic: allow server to roll back highest bidder
    public void setHighestBidder(Bidder bidder) {
        this.highestBidder = bidder;
    }

    public void setCurrentHighestBid(double amount) {
        this.currentHighestBid = amount;
    }
}