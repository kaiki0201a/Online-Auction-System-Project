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
        validateBid(transaction);
        applyAntiSniping();

        // FIX: Unfreeze tiền của bidder cũ khi bị vượt giá
        if (this.highestBidder != null
                && !this.highestBidder.getId().equals(transaction.getBidder().getId())) {
            this.highestBidder.unfreeze(this.currentHighestBid);
            System.out.println("↩️ [REFUND] Unfreeze " + this.currentHighestBid
                + " cho " + this.highestBidder.getUserName());
        }

        this.currentHighestBid = transaction.getBidAmount();
        this.highestBidder = transaction.getBidder();
        this.bidHistory.add(transaction);

        notifyObservers(transaction);
        triggerAutoBids();
    }

    private void validateBid(BidTransaction transaction) throws AuctionException {
        if (LocalDateTime.now().isAfter(this.endTime)) {
            this.status = AuctionStatus.FINISHED;
            throw new AuctionFinishedException("Phiên đấu giá đã kết thúc vào lúc " + this.endTime);
        }

        if (this.status != AuctionStatus.RUNNING && this.status != AuctionStatus.OPEN
                && this.status != AuctionStatus.APPROVED) {
            throw new AuctionClosedException("Phiên đấu giá không ở trạng thái hoạt động. Trạng thái: " + this.status);
        }

        if (transaction.getBidAmount() <= 0) {
            throw new InvalidBidException("Giá thầu không hợp lệ. Vui lòng nhập số tiền lớn hơn 0.");
        }

        if (transaction.getBidAmount() <= this.currentHighestBid) {
            throw new InvalidBidException("Giá thầu phải cao hơn giá hiện tại.",
                    this.currentHighestBid, transaction.getBidAmount());
        }

        // FIX: Kiểm tra số dư KHẢ DỤNG (không tính frozen) thay vì total balance
        Bidder bidder = transaction.getBidder();
        double available = bidder.getAvailableBalance();
        if (available < transaction.getBidAmount()) {
            throw new InsufficientBalanceException("Số dư khả dụng không đủ. Khả dụng: " + available,
                    available, transaction.getBidAmount());
        }

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
            notifyObservers("🛡️ Có người đặt giá phút chót! Phiên đấu giá gia hạn thêm " + EXTENSION_SECONDS + " giây. Kết thúc lúc: " + this.endTime);
        }
    }

    private void triggerAutoBids() {
        List<AutoBidRule> sortedRules = new ArrayList<>();
        for (AutoBidRule rule : this.autoBidRules) {
            if (rule.isActive()) sortedRules.add(rule);
        }
        sortedRules.sort((r1, r2) -> {
            int pc = Double.compare(r2.getMaxBid(), r1.getMaxBid());
            return pc != 0 ? pc : r1.getRegisterTime().compareTo(r2.getRegisterTime());
        });

        boolean hasNewAction;
        do {
            hasNewAction = false;
            for (AutoBidRule rule : sortedRules) {
                if (!rule.isActive()) continue;
                if (this.highestBidder != null
                        && rule.getBidder().getId().equals(this.highestBidder.getId())) continue;

                double targetPrice = this.currentHighestBid + rule.getIncrement();
                if (targetPrice > rule.getMaxBid()) {
                    System.out.println("🏳️ [AUTO-BID TẮt] " + rule.getBidder().getUserName() + " đã chạm trần.");
                    rule.setActive(false);
                    continue;
                }
                // Kiểm tra available balance của auto-bidder
                if (rule.getBidder().getAvailableBalance() < targetPrice) {
                    System.out.println("⚠️ [AUTO-BID TẮt] " + rule.getBidder().getUserName() + " không đủ số dư.");
                    rule.setActive(false);
                    continue;
                }
                try {
                    BidTransaction autoTx = new BidTransaction(this, rule.getBidder(), targetPrice);
                    validateBid(autoTx);

                    // Unfreeze bidder cũ nếu bị vượt
                    if (this.highestBidder != null
                            && !this.highestBidder.getId().equals(rule.getBidder().getId())) {
                        this.highestBidder.unfreeze(this.currentHighestBid);
                    }

                    this.currentHighestBid = targetPrice;
                    this.highestBidder = rule.getBidder();
                    this.bidHistory.add(autoTx);
                    // Freeze cho auto-bidder
                    rule.getBidder().freeze(targetPrice);
                    rule.getBidder().addTransaction(autoTx);

                    System.out.println("🤖 [AUTO-BID] " + rule.getBidder().getUserName() + " tự động trả " + targetPrice);
                    notifyObservers(autoTx);
                    hasNewAction = true;
                    break;
                } catch (AuctionException e) {
                    System.out.println("⚠️ [AUTO-BID TẮt] " + rule.getBidder().getUserName() + ": " + e.getMessage());
                    rule.setActive(false);
                }
            }
        } while (hasNewAction);
    }

    // QUẢN LÝ THÔNG TIN & PHÂN QUYỀN
    public synchronized boolean updateAuctionDetails(User requestor, Item newItem, LocalDateTime newStart, LocalDateTime newEnd) {
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
                System.out.println("⏰ [AUTO-CLOSE] Phiên đấu giá \"" + this.item.getNameItem() + "\" hết thời gian, tự động kết thúc!");
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
            System.out.println("Người chiến thắng: " + this.highestBidder.getUserName() + " với mức giá: " + this.currentHighestBid);
        } else {
            System.out.println("Không có ai tham gia trả giá cho phiên đấu giá này.");
        }
    }

    // Settlement: xử lý thanh toán sau khi phiên kết thúc
    public synchronized void settleAuction() {
        if (this.status != AuctionStatus.FINISHED) return;
        if (this.highestBidder == null) {
            System.out.println("ℹ️ Phiên không có người thắng.");
            return;
        }
        if (this.status == AuctionStatus.PAID) {
            System.out.println("ℹ️ Đã settlement rồi.");
            return;
        }

        double winAmount = this.currentHighestBid;

        // FIX: Người thắng: dùng consumeFrozen (trừ cả balance + frozen)
        this.highestBidder.consumeFrozen(winAmount);

        // FIX: Refund tất cả bidder thua — unfreeze số tiền bid cuối cùng của họ
        // Dựa vào bidHistory: lấy bid lớn nhất của mỗi bidder khác winner
        java.util.Map<String, Double> loserLastBid = new java.util.HashMap<>();
        for (BidTransaction tx : this.bidHistory) {
            String uid = tx.getBidder().getId();
            if (!uid.equals(this.highestBidder.getId())) {
                loserLastBid.merge(uid, tx.getBidAmount(), Math::max);
            }
        }
        for (BidTransaction tx : this.bidHistory) {
            String uid = tx.getBidder().getId();
            Double lastBid = loserLastBid.get(uid);
            if (lastBid != null && lastBid.equals(tx.getBidAmount())) {
                tx.getBidder().unfreeze(lastBid);
                System.out.println("↩️ [Refund] " + tx.getBidder().getUserName() + " +" + lastBid);
                loserLastBid.remove(uid); // Chỉ refund một lần
            }
        }

        // Trả tiền cho Seller
        this.seller.setBalance(this.seller.getBalance() + winAmount);

        System.out.println("💰 [Settlement] " + winAmount + " từ " +
                this.highestBidder.getUserName() + " → " + this.seller.getUserName());

        this.status = AuctionStatus.PAID;
        System.out.println("✅ [Settlement] Xong! Trạng thái: PAID");
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
            throw new InsufficientBalanceException("Số dư không đủ để thiết lập Auto-bid tới mức giá này.", bidder.getBalance(), maxBid);
        }

        AutoBidRule newRule = new AutoBidRule(bidder, maxBid, increment);
        this.autoBidRules.add(newRule);
        System.out.println("✅ " + bidder.getUserName() + " đã cài Auto-bid (Max: " + maxBid + ", Bước giá: " + increment + ")");
    }

    /** Hủy autobid của một bidder cụ thể */
    public synchronized void cancelAutoBid(Bidder bidder) {
        for (AutoBidRule rule : this.autoBidRules) {
            if (rule.getBidder().getId().equals(bidder.getId())) {
                rule.setActive(false);
            }
        }
        System.out.println("❌ AutoBid huỷ cho: " + bidder.getUserName());
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