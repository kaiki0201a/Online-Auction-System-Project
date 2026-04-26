package com.auction.client;

import com.auction.network.BidRequest;
import com.auction.network.UpdateMessage;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class AuctionClient {
    public static void main(String[] args) {
        String serverAddress = "localhost";
        int port = 8080;

        try (Socket socket = new Socket(serverAddress, port);
             // Nhớ nguyên tắc: Tạo ống gửi (Out) trước ống nhận (In)
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            System.out.println("✅ Đã kết nối thành công vào phòng đấu giá!");

            // 1. Tạo một luồng (Thread) riêng chuyên để NGHE tin nhắn từ Server
            Thread listenerThread = new Thread(() -> {
                try {
                    while (true) {
                        // Cứ có gói tin UpdateMessage nào bay tới là bóc ra đọc
                        UpdateMessage msg = (UpdateMessage) in.readObject();
                        System.out.println("\n[SERVER BÁO KẾT QUẢ] 📢 " + msg.getSystemMessage());
                        System.out.println("👉 Người đang dẫn đầu: " + msg.getHighestBidder() + " | Mức giá: $" + msg.getHighestBidAmount());
                    }
                } catch (Exception e) {
                    System.out.println("❌ Ngắt kết nối với phòng đấu giá.");
                }
            });
            listenerThread.start(); // Cho luồng nghe bắt đầu chạy

            // 2. Tạm ngủ 2 giây để chờ luồng nghe khởi động xong
            Thread.sleep(2000);

            // 3. Tiến hành đặt giá!
            BidRequest myBid = new BidRequest("PhuBa_007", 55000.0);
            out.writeObject(myBid);
            out.flush();
            System.out.println("💸 Bạn vừa gửi yêu cầu đặt giá $" + myBid.getBidAmount());

            // 4. Lệnh này ép chương trình Client không được tắt, cứ đứng đó mà nghe ngóng
            listenerThread.join();

        } catch (Exception e) {
            System.out.println("❌ Không thể kết nối. Bạn đã bật Server chưa? Lỗi: " + e.getMessage());
        }
    }
}