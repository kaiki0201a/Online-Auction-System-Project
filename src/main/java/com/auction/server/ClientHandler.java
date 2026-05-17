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
import com.auction.utils.UserManager;
import com.auction.model.User;

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

                    // CHUẨN MVC: Controller gọi Manager, không gọi DAO
                    Auction auction = AuctionManager.getInstance().getAuctionById(bidData.getAuctionId());
                    if (auction == null) {
                        return new Response(StatusType.ERROR, "Phiên đấu giá không tồn tại.", null);
                    }

                    // CHUẨN OOP: Lấy User từ UserManager, tuyệt đối không dùng new Bidder(...)
                    Bidder realBidder = (Bidder) UserManager.getInstance().getUser(bidData.getUsername());
                    if (realBidder == null) {
                        return new Response(StatusType.ERROR, "Tài khoản không hợp lệ.", null);
                    }

                    // Thực hiện nghiệp vụ đặt giá
                    realBidder.placeBid(auction, bidData.getBidAmount());

                    // Cập nhật dữ liệu qua Manager
                    AuctionManager.getInstance().updateAuction(auction);

                    // Broadcast cho tất cả Client (cập nhật Realtime)
                    ServerApp.broadcast(new Response(StatusType.SUCCESS, "UPDATE_AUCTION", auction));

                    return new Response(StatusType.SUCCESS, "Đặt giá thành công!", null);

                } catch (AuctionException e) { // Bắt đúng lỗi nghiệp vụ (hết tiền, phiên đóng...)
                    return new Response(StatusType.ERROR, e.getMessage(), null);
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi hệ thống khi xử lý đặt giá", null);
                }

            case GET_AUCTION_LIST:
                // CHUẨN MVC: Gọi qua Manager
                return new Response(StatusType.SUCCESS, "Danh sách", AuctionManager.getInstance().getAllAuctions());

            case LOGIN:
                try {
                    String loginData = (String) request.getPayload();
                    String[] credentials = loginData.split("\\|");
                    String username = credentials[0];
                    String password = credentials[1];

                    // CHUẨN BẢO MẬT: Kiểm tra qua UserManager
                    if (UserManager.getInstance().authenticate(username, password)) {
                        User loggedInUser = UserManager.getInstance().getUser(username);
                        return new Response(StatusType.SUCCESS, "Đăng nhập thành công!", loggedInUser);
                    } else {
                        return new Response(StatusType.ERROR, "Sai tài khoản hoặc mật khẩu!", null);
                    }
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Dữ liệu đăng nhập không hợp lệ.", null);
                }

            case REGISTER:
                try {
                    String registerData = (String) request.getPayload();
                    // Giả sử payload gửi lên là: "username|password|email"
                    String[] data = registerData.split("\\|");
                    boolean success = UserManager.getInstance().register(data[0], data[1], data[2]);

                    if (success) {
                        return new Response(StatusType.SUCCESS, "Đăng ký thành công!", null);
                    } else {
                        return new Response(StatusType.ERROR, "Tên đăng nhập đã tồn tại!", null);
                    }
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Dữ liệu đăng ký không hợp lệ.", null);
                }

            case LOGOUT:
                // Tùy vào thiết kế, nếu cần ghi log đăng xuất thì gọi UserManager
                return new Response(StatusType.SUCCESS, "Đã đăng xuất", null);

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