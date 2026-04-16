package com.auction.service;

import com.auction.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList; // Dùng cái này thay cho ArrayList

public class AutoBidService implements Runnable {
    // Danh sách các lệnh bài đăng ký tự động
    private final List<AutoBidRule> rules = new CopyOnWriteArrayList<>();

    // Ổ khóa (Monitor) để dùng cho cơ chế wait/notify
    private final Object lock = new Object();
    private volatile boolean hasNewBidEvent = false;
    private volatile boolean isRunning = true;

    // Hàm cho phép người dùng đăng ký luật
    public void registerRule(AutoBidRule rule) {
        rules.add(rule);
    }

    // Hàm này sẽ được gọi TỪ BÊN NGOÀI khi có người bình thường đặt giá thành công
    public void notifyNewBidEvent() {
        synchronized (lock) {
            hasNewBidEvent = true;
            lock.notifyAll(); // Tiếng chuông báo thức: ĐÁNH THỨC ROBOT DẬY!
        }
    }

    @Override
    public void run() {
        System.out.println("🤖 [Hệ thống] Robot Auto-Bid đã được kích hoạt chạy ngầm...");

        while (isRunning) {
            synchronized (lock) {
                while (!hasNewBidEvent) {
                    try {
                        // Tiêu chí 1: Robot sẽ "ngủ đông" tại dòng này, CPU = 0%
                        lock.wait();
                    } catch (InterruptedException e) {
                        System.out.println("Robot bị tắt đột ngột.");
                        return;
                    }
                }
                // Khi bị đánh thức (thoát khỏi wait), hạ cờ xuống để chuẩn bị cho lần ngủ tiếp theo
                hasNewBidEvent = false;
            }

            // Bắt đầu làm việc: Quét xem có ai cần nâng giá không
            processRules();
        }
    }

    // Thêm hàm để gọi khi muốn tắt robot:
    public void shutdown() {
        this.isRunning = false;
        // Bấm chuông đánh thức robot dậy lần cuối để nó tự thoát vòng lặp
        synchronized(lock) {
            lock.notifyAll();
        }
    }

    private void processRules() {
        for (AutoBidRule rule : rules) {
            Auction auction = rule.getAuction();
            Bidder bidder = rule.getBidder();

            // Bỏ qua nếu phiên đấu giá đã kết thúc
            if (auction.getStatus() != AuctionStatus.RUNNING) continue;

            // Tiêu chí 3: KHÔNG tự đấu giá với chính mình
            if (auction.getHighestBidder() != null &&
                    auction.getHighestBidder().getId().equals(bidder.getId())) {
                continue; // Đang là người dẫn đầu rồi thì không cần tự nâng giá nữa
            }

            // Tính toán giá tiếp theo
            double nextBid = auction.getCurrentHighestBid() + rule.getIncrement();

            // Tiêu chí 2: Kiểm tra maxBid và Số dư tài khoản
            if (nextBid <= rule.getMaxBid() && nextBid <= bidder.getBalance()) {
                try {
                    // Tạo hóa đơn và ép phiên đấu giá nhận
                    BidTransaction botTx = new BidTransaction(auction, bidder, nextBid);
                    auction.processBid(botTx, this);

                    System.out.println("⚡ [Auto-Bid] Robot đã tự động trả $" + nextBid + " thay cho " + bidder.getUserName());

                    // Khi robot nâng giá, nó lại tạo ra một giá trị mới.
                    // Cần đánh thức lại hệ thống để các robot khác kiểm tra xem có cần đấu lại không.
                    notifyNewBidEvent();

                } catch (Exception e) {
                    System.out.println("❌ [Auto-Bid] Lỗi đặt giá thay cho " + bidder.getUserName() + ": " + e.getMessage());
                }
            }
        }
    }
}