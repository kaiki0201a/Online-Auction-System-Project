package com.auction.model;

import java.time.LocalDateTime;

public class AuctionTimer implements Runnable {

    private Auction auction;

    public AuctionTimer(Auction auction) {
        this.auction = auction;
    }

    @Override
    public void run() {
        try {
            // Vòng lặp kiểm tra thời gian
            while (LocalDateTime.now().isBefore(auction.getEndTime())) {
                Thread.sleep(1000);
            }

            // Hết giờ -> Tự động đóng phiên
            System.out.println("\n[HỆ THỐNG] Đồng hồ đếm ngược báo hết giờ cho phiên: " + auction.getAuctionId());
            auction.closeAuction();

        } catch (InterruptedException e) {
            System.out.println("Luồng đếm ngược bị gián đoạn");
            Thread.currentThread().interrupt();
        }
    }
}