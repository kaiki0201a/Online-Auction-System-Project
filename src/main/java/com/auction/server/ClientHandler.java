package com.auction.server;

import com.auction.network.BidRequest;
import com.auction.network.UpdateMessage;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (
                // LƯU Ý SỐNG CÒN: Luôn phải tạo ống gửi (OutputStream) trước ống nhận (InputStream)
                // Nếu làm ngược lại, Client và Server sẽ đứng nhìn nhau chờ đợi mãi mãi (Deadlock)
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())
        ) {
            // 1. Đăng ký khách hàng vào danh sách "loa phường"
            AuctionServer.addClient(out);

            // 2. Vòng lặp phục vụ khách hàng này mãi mãi (cho đến khi họ thoát)
            while (true) {
                // Đứng chờ khách gửi BidRequest
                BidRequest request = (BidRequest) in.readObject();
                System.out.println("📩 Server nhận giá: $" + request.getBidAmount() + " từ " + request.getBidderName());

                // --- GÓC NỐI CODE ---
                // Sau này, bạn sẽ gọi hàm auction.processBid(transaction) có chứa chữ synchronized ở ngay vị trí này.
                // Nếu hàm đó ném ra AuctionException (ví dụ giá quá thấp), bạn catch nó lại và không làm bước dưới nữa.
                // --------------------

                // 3. Giả lập quá trình đặt giá thành công, tạo gói tin UpdateMessage
                UpdateMessage updateMsg = new UpdateMessage(
                        request.getBidderName(),
                        request.getBidAmount(),
                        "🔥 Tin nóng: " + request.getBidderName() + " vừa nâng giá lên $" + request.getBidAmount() + "!"
                );

                // 4. Phát thanh cho tất cả mọi người cùng biết
                AuctionServer.broadcast(updateMsg);
            }

        } catch (Exception e) {
            System.out.println("❌ Một Bidder đã rời khỏi phòng đấu giá.");
        }
    }
}