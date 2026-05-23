package com.auction.server;

import com.auction.exception.AuctionException;
import com.auction.model.*;
import com.auction.protocol.*;
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

            while (true) {
                try {
                    Request request = (Request) in.readObject();
                    Response response = processRequest(request);
                    if (response != null) {
                        sendResponse(response);
                    }
                } catch (ClassNotFoundException e) {
                    System.err.println("⚠️ Không hiểu định dạng gói tin từ Client: " + socket.getInetAddress());
                }
            }

        } catch (java.io.EOFException | java.net.SocketException e) {
            System.out.println("🔌 Client " + socket.getInetAddress() + " đã ngắt kết nối.");
        } catch (IOException e) {
            System.out.println("⚠️ Lỗi luồng mạng với Client " + socket.getInetAddress() + ": " + e.getMessage());
        } finally {
            closeConnections();
        }
    }

    public synchronized void sendResponse(Response response) {
        if (out != null) {
            try {
                out.reset();
                out.writeObject(response);
                out.flush();
            } catch (IOException e) {
                System.err.println("❌ Lỗi khi gửi dữ liệu cho client: " + e.getMessage());
            }
        }
    }

    private Response processRequest(Request request) {
        ActionType action = request.getAction();

        switch (action) {

            // ─── ĐĂNG NHẬP ────────────────────────────────────────────────────
            case LOGIN:
                try {
                    String loginData = (String) request.getPayload();
                    String[] credentials = loginData.split("\\|");
                    if (credentials.length < 2) {
                        return new Response(StatusType.ERROR, "Dữ liệu đăng nhập không hợp lệ.", null);
                    }
                    String username = credentials[0];
                    String password = credentials[1];

                    if (UserManager.getInstance().authenticate(username, password)) {
                        User loggedInUser = UserManager.getInstance().getUser(username);
                        return new Response(StatusType.SUCCESS, "Đăng nhập thành công!", loggedInUser);
                    } else {
                        return new Response(StatusType.ERROR, "Sai tài khoản hoặc mật khẩu!", null);
                    }
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, e.getMessage(), null);
                }

            // ─── ĐĂNG KÝ (Hỗ trợ Bidder, Seller, Admin) ─────────────────────
            case REGISTER:
                try {
                    String registerData = (String) request.getPayload();
                    String[] data = registerData.split("\\|");
                    if (data.length < 3) {
                        return new Response(StatusType.ERROR, "Dữ liệu đăng ký không đủ.", null);
                    }
                    String regUsername = data[0];
                    String regPassword = data[1];
                    String regEmail    = data[2];
                    String regRole     = (data.length >= 4) ? data[3] : "Bidder";
                    // Field thứ 5: adminCode (chỉ cần khi role = Admin)
                    String adminCode   = (data.length >= 5) ? data[4] : null;

                    boolean success = UserManager.getInstance().register(
                            regUsername, regPassword, regEmail, regRole, adminCode);

                    if (success) {
                        ServerApp.getUserDAO().saveDataToFile();
                        String roleDisplay = switch (regRole.toLowerCase()) {
                            case "seller" -> "Người Bán (Seller)";
                            case "admin"  -> "Quản Trị Viên (Admin)";
                            default       -> "Người Mua (Bidder)";
                        };
                        return new Response(StatusType.SUCCESS,
                                "Đăng ký thành công! Tài khoản " + roleDisplay + " có thể đăng nhập ngay.", null);
                    } else {
                        if ("Admin".equalsIgnoreCase(regRole)) {
                            return new Response(StatusType.ERROR,
                                    "Mã xác nhận Admin không đúng hoặc tên đăng nhập đã tồn tại!", null);
                        }
                        return new Response(StatusType.ERROR,
                                "Tên đăng nhập '" + regUsername + "' đã tồn tại!", null);
                    }
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi khi xử lý đăng ký: " + e.getMessage(), null);
                }

            // ─── ĐẶT GIÁ ─────────────────────────────────────────────────────
            case PLACE_BID:
                try {
                    BidPayload bidData = (BidPayload) request.getPayload();

                    Auction auction = AuctionManager.getInstance().getAuctionById(bidData.getAuctionId());
                    if (auction == null) {
                        return new Response(StatusType.ERROR, "Phiên đấu giá không tồn tại.", null);
                    }

                    Bidder realBidder = (Bidder) UserManager.getInstance().getUser(bidData.getUsername());
                    if (realBidder == null) {
                        return new Response(StatusType.ERROR, "Tài khoản không hợp lệ.", null);
                    }

                    realBidder.placeBid(auction, bidData.getBidAmount());
                    AuctionManager.getInstance().updateAuction(auction);
                    ServerApp.broadcastAuctionUpdate(auction, "UPDATE_AUCTION");

                    return new Response(StatusType.SUCCESS, "Đặt giá thành công!", null);

                } catch (AuctionException e) {
                    return new Response(StatusType.ERROR, e.getMessage(), null);
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi hệ thống khi xử lý đặt giá.", null);
                }

            // ─── TẠO PHIÊN ĐẤU GIÁ ──────────────────────────────────────────
            case CREATE_AUCTION:
                try {
                    Auction newAuction = (Auction) request.getPayload();
                    AuctionManager.getInstance().getAllAuctions().add(newAuction);
                    ServerApp.getAuctionDAO().save(newAuction);

                    // Broadcast toàn bộ danh sách mới đến TẤT CẢ client
                    // (dùng data = danh sách để client tự refresh)
                    ServerApp.broadcastAuctionUpdate(
                            AuctionManager.getInstance().getAllAuctions(),
                            "AUCTION_CREATED");

                    return new Response(StatusType.SUCCESS, "Đăng sản phẩm thành công!", newAuction);

                } catch (Exception e) {
                    System.err.println("Lỗi khi tạo phiên đấu giá: " + e.getMessage());
                    return new Response(StatusType.ERROR, "Lỗi hệ thống khi lưu sản phẩm.", null);
                }

            // ─── LẤY DANH SÁCH PHIÊN ─────────────────────────────────────────
            case GET_AUCTION_LIST:
                return new Response(StatusType.SUCCESS, "Danh sách đấu giá",
                        AuctionManager.getInstance().getAllAuctions());

            // ─── LẤY DANH SÁCH NGƯỜI DÙNG ────────────────────────────────────
            case GET_USER_LIST:
                return new Response(StatusType.SUCCESS, "Danh sách User",
                        UserManager.getInstance().getAllUsers());

            // ─── BAN / UNBAN USER ─────────────────────────────────────────────
            case BAN_USER:
                try {
                    String targetUsername = (String) request.getPayload();
                    User targetUser = UserManager.getInstance().getUser(targetUsername);

                    if (targetUser != null) {
                        targetUser.setBanned(!targetUser.isBanned());
                        ServerApp.getUserDAO().saveDataToFile();
                        String act = targetUser.isBanned() ? "khóa" : "mở khóa";
                        return new Response(StatusType.SUCCESS,
                                "Đã " + act + " tài khoản " + targetUsername + "!", null);
                    }
                    return new Response(StatusType.ERROR, "Không tìm thấy User.", null);
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi khi xử lý Ban/Unban.", null);
                }

            // ─── HỦY PHIÊN ĐẤU GIÁ ───────────────────────────────────────────
            case CANCEL_AUCTION:
                try {
                    String targetAuctionId = (String) request.getPayload();
                    Auction auctionToCancel = AuctionManager.getInstance().getAuctionById(targetAuctionId);

                    if (auctionToCancel != null) {
                        auctionToCancel.setStatus(AuctionStatus.CANCELED);
                        AuctionManager.getInstance().updateAuction(auctionToCancel);
                        ServerApp.broadcastAuctionUpdate(
                                AuctionManager.getInstance().getAllAuctions(), "UPDATE_AUCTION");
                        return new Response(StatusType.SUCCESS, "Đã ép dừng phiên đấu giá!", null);
                    }
                    return new Response(StatusType.ERROR, "Không tìm thấy phiên đấu giá.", null);
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi khi hủy phiên.", null);
                }

            // ─── ADMIN DUYỆT SẢN PHẨM ────────────────────────────────────────
            case APPROVE_AUCTION:
                try {
                    String approveId = (String) request.getPayload();
                    Auction toApprove = AuctionManager.getInstance().getAuctionById(approveId);
                    if (toApprove == null)
                        return new Response(StatusType.ERROR, "Không tìm thấy phiên.", null);
                    if (toApprove.getStatus() != AuctionStatus.PENDING_APPROVAL)
                        return new Response(StatusType.ERROR, "Phiên này không ở trạng thái chờ duyệt.", null);

                    toApprove.setStatus(AuctionStatus.RUNNING);
                    AuctionManager.getInstance().updateAuction(toApprove);
                    ServerApp.getAuctionDAO().saveDataToFile();
                    // Broadcast toàn bộ danh sách để Seller thấy kho hàng, Bidder thấy phiên mới
                    ServerApp.broadcastAuctionUpdate(
                            AuctionManager.getInstance().getAllAuctions(), "AUCTION_APPROVED");
                    System.out.println("✅ [ADMIN] Đã duyệt: " + toApprove.getItem().getNameItem());
                    return new Response(StatusType.SUCCESS, "DUYỆT_OK|" + approveId, toApprove);
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi khi duyệt: " + e.getMessage(), null);
                }

            // ─── ADMIN TỪ CHỐI SẢN PHẨM ──────────────────────────────────────
            case REJECT_AUCTION:
                try {
                    String rejectId = (String) request.getPayload();
                    Auction toReject = AuctionManager.getInstance().getAuctionById(rejectId);
                    if (toReject == null)
                        return new Response(StatusType.ERROR, "Không tìm thấy phiên.", null);

                    toReject.setStatus(AuctionStatus.CANCELED);
                    AuctionManager.getInstance().updateAuction(toReject);
                    ServerApp.getAuctionDAO().saveDataToFile();
                    ServerApp.broadcastAuctionUpdate(
                            AuctionManager.getInstance().getAllAuctions(), "AUCTION_REJECTED");
                    System.out.println("❌ [ADMIN] Đã từ chối: " + toReject.getItem().getNameItem());
                    return new Response(StatusType.SUCCESS, "TỪ_CHỐI_OK|" + rejectId, toReject);
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi khi từ chối: " + e.getMessage(), null);
                }

            // ─── NẠP TIỀN ─────────────────────────────────────────────────────
            case DEPOSIT:
                try {
                    String depositData = (String) request.getPayload();
                    String[] parts = depositData.split("\\|");
                    if (parts.length < 2) {
                        return new Response(StatusType.ERROR, "Dữ liệu không hợp lệ.", null);
                    }
                    String targetUser = parts[0];
                    double amount = Double.parseDouble(parts[1]);

                    User user = UserManager.getInstance().getUser(targetUser);
                    if (user instanceof Bidder) {
                        Bidder bidder = (Bidder) user;
                        bidder.setBalance(bidder.getBalance() + amount);
                        ServerApp.getUserDAO().saveDataToFile();
                        return new Response(StatusType.SUCCESS, "Nạp tiền thành công!", bidder.getBalance());
                    } else if (user instanceof Seller) {
                        Seller seller = (Seller) user;
                        seller.setBalance(seller.getBalance() + amount);
                        ServerApp.getUserDAO().saveDataToFile();
                        return new Response(StatusType.SUCCESS, "Nạp tiền thành công!", seller.getBalance());
                    }
                    return new Response(StatusType.ERROR, "Không tìm thấy tài khoản.", null);
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi khi nạp tiền: " + e.getMessage(), null);
                }

            // ─── RÚT TIỀN ─────────────────────────────────────────────────────
            case WITHDRAW:
                try {
                    String withdrawData = (String) request.getPayload();
                    String[] parts = withdrawData.split("\\|");
                    if (parts.length < 2) {
                        return new Response(StatusType.ERROR, "Dữ liệu không hợp lệ.", null);
                    }
                    String targetUser = parts[0];
                    double amount = Double.parseDouble(parts[1]);

                    User user = UserManager.getInstance().getUser(targetUser);
                    if (user instanceof Bidder) {
                        Bidder bidder = (Bidder) user;
                        if (bidder.getBalance() < amount) {
                            return new Response(StatusType.ERROR, "Số dư không đủ để rút tiền!", null);
                        }
                        bidder.setBalance(bidder.getBalance() - amount);
                        ServerApp.getUserDAO().saveDataToFile();
                        return new Response(StatusType.SUCCESS, "Rút tiền thành công!", bidder.getBalance());
                    } else if (user instanceof Seller) {
                        Seller seller = (Seller) user;
                        if (seller.getBalance() < amount) {
                            return new Response(StatusType.ERROR, "Số dư không đủ để rút tiền!", null);
                        }
                        seller.setBalance(seller.getBalance() - amount);
                        ServerApp.getUserDAO().saveDataToFile();
                        return new Response(StatusType.SUCCESS, "Rút tiền thành công!", seller.getBalance());
                    }
                    return new Response(StatusType.ERROR, "Không tìm thấy tài khoản.", null);
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi khi rút tiền: " + e.getMessage(), null);
                }

            // ─── CẬP NHẬT HỒ SƠ ──────────────────────────────────────────────
            case UPDATE_PROFILE:
                try {
                    String profileData = (String) request.getPayload();
                    String[] parts = profileData.split("\\|");
                    if (parts.length < 3) {
                        return new Response(StatusType.ERROR, "Dữ liệu không hợp lệ.", null);
                    }
                    String username    = parts[0];
                    String newEmail    = parts[1];
                    String newPassword = parts[2]; // Có thể rỗng nếu không đổi

                    User user = UserManager.getInstance().getUser(username);
                    if (user == null) {
                        return new Response(StatusType.ERROR, "Không tìm thấy tài khoản.", null);
                    }
                    if (!newEmail.isEmpty()) {
                        user.setEmail(newEmail);
                    }
                    if (!newPassword.isEmpty()) {
                        user.setPassWord(user.hashPasswordPublic(newPassword));
                    }
                    ServerApp.getUserDAO().saveDataToFile();
                    return new Response(StatusType.SUCCESS, "Cập nhật thông tin thành công!", user);
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi khi cập nhật: " + e.getMessage(), null);
                }

            // ─── ĐĂNG XUẤT ────────────────────────────────────────────────────
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