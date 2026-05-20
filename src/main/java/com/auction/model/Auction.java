package com.auction.model;

import com.auction.exception.AuctionException;
import com.auction.exception.InvalidBidException;
import com.auction.exception.AuctionClosedException;
import com.auction.exception.InsufficientBalanceException;
import com.auction.utils.AuctionObserver;

import java.util.concurrent.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Auction extends Entity implements Serializable {
    // ID phiên bản để tránh lỗi khi nâng cấp code sau này
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
    // THÊM MỚI: Danh sách những người đang xem phiên đấu giá này
    private List<AuctionObserver> observers;
    private transient ExecutorService notificationPool = Executors.newCachedThreadPool();

    // CONSTRUCTOR
    public Auction(Item item, Seller seller, LocalDateTime startTime, LocalDateTime endTime) {
        super(); // Gọi Entity để sinh ID
        this.item = item;
        this.seller = seller;
        this.startTime = startTime;
        this.endTime = endTime;

        this.currentHighestBid = item.getStartingPrice();
        this.status = AuctionStatus.OPEN;
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

    // Hàm hỗ trợ để làm sạch code (Clean Code) - ĐÃ BỔ SUNG EDGE CASES
    private void validateBid(BidTransaction transaction) throws AuctionException {
        // 1. THÊM LẠI ĐOẠN KIỂM TRA THỜI GIAN
        if (LocalDateTime.now().isAfter(this.endTime)) {
            this.status = AuctionStatus.FINISHED; // Tự động đóng phiên
            throw new AuctionClosedException("Phiên đấu giá đã kết thúc vào lúc " + this.endTime);
        }

        // 2. Các kiểm tra trạng thái
        if (this.status != AuctionStatus.RUNNING) {
            throw new AuctionClosedException("Phiên đấu giá không ở trạng thái RUNNING.");
        }

        // 3. EDGE CASE 1: Chống đặt giá âm hoặc bằng 0
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

        // Nếu đặt giá vào những phút cuối cùng
        if (now.isAfter(thresholdTime) && now.isBefore(this.endTime)) {
            this.endTime = this.endTime.plusSeconds(EXTENSION_SECONDS);
            //System.out.println("🛡️ [Anti-Sniping] Phiên đấu giá được gia hạn thêm " + EXTENSION_SECONDS + " giây!");
            notifyObservers("🛡️ Có người đặt giá phút chót! Phiên đấu giá gia hạn thêm " + EXTENSION_SECONDS + " giây. Kết thúc lúc: " + this.endTime);
        }
    }

    private void triggerAutoBids() {
        // 1. TỐI ƯU HÓA: Sắp xếp bảng xếp hạng MỘT LẦN DUY NHẤT ở bên ngoài vòng lặp.
        // Tạo một list copy chứa các luật đang active để tránh làm ảnh hưởng list gốc
        java.util.List<AutoBidRule> sortedRules = new java.util.ArrayList<>();
        for (AutoBidRule rule : this.autoBidRules) {
            if (rule.isActive()) {
                sortedRules.add(rule);
            }
        }

        // Sắp xếp theo đúng luật Tie-breaker của bạn
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

            // 2. DUYỆT BẢNG XẾP HẠNG: Đi từ người có ưu tiên cao nhất xuống
            for (AutoBidRule topRule : sortedRules) {
                // Bỏ qua nếu luật đã tắt giữa chừng, hoặc người đó ĐANG LÀ người dẫn đầu
                if (!topRule.isActive() || (this.highestBidder != null && topRule.getBidder().getId().equals(this.highestBidder.getId()))) {
                    continue;
                }

                double targetPrice = this.currentHighestBid + topRule.getIncrement();

                if (targetPrice <= topRule.getMaxBid()) {
                    try {
                        BidTransaction autoTx = new BidTransaction(this, topRule.getBidder(), targetPrice);
                        validateBid(autoTx); // Kiểm tra tiền trong ví

                        // 🟢 CHỐT ĐƠN THÀNH CÔNG!
                        this.currentHighestBid = targetPrice;
                        this.highestBidder = topRule.getBidder();
                        this.bidHistory.add(autoTx);
                        topRule.getBidder().addTransaction(autoTx);

                        System.out.println("🤖 [AUTO-BID] Tự động trả giá $" + targetPrice + " thay cho " + topRule.getBidder().getUserName());
                        notifyObservers(autoTx); // Vẽ lên biểu đồ

                        hasNewAction = true;
                        // THÀNH CÔNG THÌ PHẢI BREAK ĐỂ RESET LẠI TỪ ĐẦU
                        break;

                    } catch (AuctionException e) {
                        // 🔴 Lỗi (VD: Hết tiền). Tắt luật của người này.
                        System.out.println("⚠️ [AUTO-BID TẮT] Hủy lệnh của " + topRule.getBidder().getUserName() + " vì: " + e.getMessage());
                        topRule.setActive(false);
                    }
                } else {
                    // 🔴 Chạm trần Max Bid. Tắt luật của người này.
                    System.out.println("🏳️ [AUTO-BID TẮT] " + topRule.getBidder().getUserName() + " đã chạm trần Max Bid.");
                    topRule.setActive(false);
                }
            }
        } while (hasNewAction);
    }

    // QUẢN LÝ THÔNG TIN & PHÂN QUYỀN
    public synchronized boolean updateAuctionDetails(User requestor, Item newItem, LocalDateTime newStart, LocalDateTime newEnd) {
        // Chỉ cho phép sửa khi phiên đấu giá chưa bắt đầu (đang OPEN)
        if (this.status != AuctionStatus.OPEN || this.status == AuctionStatus.FINISHED) {
            //System.err.println("Lỗi: Không thể sửa thông tin khi phiên đấu giá đã chạy hoặc đã kết thúc");
            return false;
        }

        // Kiểm tra quyền: Người yêu cầu phải là Chủ sản phẩm (Seller) HOẶC là Quản trị viên (Admin)
        boolean isOwner = requestor.getId().equals(this.seller.getId());
        boolean isAdmin = requestor instanceof Admin;

        if (isOwner || isAdmin) {
            this.item = newItem;
            this.startTime = newStart;
            this.endTime = newEnd;
            //System.out.println("Cập nhật thông tin phiên đấu giá thành công!");
            return true;
        } else {
            //System.out.println("Từ chối truy cập: Bạn không có quyền truy cập!");
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
            //System.out.println("Phiên đấu giá đã bị hủy. Lý do: " + reason);
            return true;
        }
        return false;
    }

    // QUẢN LÝ TRẠNG THÁI
    public synchronized void startAuction() {
        if (this.status == AuctionStatus.OPEN) {
            this.status = AuctionStatus.RUNNING;
            //System.out.println("Phiên đấu giá cho sản phẩm '" + this.item.getNameItem() + "' ĐÃ BẮT ĐẦU!");
        } else {
            //System.out.println("Không thể bắt đầu. Trạng thái hiện tại: " + this.status);
        }
    }

    public synchronized void closeAuction() {
        if (this.status == AuctionStatus.RUNNING) {
            this.status = AuctionStatus.FINISHED;
            //System.out.println("Phiên đấu giá ĐÃ KẾT THÚC!");
            determineWinner(); // Gọi luôn hàm công bố người thắng cuộc của bạn
        }
    }

    // KQ
    public void determineWinner() {
        if (this.highestBidder != null) {
            //System.out.println("Người chiến thắng: " + this.highestBidder.getUserName() + " với mức giá: " + this.currentHighestBid);
        } else {
            //System.out.println("Không có ai tham gia trả giá cho phiên đấu giá này.");
        }
    }

    // ĐÃ FIX: THÊM CÁC RÀO CHẮN BẢO MẬT (EDGE CASES)
    public synchronized void registerAutoBid(Bidder bidder, double maxBid, double increment) throws AuctionException {
        if(this.status == AuctionStatus.FINISHED){
            throw new AuctionClosedException("Lỗi: Không thể cài Auto-bid vì phiên đấu giá đã kết thúc!");
        }

        // EDGE CASE 2: Kiểm tra tính hợp lệ của Max Bid
        if (maxBid <= this.currentHighestBid) {
            throw new InvalidBidException("Giá tối đa (Max Bid) phải lớn hơn giá hiện tại!");
        }

        // EDGE CASE 3: Kiểm tra bước giá (Increment)
        if (increment <= 0) {
            throw new InvalidBidException("Bước giá (Increment) phải là một số lớn hơn 0!");
        }

        // EDGE CASE 4: Ví tiền không đủ bảo lãnh mức Max Bid
        if (bidder.getBalance() < maxBid) {
            throw new InsufficientBalanceException("Số dư không đủ để thiết lập Auto-bid tới mức giá này.", bidder.getBalance(), maxBid);
        }

        AutoBidRule newRule = new AutoBidRule(bidder, maxBid, increment);
        this.autoBidRules.add(newRule);
    }

    // THÊM MỚI 3 HÀM CỦA OBSERVER PATTERN:
    // 1. Cho phép người dùng tham gia xem (Đăng ký nhận thông báo)
    public synchronized void addObserver(AuctionObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
            //System.out.println("Một người dùng vừa vào xem phiên đấu giá " + this.item.getNameItem());
        }
    }

    // 2. Cho phép người dùng thoát ra (Hủy nhận thông báo)
    public synchronized void removeObserver(AuctionObserver observer) {
        observers.remove(observer);
    }

    // 3. Hàm cầm loa thông báo cho tất cả mọi người
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

    // HÀM NÀY VÀO TRONG CLASS AUCTION ĐỂ CHỐT BUG TRANSIENT
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject(); // Đọc các dữ liệu bình thường từ file
        this.notificationPool = Executors.newCachedThreadPool(); // Khởi tạo lại Thread Pool mới sau khi hồi sinh đối tượng
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