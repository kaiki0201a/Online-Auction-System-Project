package com.auction.server;

import com.auction.exception.AuctionException;
import com.auction.model.Auction;
import com.auction.model.Bidder;
import com.auction.model.User;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.protocol.StatusType;
import com.auction.protocol.BidPayload;
import com.auction.utils.AuctionManager;
import com.auction.utils.UserManager;

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
            // LƯU Ý SỐNG CÒN: Khởi tạo OutputStream trước để tránh Deadlock
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Vòng lặp phục vụ: Lắng nghe liên tục các Request từ Client này
            while (true) {
                try {
                    Request request = (Request) in.readObject();
                    Response response = processRequest(request);

                    // Gửi câu trả lời về cho Client (chỉ gửi nếu có phản hồi cụ thể)
                    if (response != null) {
                        sendResponse(response);
                    }

                } catch (ClassNotFoundException e) {
                    System.err.println("⚠️ Không hiểu định dạng gói tin từ Client: " + socket.getInetAddress());
                }
            }

        } catch (java.io.EOFException | java.net.SocketException e) {
            // Bắt lỗi khi người dùng bấm [X] tắt app hoặc rớt mạng
            System.out.println("🔌 Client " + socket.getInetAddress() + " đã ngắt kết nối (Đóng ứng dụng).");
        } catch (IOException e) {
            // Bắt các lỗi I/O vặt khác
            System.out.println("⚠️ Lỗi luồng mạng với Client " + socket.getInetAddress() + ": " + e.getMessage());
        } finally {
            closeConnections();
        }
    }

    /**
     * Gửi phản hồi an toàn.
     * Từ khóa 'synchronized' ngăn chặn đụng độ luồng khi Server gọi broadcast()
     * đè lên luồng Client đang tự động trả lời.
     */
    public synchronized void sendResponse(Response response) {
        if (out != null) {
            try {
                out.reset(); // Xóa cache object cũ, ép Java gửi bản sao mới nhất của Object
                out.writeObject(response);
                out.flush();
            } catch (IOException e) {
                System.err.println("❌ Lỗi khi gửi dữ liệu cho client: " + e.getMessage());
            }
        }
    }

    // Hàm não bộ: Xử lý các loại Request khác nhau thông qua Switch-Case
    private Response processRequest(Request request) {
        ActionType action = request.getAction();

        switch (action) {
            case PLACE_BID:
                try {
                    BidPayload bidData = (BidPayload) request.getPayload();

                    // Lấy thông tin từ Manager
                    Auction auction = AuctionManager.getInstance().getAuctionById(bidData.getAuctionId());
                    if (auction == null) {
                        return new Response(StatusType.ERROR, "Phiên đấu giá không tồn tại.", null);
                    }

                    Bidder realBidder = (Bidder) UserManager.getInstance().getUser(bidData.getUsername());
                    if (realBidder == null) {
                        return new Response(StatusType.ERROR, "Tài khoản không hợp lệ.", null);
                    }

                    // Thực hiện nghiệp vụ đặt giá (Logic đồng bộ tránh Race Condition nằm trong hàm này)
                    realBidder.placeBid(auction, bidData.getBidAmount());

                    // Cập nhật dữ liệu qua Manager
                    AuctionManager.getInstance().updateAuction(auction);

                    // BROADCAST: Thông báo cho toàn bộ mạng lưới về giá mới
                    ServerApp.broadcastAuctionUpdate(auction, "UPDATE_AUCTION");

                    return new Response(StatusType.SUCCESS, "Đặt giá thành công!", null);

                } catch (AuctionException e) {
                    // Bắt đúng lỗi nghiệp vụ (hết tiền, phiên đã đóng, giá quá thấp...)
                    return new Response(StatusType.ERROR, e.getMessage(), null);
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi hệ thống khi xử lý đặt giá.", null);
                }

            case CREATE_AUCTION:
                try {
                    Auction newAuction = (Auction) request.getPayload();

                    // Thêm vào danh sách quản lý RAM
                    AuctionManager.getInstance().getAllAuctions().add(newAuction);

                    // Lưu ngay xuống file qua DAO để đảm bảo Data Persistence
                    ServerApp.getAuctionDAO().save(newAuction);

                    // BROADCAST: Báo cáo có sản phẩm mới lên sàn
                    ServerApp.broadcastAuctionUpdate(null, "UPDATE_AUCTION");

                    return new Response(StatusType.SUCCESS, "Đăng sản phẩm thành công!", null);

                } catch (Exception e) {
                    System.err.println("Lỗi khi tạo phiên đấu giá: " + e.getMessage());
                    return new Response(StatusType.ERROR, "Lỗi hệ thống khi lưu sản phẩm.", null);
                }

            case GET_AUCTION_LIST:
                return new Response(StatusType.SUCCESS, "Danh sách đấu giá", AuctionManager.getInstance().getAllAuctions());

            case GET_USER_LIST:
                return new Response(StatusType.SUCCESS, "Danh sách User", UserManager.getInstance().getAllUsers());

            case BAN_USER:
                try {
                    String targetUsername = (String) request.getPayload();
                    User targetUser = UserManager.getInstance().getUser(targetUsername);

                    if (targetUser != null) {
                        // Toggle (Bật/Tắt) trạng thái khóa tài khoản
                        targetUser.setBanned(!targetUser.isBanned());
                        return new Response(StatusType.SUCCESS, "Đã cập nhật trạng thái tài khoản!", null);
                    }
                    return new Response(StatusType.ERROR, "Không tìm thấy User.", null);
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi khi xử lý Ban/Unban.", null);
                }

            case CANCEL_AUCTION:
                try {
                    String targetAuctionId = (String) request.getPayload();
                    Auction auctionToCancel = AuctionManager.getInstance().getAuctionById(targetAuctionId);

                    if (auctionToCancel != null) {
                        auctionToCancel.setStatus(com.auction.model.AuctionStatus.CANCELED);
                        AuctionManager.getInstance().updateAuction(auctionToCancel);

                        // BROADCAST: Cập nhật lại UI vì có phiên bị hủy
                        ServerApp.broadcastAuctionUpdate(null, "UPDATE_AUCTION");
                        return new Response(StatusType.SUCCESS, "Đã ép dừng phiên đấu giá!", null);
                    }
                    return new Response(StatusType.ERROR, "Không tìm thấy phiên đấu giá.", null);
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi khi hủy phiên.", null);
                }

            case LOGIN:
                try {
                    String loginData = (String) request.getPayload();
                    String[] credentials = loginData.split("\\|");
                    String username = credentials[0];
                    String password = credentials[1];

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
                return new Response(StatusType.SUCCESS, "Đã đăng xuất", null);

            default:
                return new Response(StatusType.ERROR, "Server không hỗ trợ hành động này.", null);
        }
    }

    private void closeConnections() {
        try {
            ServerApp.removeClient(this);
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Lỗi khi đóng kết nối luồng Client.");
        }
    }
}