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

                    if (targetUser == null)
                        return new Response(StatusType.ERROR, "Không tìm thấy User.", null);

                    // FIX: Không cho phép ban tài khoản Admin
                    if (targetUser instanceof Admin)
                        return new Response(StatusType.ERROR, "Không thể khóa tài khoản Admin!", null);

                    boolean wasBanned = targetUser.isBanned();
                    targetUser.setBanned(!wasBanned);
                    ServerApp.getUserDAO().saveDataToFile();
                    String act = targetUser.isBanned() ? "khóa" : "mở khóa";

                    // Khi KHÓA → cascade + FORCE_LOGOUT (áp dụng cho cả Bidder lẫn Seller)
                    if (targetUser.isBanned()) {
                        // Cascade cancel: hủy các phiên liên quan đến user bị khóa
                        if (targetUser instanceof Bidder bannedBidder) {
                            // FIX #8: Hủy/reset TẤT CẢ phiên mà bidder đã tham gia (qua bidHistory)
                            List<Auction> bannedBidderAuctions = AuctionManager.getInstance().getAllAuctions();
                            for (Auction a : bannedBidderAuctions) {
                                if (a.getStatus() != AuctionStatus.RUNNING
                                        && a.getStatus() != AuctionStatus.OPEN
                                        && a.getStatus() != AuctionStatus.APPROVED) continue;
                                boolean participated = a.getBidHistory().stream()
                                        .anyMatch(bt -> bt.getBidder().getUserName()
                                                .equals(bannedBidder.getUserName()));
                                if (!participated) continue;
                                if (a.getHighestBidder() != null
                                        && a.getHighestBidder().getUserName()
                                        .equals(bannedBidder.getUserName())) {
                                    a.refundOnCancel();
                                    a.setStatus(AuctionStatus.CANCELED);
                                    AuctionManager.getInstance().updateAuction(a);
                                    System.out.println("🚫 [BAN CASCADE] Hủy phiên "
                                            + a.getItem().getNameItem() + " (bidder đang dẫn đầu)");
                                } else {
                                    System.out.println("ℹ️ [BAN INFO] Bidder " + bannedBidder.getUserName()
                                            + " tham gia phiên " + a.getItem().getNameItem()
                                            + " nhưng không dẫn đầu — tiếp tục bình thường.");
                                }
                            }
                            ServerApp.getAuctionDAO().saveDataToFile();
                            ServerApp.getUserDAO().saveDataToFile();
                            ServerApp.broadcastAuctionUpdate(
                                    AuctionManager.getInstance().getAllAuctions(), "AUCTION_CANCELED");
                        } else if (targetUser instanceof Seller bannedSeller) {
                            // FIX: Seller bị ban → hủy các phiên PENDING/APPROVED của seller đó
                            List<Auction> allAuctions = AuctionManager.getInstance().getAllAuctions();
                            for (Auction a : allAuctions) {
                                if (a.getSeller().getUserName().equals(bannedSeller.getUserName())
                                        && (a.getStatus() == AuctionStatus.PENDING_APPROVAL
                                        || a.getStatus() == AuctionStatus.APPROVED)) {
                                    a.setStatus(AuctionStatus.CANCELED);
                                    AuctionManager.getInstance().updateAuction(a);
                                    System.out.println("🚫 [BAN CASCADE] Hủy phiên "
                                            + a.getItem().getNameItem() + " (seller bị khóa)");
                                }
                            }
                            ServerApp.getAuctionDAO().saveDataToFile();
                            ServerApp.getUserDAO().saveDataToFile();
                            ServerApp.broadcastAuctionUpdate(
                                    AuctionManager.getInstance().getAllAuctions(), "AUCTION_CANCELED");
                        }

                        // FIX: FORCE_LOGOUT áp dụng cho cả Bidder và Seller
                        ServerApp.broadcast(new Response(
                                StatusType.SUCCESS,
                                "FORCE_LOGOUT|" + targetUsername,
                                null
                        ));
                    }

                    // FIX: Trả về updated user list thay vì null → AdminController refresh ngay
                    List<User> updatedUsers = UserManager.getInstance().getAllUsers();
                    return new Response(StatusType.SUCCESS,
                            "Đã " + act + " tài khoản " + targetUsername + "!", updatedUsers);

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

                    // BUG #4 FIX: Không duyệt phiên đã hết hạn
                    LocalDateTime now = LocalDateTime.now();
                    if (toApprove.getEndTime() != null && toApprove.getEndTime().isBefore(now)) {
                        return new Response(StatusType.ERROR,
                                "❌ Không thể duyệt! Phiên đã hết hạn vào "
                                        + toApprove.getEndTime().format(
                                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                                        + ".\nVui lòng từ chối phiên này.", null);
                    }

                    // FIX: Auto-LIVE nếu startTime <= now, còn lại set APPROVED (chờ đến giờ)
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

                    // Fix #12: Gửi thêm thông báo riêng cho Seller để biết phiên bị từ chối
                    String sellerNotify = "AUCTION_REJECTED_SELLER|" + toReject.getSeller().getUserName()
                            + "|" + toReject.getItem().getNameItem();
                    ServerApp.broadcast(new Response(StatusType.SUCCESS, sellerNotify, null));

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

                        // BUG #7 FIX: Trigger autobid NGAY nếu bidder không phải dẫn đầu
                        // Không cần chờ người khác bid mới autobid mới kích hoạt
                        if (abAuction.getHighestBidder() == null
                                || !abAuction.getHighestBidder().getUserName()
                                .equals(abBidder.getUserName())) {
                            abAuction.triggerAutoBidsPublic();
                            // Cập nhật lại bidder balance sau khi autobid
                            ServerApp.getUserDAO().saveDataToFile();
                            // Broadcast update giá mới
                            List<Auction> afterAutoBid = AuctionManager.getInstance().getAllAuctions();
                            ServerApp.broadcastAuctionUpdate(afterAutoBid, "UPDATE_AUCTION");
                            System.out.println("🤖 [AUTOBID IMMEDIATE] Triggered for "
                                    + abBidder.getUserName() + " on " + abAuction.getItem().getNameItem());
                        }

                        // Flush dữ liệu xuống file
                        ServerApp.getAuctionDAO().saveDataToFile();

                        System.out.println("🤖 [AUTOBID BẬT] " + abBidder.getUserName()
                                + " | Max: " + abPayload.getMaxAmount()
                                + " | Bước: " + abPayload.getIncrementAmount()
                                + " | Phiên: " + abAuction.getItem().getNameItem());

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

                // ─── NẠP TIỀN ────────────────────────────────────────────────────────
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
                        double newBalance = bidder.getBalance() + amount;
                        bidder.setBalance(newBalance);
                        // FIX BUG B: Tạo WalletTransaction trên server và persist vào User object
                        WalletTransaction wt = new WalletTransaction(
                                WalletTransaction.Type.DEPOSIT, amount, newBalance);
                        bidder.addWalletTransaction(wt);
                        ServerApp.getUserDAO().saveDataToFile();
                        // Trả về WalletTransaction để client không cần tự tạo nữa
                        return new Response(StatusType.SUCCESS, "Nạp tiền thành công!", wt);
                    } else if (user instanceof Seller seller) {
                        double newBalance = seller.getBalance() + amount;
                        seller.setBalance(newBalance);
                        // FIX BUG B: Tạo WalletTransaction trên server và persist vào User object
                        WalletTransaction wt = new WalletTransaction(
                                WalletTransaction.Type.DEPOSIT, amount, newBalance);
                        seller.addWalletTransaction(wt);
                        ServerApp.getUserDAO().saveDataToFile();
                        // Trả về WalletTransaction để client không cần tự tạo nữa
                        return new Response(StatusType.SUCCESS, "Nạp tiền thành công!", wt);
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
                        double newBalance = bidder.getBalance() - amount;
                        bidder.setBalance(newBalance);
                        // FIX BUG B: Tạo WalletTransaction trên server và persist vào User object
                        WalletTransaction wt = new WalletTransaction(
                                WalletTransaction.Type.WITHDRAW, amount, newBalance);
                        bidder.addWalletTransaction(wt);
                        ServerApp.getUserDAO().saveDataToFile();
                        return new Response(StatusType.SUCCESS, "Rút tiền thành công!", wt);
                    } else if (user instanceof Seller seller) {
                        if (seller.getBalance() < amount) {
                            return new Response(StatusType.ERROR, "Số dư không đủ để rút tiền!", null);
                        }
                        double newBalance = seller.getBalance() - amount;
                        seller.setBalance(newBalance);
                        // FIX BUG B: Tạo WalletTransaction trên server và persist vào User object
                        WalletTransaction wt = new WalletTransaction(
                                WalletTransaction.Type.WITHDRAW, amount, newBalance);
                        seller.addWalletTransaction(wt);
                        ServerApp.getUserDAO().saveDataToFile();
                        return new Response(StatusType.SUCCESS, "Rút tiền thành công!", wt);
                    }
                    return new Response(StatusType.ERROR, "Không tìm thấy tài khoản.", null);
                } catch (Exception e) {
                    return new Response(StatusType.ERROR, "Lỗi khi rút tiền: " + e.getMessage(), null);
                }


                // ─── CẬP NHẬT HỒ SƠ ──────────────────────────────────────────────
            case UPDATE_PROFILE:
                try {
                    String profileData = (String) request.getPayload();
                    // Dùng limit -1 để giữ phần tử rỗng ở cuối (tránh split bỏ sót)
                    String[] parts = profileData.split("\\|", -1);
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
                    boolean emailUpdated = false;
                    if (!newEmail.isEmpty()) {
                        user.setEmail(newEmail);
                        emailUpdated = true;
                    }
                    if (!newPassword.isEmpty()) {
                        user.setPassWord(user.hashPasswordPublic(newPassword));
                    }
                    ServerApp.getUserDAO().saveDataToFile();
                    String successMsg = emailUpdated
                            ? "Thay đổi email thành công!"
                            : "Đổi mật khẩu thành công!";
                    return new Response(StatusType.SUCCESS, successMsg, user);
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