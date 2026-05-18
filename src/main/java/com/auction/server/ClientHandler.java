package com.auction.server; // Hoặc com.auction.server.network

import com.auction.exception.AuctionException;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.protocol.StatusType;
import com.auction.protocol.BidPayload;
import com.auction.utils.AuctionManager;

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
                    sendResponse(response);

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

    // Gửi phản hồi một cách an toàn (hỗ trợ broadcast)
    public synchronized void sendResponse(Response response) {
        if (out != null) {
            try {
                out.writeObject(response);
                out.flush();
            } catch (IOException e) {
                System.err.println("❌ Lỗi khi gửi dữ liệu cho client: " + e.getMessage());
            }
        }
    }

    // Hàm não bộ: Xử lý các loại Request khác nhau
    private Response processRequest(Request request) {
        ActionType action = request.getAction();

        switch (action) {
            case PLACE_BID:
                try {
                    BidPayload bidData = (BidPayload) request.getPayload();
                    System.out.println("Nhận được Bid: User " + bidData.getUsername() + " đặt " + bidData.getBidAmount() + "$ cho sản phẩm " + bidData.getAuctionId());

                    Auction auction = AuctionManager.getInstance().getAuctionById(bidData.getAuctionId());
                    if (auction == null) {
                        return new Response(StatusType.ERROR, "Phiên đấu giá không tồn tại.", null);
                    }

                    // Tạo Bidder tạm thời (Do chưa có hệ thống Auth hoàn chỉnh)
                    Bidder dummyBidder = new Bidder(bidData.getUsername(), "123", bidData.getUsername() + "@mail.com", 999999.0);
                    
                    // Xử lý nghiệp vụ thực tế
                    dummyBidder.placeBid(auction, bidData.getBidAmount());

                    // Tự động lưu trạng thái xuống file
                    ServerApp.getAuctionDAO().update(auction);

                    // Broadcasting: Thông báo toàn cục cho tất cả client về giá mới
                    ServerApp.broadcast(new Response(StatusType.SUCCESS, "UPDATE_AUCTION", auction));

                    return new Response(StatusType.SUCCESS, "Đặt giá thành công!", null);
                } catch (AuctionException e) {
                    return new Response(StatusType.ERROR, e.getMessage(), null);
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi hệ thống: " + e.getMessage(), null);
                }

            case GET_AUCTION_LIST:
                // Lấy toàn bộ danh sách phiên đấu giá hiện có
                return new Response(StatusType.SUCCESS, "Danh sách phiên đấu giá", AuctionManager.getInstance().getAllAuctions());

            case LOGIN:
                try {
                    // 1. Lấy dữ liệu Client gửi lên (username|password)
                    String loginData = (String) request.getPayload();
                    String[] credentials = loginData.split("\\|");
                    String username = credentials[0];
                    String password = credentials[1];

                    // 2. Kiểm tra (Giả lập logic check DB)
                    // Ở đây ta cho phép đăng nhập nếu có nhập pass
                    if (password != null && !password.isEmpty()) {

                        // 3. TẠO ĐỐI TƯỢNG TRẢ VỀ (ĐÂY LÀ KHÚC QUAN TRỌNG NHẤT)
                        // Giả lập tài khoản này có 50.000$
                        Bidder loggedInUser = new Bidder(username, password, username + "@gmail.com", 50000.0);

                        // Nhét loggedInUser vào tham số thứ 3 (data) của Response
                        return new Response(StatusType.SUCCESS, "Đăng nhập thành công!", loggedInUser);
                    } else {
                        return new Response(StatusType.ERROR, "Mật khẩu không được để trống!", null);
                    }
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Dữ liệu đăng nhập không hợp lệ.", null);
                }
            default:
                return new Response(StatusType.ERROR, "Không hỗ trợ hành động này.", null);
        }
    }

    private void closeConnections() {
        try {
            ServerApp.removeClient(this); // Báo cho Server biết đã ngắt kết nối
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}