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
 * REFACTOR (không đổi behavior):
 * - processRequest() được tách thành 14 private handler method riêng biệt.
 * - DEPOSIT / WITHDRAW dùng chung processWalletOperation() — loại bỏ duplicate code.
 * - BAN cascade tách thành cascadeBanBidder() / cascadeBanSeller().
 * - SET_AUTOBID tách thành enableAutoBid() / disableAutoBid().
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

    // ─── DISPATCHER ───────────────────────────────────────────────────────────

    private Response processRequest(Request request) {
        ActionType action = request.getAction();
        return switch (action) {
            case LOGIN            -> handleLogin(request);
            case REGISTER         -> handleRegister(request);
            case PLACE_BID        -> handlePlaceBid(request);
            case CREATE_AUCTION   -> handleCreateAuction(request);
            case GET_AUCTION_LIST -> handleGetAuctionList();
            case GET_USER_LIST    -> handleGetUserList();
            case BAN_USER         -> handleBanUser(request);
            case CANCEL_AUCTION   -> handleCancelAuction(request);
            case APPROVE_AUCTION  -> handleApproveAuction(request);
            case REJECT_AUCTION   -> handleRejectAuction(request);
            case SET_AUTOBID      -> handleSetAutoBid(request);
            case DEPOSIT          -> handleDeposit(request);
            case WITHDRAW         -> handleWithdraw(request);
            case UPDATE_PROFILE   -> handleUpdateProfile(request);
            case LOGOUT           -> handleLogout();
            default -> new Response(StatusType.ERROR, "Server không hỗ trợ hành động này.", null);
        };
    }

    // ─── ĐĂNG NHẬP ────────────────────────────────────────────────────────────

    private Response handleLogin(Request request) {
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
    }

    // ─── ĐĂNG KÝ ──────────────────────────────────────────────────────────────

    private Response handleRegister(Request request) {
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
    }

    // ─── ĐẶT GIÁ ──────────────────────────────────────────────────────────────

    private Response handlePlaceBid(Request request) {
        try {
            BidPayload bidData = (BidPayload) request.getPayload();

            Auction auction = AuctionManager.getInstance().getAuctionById(bidData.getAuctionId());
            if (auction == null) {
                return new Response(StatusType.ERROR, "Phiên đấu giá không tồn tại.", null);
            }

            // Cho phép đặt giá khi RUNNING hoặc APPROVED (đã được duyệt)
            if (auction.getStatus() != AuctionStatus.RUNNING
                    && auction.getStatus() != AuctionStatus.OPEN
                    && auction.getStatus() != AuctionStatus.APPROVED) {
                return new Response(StatusType.ERROR,
                        "Phiên đấu giá không ở trạng thái có thể đặt giá. Trạng thái: " + auction.getStatus(), null);
            }

            Bidder realBidder = (Bidder) UserManager.getInstance().getUser(bidData.getUsername());
            if (realBidder == null) {
                return new Response(StatusType.ERROR, "Tài khoản không hợp lệ.", null);
            }

            // Áp dụng Strategy Pattern: Manual Bid
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
    }

    // ─── TẠO PHIÊN ĐẤU GIÁ ────────────────────────────────────────────────────

    private Response handleCreateAuction(Request request) {
        try {
            Auction newAuction = (Auction) request.getPayload();

            // Thêm vào RAM list trước khi broadcast
            AuctionManager.getInstance().getAllAuctions().add(newAuction);
            // Flush file (RAM đã có newAuction rồi)
            ServerApp.getAuctionDAO().saveDataToFile();

            // Lấy toàn bộ list (đã bao gồm newAuction) để broadcast
            List<Auction> updatedList = AuctionManager.getInstance().getAllAuctions();

            // Broadcast với data = List<Auction> — client nhận và renderInventory ngay
            ServerApp.broadcastAuctionUpdate(updatedList, "AUCTION_CREATED");

            // Trả về List (không phải single Auction) để client filter ngay
            System.out.println("📦 [SERVER] Auction mới tạo: " + newAuction.getItem().getNameItem()
                    + " | Tổng: " + updatedList.size() + " phiên");
            return new Response(StatusType.SUCCESS, "AUCTION_CREATED", updatedList);

        } catch (Exception e) {
            System.err.println("Lỗi khi tạo phiên đấu giá: " + e.getMessage());
            return new Response(StatusType.ERROR, "Lỗi hệ thống khi lưu sản phẩm.", null);
        }
    }

    // ─── LẤY DANH SÁCH PHIÊN ──────────────────────────────────────────────────

    private Response handleGetAuctionList() {
        return new Response(StatusType.SUCCESS, "Danh sách đấu giá",
                AuctionManager.getInstance().getAllAuctions());
    }

    // ─── LẤY DANH SÁCH USER ───────────────────────────────────────────────────

    private Response handleGetUserList() {
        return new Response(StatusType.SUCCESS, "Danh sách User",
                UserManager.getInstance().getAllUsers());
    }

    // ─── BAN / UNBAN USER ─────────────────────────────────────────────────────

    private Response handleBanUser(Request request) {
        try {
            String targetUsername = (String) request.getPayload();
            User targetUser = UserManager.getInstance().getUser(targetUsername);

            if (targetUser == null)
                return new Response(StatusType.ERROR, "Không tìm thấy User.", null);

            // Không cho phép ban tài khoản Admin
            if (targetUser instanceof Admin)
                return new Response(StatusType.ERROR, "Không thể khóa tài khoản Admin!", null);

            boolean wasBanned = targetUser.isBanned();
            targetUser.setBanned(!wasBanned);
            ServerApp.getUserDAO().saveDataToFile();
            String act = targetUser.isBanned() ? "khóa" : "mở khóa";

            // Khi KHÓA → cascade + FORCE_LOGOUT (áp dụng cho cả Bidder lẫn Seller)
            if (targetUser.isBanned()) {
                if (targetUser instanceof Bidder bannedBidder) {
                    cascadeBanBidder(bannedBidder);
                } else if (targetUser instanceof Seller bannedSeller) {
                    cascadeBanSeller(bannedSeller);
                }

                // FORCE_LOGOUT áp dụng cho cả Bidder và Seller
                ServerApp.broadcast(new Response(
                        StatusType.SUCCESS,
                        "FORCE_LOGOUT|" + targetUsername,
                        null
                ));
            }

            // Trả về updated user list thay vì null → AdminController refresh ngay
            List<User> updatedUsers = UserManager.getInstance().getAllUsers();
            return new Response(StatusType.SUCCESS,
                    "Đã " + act + " tài khoản " + targetUsername + "!", updatedUsers);

        } catch (Exception e) {
            return new Response(StatusType.ERROR, "Lỗi khi xử lý Ban/Unban.", null);
        }
    }

    /**
     * Extract: Cascade ban cho Bidder — hủy tất cả phiên mà bidder đang dẫn đầu.
     */
    private void cascadeBanBidder(Bidder bannedBidder) {
        // Hủy/reset TẤT CẢ phiên mà bidder đã tham gia (qua bidHistory)
        List<Auction> allAuctions = AuctionManager.getInstance().getAllAuctions();
        for (Auction a : allAuctions) {
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
    }

    /**
     * Extract: Cascade ban cho Seller — hủy tất cả phiên PENDING/APPROVED của seller.
     */
    private void cascadeBanSeller(Seller bannedSeller) {
        // Seller bị ban → hủy các phiên PENDING/APPROVED của seller đó
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

    // ─── HỦY PHIÊN ĐẤU GIÁ ───────────────────────────────────────────────────

    private Response handleCancelAuction(Request request) {
        try {
            String targetAuctionId = (String) request.getPayload();
            Auction auctionToCancel = AuctionManager.getInstance().getAuctionById(targetAuctionId);

            if (auctionToCancel != null) {
                // Hoàn tiền cho bidder đang thắng (nếu có) trước khi hủy
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
                    ServerApp.broadcast(new Response(
                            StatusType.SUCCESS,
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
    }

    // ─── ADMIN DUYỆT SẢN PHẨM ────────────────────────────────────────────────

    private Response handleApproveAuction(Request request) {
        try {
            String approveId = (String) request.getPayload();
            Auction toApprove = AuctionManager.getInstance().getAuctionById(approveId);

            if (toApprove == null)
                return new Response(StatusType.ERROR, "Không tìm thấy phiên.", null);
            if (toApprove.getStatus() != AuctionStatus.PENDING_APPROVAL)
                return new Response(StatusType.ERROR, "Phiên này không ở trạng thái chờ duyệt.", null);

            // Không duyệt phiên đã hết hạn
            LocalDateTime now = LocalDateTime.now();
            if (toApprove.getEndTime() != null && toApprove.getEndTime().isBefore(now)) {
                return new Response(StatusType.ERROR,
                        "❌ Không thể duyệt! Phiên đã hết hạn vào "
                                + toApprove.getEndTime().format(
                                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                                + ".\nVui lòng từ chối phiên này.", null);
            }

            // Auto-LIVE nếu startTime <= now, còn lại set APPROVED (chờ đến giờ)
            if (toApprove.getStartTime() == null || !toApprove.getStartTime().isAfter(now)) {
                // Bắt đầu ngay → RUNNING
                toApprove.setStatus(AuctionStatus.RUNNING);
                // Trigger scheduleAutoClose để auction tự kết thúc đúng giờ
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

            // Broadcast với toàn bộ List<Auction> — client filter nhất quán
            List<Auction> allAfterApprove = AuctionManager.getInstance().getAllAuctions();
            ServerApp.broadcastAuctionUpdate(allAfterApprove, "AUCTION_APPROVED");

            return new Response(StatusType.SUCCESS, "DUYỆT_OK|" + approveId, allAfterApprove);

        } catch (Exception e) {
            return new Response(StatusType.ERROR, "Lỗi khi duyệt: " + e.getMessage(), null);
        }
    }

    // ─── ADMIN TỪ CHỐI SẢN PHẨM ──────────────────────────────────────────────

    private Response handleRejectAuction(Request request) {
        try {
            String rejectId = (String) request.getPayload();
            Auction toReject = AuctionManager.getInstance().getAuctionById(rejectId);

            if (toReject == null)
                return new Response(StatusType.ERROR, "Không tìm thấy phiên.", null);

            // Set REJECTED thay vì CANCELED — seller thấy đúng lý do
            toReject.setStatus(AuctionStatus.REJECTED);
            AuctionManager.getInstance().updateAuction(toReject);
            ServerApp.getAuctionDAO().saveDataToFile();

            // Broadcast với List<Auction>
            List<Auction> allAfterReject = AuctionManager.getInstance().getAllAuctions();
            ServerApp.broadcastAuctionUpdate(allAfterReject, "AUCTION_REJECTED");

            // Gửi thêm thông báo riêng cho Seller để biết phiên bị từ chối
            String sellerNotify = "AUCTION_REJECTED_SELLER|" + toReject.getSeller().getUserName()
                    + "|" + toReject.getItem().getNameItem();
            ServerApp.broadcast(new Response(StatusType.SUCCESS, sellerNotify, null));

            System.out.println("❌ [ADMIN] Đã từ chối: " + toReject.getItem().getNameItem());
            return new Response(StatusType.SUCCESS, "TỪ_CHỐI_OK|" + rejectId, allAfterReject);

        } catch (Exception e) {
            return new Response(StatusType.ERROR, "Lỗi khi từ chối: " + e.getMessage(), null);
        }
    }

    // ─── CÀI ĐẶT AUTOBID ──────────────────────────────────────────────────────

    private Response handleSetAutoBid(Request request) {
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
                return enableAutoBid(abAuction, abBidder, abPayload);
            } else {
                return disableAutoBid(abAuction, abBidder);
            }

        } catch (AuctionException e) {
            return new Response(StatusType.ERROR, "AUTOBID_ERROR: " + e.getMessage(), null);
        } catch (Exception e) {
            return new Response(StatusType.ERROR, "AUTOBID_ERROR: Lỗi hệ thống khi xử lý AutoBid.", null);
        }
    }

    /**
     * Extract: Bật AutoBid — đăng ký rule và trigger ngay nếu bidder không dẫn đầu.
     */
    private Response enableAutoBid(Auction auction, Bidder bidder, AutoBidPayload payload)
            throws AuctionException {
        // BẬT AutoBid: đăng ký rule vào phiên
        auction.registerAutoBid(bidder, payload.getMaxAmount(), payload.getIncrementAmount());

        // Trigger autobid NGAY nếu bidder không phải dẫn đầu
        // Không cần chờ người khác bid mới autobid mới kích hoạt
        if (auction.getHighestBidder() == null
                || !auction.getHighestBidder().getUserName().equals(bidder.getUserName())) {
            auction.triggerAutoBidsPublic();
            // Cập nhật lại bidder balance sau khi autobid
            ServerApp.getUserDAO().saveDataToFile();
            // Broadcast update giá mới
            List<Auction> afterAutoBid = AuctionManager.getInstance().getAllAuctions();
            ServerApp.broadcastAuctionUpdate(afterAutoBid, "UPDATE_AUCTION");
            System.out.println("🤖 [AUTOBID IMMEDIATE] Triggered for "
                    + bidder.getUserName() + " on " + auction.getItem().getNameItem());
        }

        // Flush dữ liệu xuống file
        ServerApp.getAuctionDAO().saveDataToFile();

        System.out.println("🤖 [AUTOBID BẬT] " + bidder.getUserName()
                + " | Max: " + payload.getMaxAmount()
                + " | Bước: " + payload.getIncrementAmount()
                + " | Phiên: " + auction.getItem().getNameItem());

        return new Response(StatusType.SUCCESS, "AUTOBID_OK", "Đã bật AutoBid thành công!");
    }

    /**
     * Extract: Tắt AutoBid — deactivate toàn bộ rule của bidder này trong phiên.
     */
    private Response disableAutoBid(Auction auction, Bidder bidder) {
        if (auction.getAutoBidRules() != null) {
            auction.getAutoBidRules().stream()
                    .filter(r -> r.getBidder().getUserName().equals(bidder.getUserName()))
                    .forEach(r -> r.setActive(false));
        }

        ServerApp.getAuctionDAO().saveDataToFile();

        System.out.println("🤖 [AUTOBID TẮT] " + bidder.getUserName()
                + " | Phiên: " + auction.getItem().getNameItem());

        return new Response(StatusType.SUCCESS, "AUTOBID_OK", "Đã tắt AutoBid thành công!");
    }

    // ─── NẠP TIỀN ─────────────────────────────────────────────────────────────

    private Response handleDeposit(Request request) {
        try {
            String depositData = (String) request.getPayload();
            String[] parts = depositData.split("\\|");
            if (parts.length < 2) {
                return new Response(StatusType.ERROR, "Dữ liệu không hợp lệ.", null);
            }
            String targetUsername = parts[0];
            double amount = Double.parseDouble(parts[1]);

            User user = UserManager.getInstance().getUser(targetUsername);
            WalletTransaction wt = processWalletOperation(user, amount, WalletTransaction.Type.DEPOSIT);
            if (wt == null) {
                return new Response(StatusType.ERROR, "Không tìm thấy tài khoản.", null);
            }
            return new Response(StatusType.SUCCESS, "Nạp tiền thành công!", wt);
        } catch (Exception e) {
            return new Response(StatusType.ERROR, "Lỗi khi nạp tiền: " + e.getMessage(), null);
        }
    }

    // ─── RÚT TIỀN ─────────────────────────────────────────────────────────────

    private Response handleWithdraw(Request request) {
        try {
            String withdrawData = (String) request.getPayload();
            String[] parts = withdrawData.split("\\|");
            if (parts.length < 2) {
                return new Response(StatusType.ERROR, "Dữ liệu không hợp lệ.", null);
            }
            String targetUsername = parts[0];
            double amount = Double.parseDouble(parts[1]);

            User user = UserManager.getInstance().getUser(targetUsername);

            // Kiểm tra số dư trước khi thực hiện rút tiền
            if (user instanceof Bidder bidder && bidder.getBalance() < amount) {
                return new Response(StatusType.ERROR, "Số dư không đủ để rút tiền!", null);
            } else if (user instanceof Seller seller && seller.getBalance() < amount) {
                return new Response(StatusType.ERROR, "Số dư không đủ để rút tiền!", null);
            }

            WalletTransaction wt = processWalletOperation(user, amount, WalletTransaction.Type.WITHDRAW);
            if (wt == null) {
                return new Response(StatusType.ERROR, "Không tìm thấy tài khoản.", null);
            }
            return new Response(StatusType.SUCCESS, "Rút tiền thành công!", wt);
        } catch (Exception e) {
            return new Response(StatusType.ERROR, "Lỗi khi rút tiền: " + e.getMessage(), null);
        }
    }

    /**
     * Remove Duplication: Xử lý giao dịch ví chung cho cả Bidder và Seller.
     * Loại bỏ code trùng lặp giữa handleDeposit() và handleWithdraw().
     *
     * @param user   User cần thực hiện giao dịch (Bidder hoặc Seller)
     * @param amount Số tiền giao dịch (luôn là số dương)
     * @param type   DEPOSIT (nạp) hoặc WITHDRAW (rút)
     * @return WalletTransaction đã tạo, hoặc null nếu user không phải Bidder/Seller
     */
    private WalletTransaction processWalletOperation(User user, double amount, WalletTransaction.Type type) {
        double sign = (type == WalletTransaction.Type.DEPOSIT) ? 1.0 : -1.0;
        if (user instanceof Bidder bidder) {
            double newBalance = bidder.getBalance() + sign * amount;
            bidder.setBalance(newBalance);
            WalletTransaction wt = new WalletTransaction(type, amount, newBalance);
            bidder.addWalletTransaction(wt);
            ServerApp.getUserDAO().saveDataToFile();
            return wt;
        } else if (user instanceof Seller seller) {
            double newBalance = seller.getBalance() + sign * amount;
            seller.setBalance(newBalance);
            WalletTransaction wt = new WalletTransaction(type, amount, newBalance);
            seller.addWalletTransaction(wt);
            ServerApp.getUserDAO().saveDataToFile();
            return wt;
        }
        return null;
    }

    // ─── CẬP NHẬT HỒ SƠ ──────────────────────────────────────────────────────

    private Response handleUpdateProfile(Request request) {
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
    }

    // ─── ĐĂNG XUẤT ────────────────────────────────────────────────────────────

    private Response handleLogout() {
        return new Response(StatusType.SUCCESS, "Đã đăng xuất", null);
    }

    // ─── ĐÓNG KẾT NỐI ─────────────────────────────────────────────────────────

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