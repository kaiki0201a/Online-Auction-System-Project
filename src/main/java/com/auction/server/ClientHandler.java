package com.auction.server; // Hoặc com.auction.server.network

import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.protocol.StatusType;
import com.auction.protocol.BidPayload; // Ví dụ payload

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            // LƯU Ý SỐNG CÒN SỐ 2: Phía Server phải khởi tạo ObjectOutputStream TRƯỚC
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Vòng lặp phục vụ: Lắng nghe liên tục các Request từ Client này
            while (true) {
                try {
                    // Chờ Client gửi Request
                    Request request = (Request) in.readObject();

                    // Gọi hàm xử lý yêu cầu và lấy câu trả lời
                    Response response = processRequest(request);

                    // Gửi câu trả lời về cho Client
                    out.writeObject(response);
                    out.flush();

                } catch (ClassNotFoundException e) {
                    System.err.println("⚠️ Không hiểu gói tin từ Client.");
                }
            }

        } catch (IOException e) {
            System.out.println("🔌 Client " + socket.getInetAddress() + " đã ngắt kết nối.");
        } finally {
            closeConnections();
        }
    }

    // Hàm não bộ: Xử lý các loại Request khác nhau
    private Response processRequest(Request request) {
        ActionType action = request.getAction();

        switch (action) {
            case PLACE_BID:
                // Ép kiểu (cast) payload về đúng loại BidPayload
                BidPayload bidData = (BidPayload) request.getPayload();
                System.out.println("Nhận được Bid: User " + bidData.getUsername() + " đặt " + bidData.getBidAmount() + "$ cho sản phẩm " + bidData.getItemId());

                // TODO: Chỗ này sau này bạn sẽ gọi tới AuctionManager (logic nghiệp vụ)
                // để kiểm tra xem giá này có hợp lệ không (có lớn hơn giá cao nhất hiện tại không)

                // Tạm thời trả về SUCCESS luôn để test
                return new Response(StatusType.SUCCESS, "Đặt giá thành công!", null);

            case LOGIN:
                // Tương tự xử lý login...
                return new Response(StatusType.SUCCESS, "Đăng nhập thành công!", null);

            default:
                return new Response(StatusType.ERROR, "Không hỗ trợ hành động này.", null);
        }
    }

    private void closeConnections() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}