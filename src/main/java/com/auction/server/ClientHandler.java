package com.auction.server;

import com.auction.exception.AuctionException;
import com.auction.model.*;
import com.auction.protocol.*;
import com.auction.strategy.BidContext;
import com.auction.strategy.ManualBidStrategy;
import com.auction.utils.AuctionManager;
import com.auction.utils.UserManager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ClientHandler — xử lý tất cả request từ một client cụ thể.
 *
 * FIXES THỰC HIỆN:
 * 1. CREATE_AUCTION: trả về List<Auction> thay vì single Auction — client tự renderInventory
 * 2. APPROVE_AUCTION: auto-LIVE nếu startTime <= now, còn lại set APPROVED + lên scheduler
 *    Trả về List<Auction> thay vì single Auction.
 *    Trigger scheduleAutoClose() sau khi approve để auction tự kết thúc đúng giờ.
 * 3. REJECT_AUCTION: set REJECTED thay vì CANCELED — seller thấy đúng status.
 *    Trả về List<Auction>.
 * 4. PLACE_BID: trigger settleAuction() khi thời gian kết thúc
 * 5. Toàn bộ broadcast dùng List<Auction> để client filter nhất quán.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
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

            // ─── ĐĂNG KÝ ─────────────────────────────────────────────────────
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

                    // Cho phép đặt giá khi RUNNING hoặc APPROVED (đã được duyệt)
                    if (auction.getStatus() != AuctionStatus.RUNNING &&
                        auction.getStatus() != AuctionStatus.OPEN &&
                        auction.getStatus() != AuctionStatus.APPROVED) {
                        return new Response(StatusType.ERROR,
                            "Phiên đấu giá không ở trạng thái có thể đặt giá. Trạng thái: " + auction.getStatus(), null);
                    }

                    Bidder realBidder = (Bidder) UserManager.getInstance().getUser(bidData.getUsername());
                    if (realBidder == null) {
                        return new Response(StatusType.ERROR, "Tài khoản không hợp lệ.", null);
                    }

                    // ─── Áp dụng Strategy Pattern: Manual Bid ───
                    // Thay thế cách gọi trực tiếp realBidder.placeBid() bằng BidContext
                    // ⇒ Cho phép hoán đổi sang AutoBidStrategy hoặc strategy khác không sửa code này
                    BidTransaction manualTx = new BidTransaction(auction, realBidder, bidData.getBidAmount());
                    BidContext bidContext = new BidContext(ManualBidStrategy.getInstance());
                    bidContext.executeBid(auction, manualTx);
                    realBidder.addTransaction(manualTx);

                    AuctionManager.getInstance().updateAuction(auction);

                    // Reschedule auto-close sau khi bid thành công
                    // Lý do: anti-sniping có thể đã thay đổi endTime bên trong processBid()
                    ServerApp.scheduleAutoClose(auction);

                    // Broadcast toàn bộ list để các client tự refresh
                    List<Auction> allAfterBid = AuctionManager.getInstance().getAllAuctions();
                    ServerApp.broadcastAuctionUpdate(allAfterBid, "UPDATE_AUCTION");

                    // Lưu số dư mới của bidder
                    ServerApp.getUserDAO().saveDataToFile();

                    return new Response(StatusType.SUCCESS, "Đặt giá thành công!", realBidder.getBalance());

                } catch (AuctionException e) {
                    return new Response(StatusType.ERROR, e.getMessage(), null);
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi hệ thống khi xử lý đặt giá.", null);
                }

            // ─── TẠO PHIÊN ĐẤU GIÁ ─────────────────────────────────────────
            case CREATE_AUCTION:
                try {
                    Auction newAuction = (Auction) request.getPayload();

                    // FIX: Thêm vào RAM list trước khi broadcast
                    AuctionManager.getInstance().getAllAuctions().add(newAuction);
                    // Flush file (RAM đã có newAuction rồi)
                    ServerApp.getAuctionDAO().saveDataToFile();

                    // FIX: Lấy toàn bộ list (đã bao gồm newAuction) để broadcast
                    List<Auction> updatedList = AuctionManager.getInstance().getAllAuctions();

                    // Broadcast với data = List<Auction> — client nhận và renderInventory ngay
                    ServerApp.broadcastAuctionUpdate(updatedList, "AUCTION_CREATED");

                    // FIX: Trả về List (không phải single Auction) để client filter ngay
                    System.out.println("📦 [SERVER] Auction mới tạo: " + newAuction.getItem().getNameItem() +
                        " | Tổng: " + updatedList.size() + " phiên");
                    return new Response(StatusType.SUCCESS, "AUCTION_CREATED", updatedList);

                } catch (Exception e) {
                    System.err.println("Lỗi khi tạo phiên đấu giá: " + e.getMessage());
                    return new Response(StatusType.ERROR, "Lỗi hệ thống khi lưu sản phẩm.", null);
                }

            // ─── LẤY DANH SÁCH PHIÊN ─────────────────────────────────────────
            case GET_AUCTION_LIST:
                return new Response(StatusType.SUCCESS, "Danh sách đấu giá",
                        AuctionManager.getInstance().getAllAuctions());

            // ─── LẤY DANH SÁCH USER ──────────────────────────────────────────
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
                        // FIX: Hoàn tiền cho bidder đang thắng (nếu có) trước khi hủy
                        Bidder refundedBidder = auctionToCancel.getHighestBidder();
                        auctionToCancel.refundOnCancel();

                        auctionToCancel.setStatus(AuctionStatus.CANCELED);
                        AuctionManager.getInstance().updateAuction(auctionToCancel);
                        ServerApp.getAuctionDAO().saveDataToFile();
                        ServerApp.getUserDAO().saveDataToFile();

                        List<Auction> allAfterCancel = AuctionManager.getInstance().getAllAuctions();
                        ServerApp.broadcastAuctionUpdate(allAfterCancel, "AUCTION_CANCELED");

                        // Broadcast số dư mới cho bidder được hoàn tiền
                        if (refundedBidder != null) {
                            String bidderMsg = "BIDDER_REFUND|" + refundedBidder.getUserName();
                            ServerApp.broadcast(new com.auction.protocol.Response(
                                    com.auction.protocol.StatusType.SUCCESS,
                                    bidderMsg,
                                    refundedBidder.getBalance()
                            ));
                            System.out.println("💸 [CANCEL] Đã hoàn tiền cho bidder "
                                    + refundedBidder.getUserName() + ": "
                                    + refundedBidder.getBalance());
                        }

                        return new Response(StatusType.SUCCESS,
                                "Đã hủy phiên và hoàn tiền cho người tham gia!", allAfterCancel);
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

                    // FIX: Auto-LIVE nếu startTime <= now, còn lại set APPROVED (chờ đến giờ)
                    LocalDateTime now = LocalDateTime.now();
                    if (toApprove.getStartTime() == null || !toApprove.getStartTime().isAfter(now)) {
                        // Bắt đầu ngay → RUNNING
                        toApprove.setStatus(AuctionStatus.RUNNING);
                        // FIX: Trigger scheduleAutoClose để auction tự kết thúc đúng giờ
                        ServerApp.scheduleAutoClose(toApprove);
                        System.out.println("✅ [ADMIN] Duyệt + RUNNING ngay: " + toApprove.getItem().getNameItem());
                    } else {
                        // Chưa đến giờ → APPROVED, scheduler sẽ chuyển sang RUNNING sau
                        toApprove.setStatus(AuctionStatus.APPROVED);
                        ServerApp.scheduleApprovedToLive(toApprove);
                        System.out.println("✅ [ADMIN] Duyệt → APPROVED (chờ giờ): " + toApprove.getItem().getNameItem());
                    }

                    AuctionManager.getInstance().updateAuction(toApprove);
                    ServerApp.getAuctionDAO().saveDataToFile();

                    // FIX: Broadcast với toàn bộ List<Auction> — client filter nhất quán
                    List<Auction> allAfterApprove = AuctionManager.getInstance().getAllAuctions();
                    ServerApp.broadcastAuctionUpdate(allAfterApprove, "AUCTION_APPROVED");

                    return new Response(StatusType.SUCCESS, "DUYỆT_OK|" + approveId, allAfterApprove);

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

                    // FIX: Set REJECTED thay vì CANCELED — seller thấy đúng lý do
                    toReject.setStatus(AuctionStatus.REJECTED);
                    AuctionManager.getInstance().updateAuction(toReject);
                    ServerApp.getAuctionDAO().saveDataToFile();

                    // FIX: Broadcast với List<Auction>
                    List<Auction> allAfterReject = AuctionManager.getInstance().getAllAuctions();
                    ServerApp.broadcastAuctionUpdate(allAfterReject, "AUCTION_REJECTED");

                    System.out.println("❌ [ADMIN] Đã từ chối: " + toReject.getItem().getNameItem());
                    return new Response(StatusType.SUCCESS, "TỪ_CHỐI_OK|" + rejectId, allAfterReject);

                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi khi từ chối: " + e.getMessage(), null);
                }
                // ─── CÀI ĐẶT AUTOBID ──────────────────────────────────────────────
                // THÊM ĐOẠN NÀY VÀO TRONG switch(action) của ClientHandler.processRequest(),
                // đặt ngay trước case DEPOSIT (hoặc trước "default").
            case SET_AUTOBID:
                try {
                    AutoBidPayload abPayload = (AutoBidPayload) request.getPayload();

                    Auction abAuction = AuctionManager.getInstance()
                            .getAuctionById(abPayload.getAuctionId());
                    if (abAuction == null) {
                        return new Response(StatusType.ERROR, "Phiên đấu giá không tồn tại.", null);
                    }

                    Bidder abBidder = (Bidder) UserManager.getInstance()
                            .getUser(abPayload.getBidderId());
                    if (abBidder == null) {
                        return new Response(StatusType.ERROR, "Tài khoản Bidder không hợp lệ.", null);
                    }

                    if (abPayload.isEnable()) {
                        // BẬT AutoBid: đăng ký rule vào phiên
                        abAuction.registerAutoBid(abBidder,
                                abPayload.getMaxAmount(),
                                abPayload.getIncrementAmount());

                        // Chạy vòng AutoBid + broadcast TRÊN BACKGROUND THREAD.
                        // Lý do: triggerAutoBids() có thể lặp hàng trăm vòng (nhiều bot cạnh nhau)
                        // → không được chạy trên thread ClientHandler (gây freeze toàn bộ app).
                        final Auction finalAuction = abAuction;
                        final Bidder finalBidder = abBidder;
                        java.util.concurrent.CompletableFuture.runAsync(() -> {
                            try {
                                // Kích hoạt vòng autobid (synchronized bên trong)
                                finalAuction.kickstartAutoBid();

                                // Lưu sau khi vòng autobid hoàn tất
                                AuctionManager.getInstance().updateAuction(finalAuction);
                                ServerApp.getAuctionDAO().saveDataToFile();
                                ServerApp.getUserDAO().saveDataToFile();

                                // Broadcast kết quả mới nhất lên tất cả client
                                List<Auction> allAfterAB = AuctionManager.getInstance().getAllAuctions();
                                ServerApp.broadcastAuctionUpdate(allAfterAB, "UPDATE_AUCTION");

                                System.out.printf("🤖 [AUTOBID BẬT] %s | Max: %.0f | Bước: %.0f | Phiên: %s%n",
                                        finalBidder.getUserName(),
                                        abPayload.getMaxAmount(),
                                        abPayload.getIncrementAmount(),
                                        finalAuction.getItem().getNameItem());
                            } catch (Exception ex) {
                                System.err.println("❌ [AUTOBID BG] Lỗi: " + ex.getMessage());
                            }
                        });

                        // Trả kết quả ngay cho client — không chờ vòng autobid xong
                        return new Response(StatusType.SUCCESS, "AUTOBID_OK",
                                "Đã bật AutoBid thành công!");

                    } else {
                        // TẮT AutoBid: deactivate toàn bộ rule của bidder này trong phiên
                        abAuction.getBidHistory(); // warm up (không cần thiết, chỉ minh hoạ)

                        // Truy cập autoBidRules qua getter — cần thêm getter vào Auction
                        // Xem hướng dẫn bên dưới nếu chưa có getAutoBidRules()
                        if (abAuction.getAutoBidRules() != null) {
                            abAuction.getAutoBidRules().stream()
                                    .filter(r -> r.getBidder().getUserName()
                                            .equals(abBidder.getUserName()))
                                    .forEach(r -> r.setActive(false));
                        }

                        ServerApp.getAuctionDAO().saveDataToFile();

                        System.out.println("🤖 [AUTOBID TẮT] " + abBidder.getUserName()
                                + " | Phiên: " + abAuction.getItem().getNameItem());

                        return new Response(StatusType.SUCCESS, "AUTOBID_OK",
                                "Đã tắt AutoBid thành công!");
                    }

                } catch (com.auction.exception.AuctionException e) {
                    return new Response(StatusType.ERROR, "AUTOBID_ERROR: " + e.getMessage(), null);
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "AUTOBID_ERROR: Lỗi hệ thống khi xử lý AutoBid.", null);
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
                    if (user instanceof Bidder bidder) {
                        bidder.setBalance(bidder.getBalance() + amount);
                        ServerApp.getUserDAO().saveDataToFile();
                        return new Response(StatusType.SUCCESS, "Nạp tiền thành công!", bidder.getBalance());
                    } else if (user instanceof Seller seller) {
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
                    if (user instanceof Bidder bidder) {
                        if (bidder.getBalance() < amount) {
                            return new Response(StatusType.ERROR, "Số dư không đủ để rút tiền!", null);
                        }
                        bidder.setBalance(bidder.getBalance() - amount);
                        ServerApp.getUserDAO().saveDataToFile();
                        return new Response(StatusType.SUCCESS, "Rút tiền thành công!", bidder.getBalance());
                    } else if (user instanceof Seller seller) {
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
                    String newPassword = parts[2];

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