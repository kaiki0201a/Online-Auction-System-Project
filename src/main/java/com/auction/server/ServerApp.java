package com.auction.server;

import com.auction.dao.impl.AuctionDAOImpl;
import com.auction.dao.impl.UserDAOImpl;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.protocol.Response;
import com.auction.protocol.StatusType;
import com.auction.utils.AuctionManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * ServerApp — Main server với auto-live scheduler và auto-close trigger.
 *
 * FIXES THỰC HIỆN:
 * 1. scheduleAutoClose(auction): gọi ngay sau khi approve nếu auction RUNNING
 *    → auction tự kết thúc đúng giờ và broadcast AUCTION_ENDED
 * 2. scheduleApprovedToLive(auction): dùng cho auction APPROVED có startTime tương lai
 *    → chờ đến startTime → set RUNNING → broadcast AUCTION_WENT_LIVE
 * 3. autoLiveChecker: ScheduledExecutorService chạy mỗi 30s
 *    → kiểm tra APPROVED auctions có đến giờ chưa → set RUNNING
 * 4. Settlement: tự động settle khi auction kết thúc (chuyển tiền seller)
 */
public class ServerApp {

    private static final int PORT = 8888;

    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static final Map<String, List<ClientHandler>> auctionViewers = new ConcurrentHashMap<>();

    private static final AuctionDAOImpl auctionDAO = new AuctionDAOImpl();
    private static final UserDAOImpl userDAO = new UserDAOImpl();

    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    // FIX BUG #2: Scheduler cho auto-live và auto-close
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    // FIX BUG #2: Map lưu ScheduledFuture cho từng auction để cancel + reschedule khi endTime đổi
    private static final ConcurrentHashMap<String, ScheduledFuture<?>> autoCloseTasks = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("🚀 Đang khởi động Server Đấu Giá...");

        AuctionManager.getInstance().setAuctionDAO(auctionDAO);

        // 1. Tải dữ liệu từ file vào RAM
        userDAO.loadDataFromFile();
        auctionDAO.loadDataFromFile();
        System.out.println("📦 Dữ liệu đã được nạp lên bộ nhớ hoàn tất.");

        // FIX: Sau khi load, schedule auto-close cho các RUNNING auctions còn lại
        for (Auction a : AuctionManager.getInstance().getAllAuctions()) {
            if (a.getStatus() == AuctionStatus.RUNNING || a.getStatus() == AuctionStatus.OPEN) {
                scheduleAutoClose(a);
            } else if (a.getStatus() == AuctionStatus.APPROVED) {
                scheduleApprovedToLive(a);
            }
        }

        // 2. Seed dữ liệu mẫu nếu chưa có
        if (AuctionManager.getInstance().getAllAuctions().isEmpty()) {
            System.out.println("⚠️ Không có dữ liệu cũ, đang tạo sản phẩm mẫu để demo...");
            try {
                com.auction.model.Seller demoSeller = (com.auction.model.Seller)
                        com.auction.utils.UserManager.getInstance().getUser("seller");
                if (demoSeller == null) {
                    demoSeller = new com.auction.model.Seller("seller", "123", "seller@gmail.com");
                    com.auction.utils.UserManager.getInstance().addUser(demoSeller);
                }

                com.auction.model.Art art = new com.auction.model.Art(
                    "Tranh Đêm Đầy Sao (Phiên bản kỹ thuật số)", "Tác phẩm nổi tiếng của Van Gogh.",
                    1500.0, "Vincent van Gogh", 1889);
                Auction auction1 = AuctionManager.getInstance().createAuction(
                    art, demoSeller, LocalDateTime.now(), LocalDateTime.now().plusDays(2));
                auction1.setStatus(AuctionStatus.RUNNING);
                AuctionManager.getInstance().updateAuction(auction1);
                scheduleAutoClose(auction1);

                com.auction.model.Electronics elec = new com.auction.model.Electronics(
                    "MacBook Pro M3 Max 16-inch", "Chip M3 Max, 48GB RAM, 1TB SSD, màu Space Black.",
                    3500.0, "Apple", 12);
                Auction auction2 = AuctionManager.getInstance().createAuction(
                    elec, demoSeller, LocalDateTime.now(), LocalDateTime.now().plusDays(1));
                auction2.setStatus(AuctionStatus.RUNNING);
                AuctionManager.getInstance().updateAuction(auction2);
                scheduleAutoClose(auction2);

                com.auction.model.Vehicle car = new com.auction.model.Vehicle(
                    "Ferrari 488 GTB 2022", "Siêu xe Ferrari 488 GTB, màu đỏ Rosso Corsa.",
                    85000.0, "V8 Turbo", 12500.0);
                AuctionManager.getInstance().createAuction(
                    car, demoSeller, LocalDateTime.now(), LocalDateTime.now().plusDays(5));

                System.out.println("✅ Đã tạo 3 sản phẩm mẫu (2 RUNNING + 1 PENDING_APPROVAL)");
            } catch (Exception e) {
                System.err.println("❌ Lỗi tạo hàng mẫu: " + e.getMessage());
            }
        }

        // 3. FIX: Auto-live checker — chạy mỗi 30 giây để chuyển APPROVED → RUNNING khi đến giờ
        scheduler.scheduleAtFixedRate(() -> {
            try {
                boolean anyChange = false;
                for (Auction a : AuctionManager.getInstance().getAllAuctions()) {
                    if (a.getStatus() == AuctionStatus.APPROVED) {
                        LocalDateTime now = LocalDateTime.now();
                        if (a.getStartTime() != null && !a.getStartTime().isAfter(now)) {
                            a.setStatus(AuctionStatus.RUNNING);
                            AuctionManager.getInstance().updateAuction(a);
                            scheduleAutoClose(a);
                            anyChange = true;
                            System.out.println("🔴 [AUTO-LIVE] Phiên bắt đầu: " + a.getItem().getNameItem());
                        }
                    }
                }
                if (anyChange) {
                    auctionDAO.saveDataToFile();
                    broadcastAuctionUpdate(AuctionManager.getInstance().getAllAuctions(), "AUCTION_WENT_LIVE");
                }
            } catch (Exception e) {
                System.err.println("⚠️ Lỗi auto-live checker: " + e.getMessage());
            }
        }, 10, 30, TimeUnit.SECONDS);

        // 4. Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n⚠️ Server đang tắt. Tiến hành lưu toàn bộ dữ liệu...");
            userDAO.saveDataToFile();
            auctionDAO.saveDataToFile();
            scheduler.shutdown();
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) threadPool.shutdownNow();
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
            }
            System.out.println("💾 Đã lưu dữ liệu an toàn xuống file.");
        }));

        // 5. Bắt đầu lắng nghe kết nối
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("✅ Server đang lắng nghe tại cổng " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("👋 Có khách mới kết nối: " + clientSocket.getInetAddress());

                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler);
                threadPool.execute(handler);
            }

        } catch (IOException e) {
            System.err.println("❌ Lỗi mạng Server: " + e.getMessage());
        }
    }

    /**
     * FIX BUG #2: Lên lịch tự động kết thúc phiên.
     * Nếu đã có task cũ cho auction này → cancel trước rồi mới schedule mới.
     * Đảm bảo khi anti-sniping/autobid kéo dài endTime, task cũ bị hủy,
     * task mới sẽ fire đúng theo endTime mới.
     */
    public static void scheduleAutoClose(Auction auction) {
        if (auction.getEndTime() == null) return;

        long secondsUntilEnd = java.time.temporal.ChronoUnit.SECONDS.between(
                LocalDateTime.now(), auction.getEndTime());

        if (secondsUntilEnd <= 0) {
            // Đã hết giờ rồi — kết thúc ngay
            finishAuction(auction);
            return;
        }

        // FIX: Cancel task cũ nếu có (tránh double-finish khi reschedule)
        ScheduledFuture<?> oldTask = autoCloseTasks.get(auction.getAuctionId());
        if (oldTask != null && !oldTask.isDone()) {
            oldTask.cancel(false);
            System.out.println("🔁 [AUTO-CLOSE] Hủy lịch cũ cho \"" + auction.getItem().getNameItem()
                + "\" — reschedule theo endTime mới.");
        }

        ScheduledFuture<?> newTask = scheduler.schedule(() -> {
            try {
                finishAuction(auction);
            } catch (Exception e) {
                System.err.println("❌ Lỗi auto-close: " + e.getMessage());
            }
        }, secondsUntilEnd, TimeUnit.SECONDS);

        // Lưu task mới vào map
        autoCloseTasks.put(auction.getAuctionId(), newTask);

        System.out.println("⏰ [AUTO-CLOSE] Phiên \"" + auction.getItem().getNameItem() +
            "\" sẽ kết thúc sau " + secondsUntilEnd + " giây.");
    }

    /**
     * FIX: Lên lịch chuyển APPROVED → RUNNING khi đến startTime.
     * Dành cho auction đã duyệt nhưng chưa đến giờ bắt đầu.
     */
    public static void scheduleApprovedToLive(Auction auction) {
        if (auction.getStartTime() == null) return;

        long secondsUntilStart = java.time.temporal.ChronoUnit.SECONDS.between(
                LocalDateTime.now(), auction.getStartTime());

        if (secondsUntilStart <= 0) {
            // Đã đến giờ rồi — chuyển RUNNING ngay
            auction.setStatus(AuctionStatus.RUNNING);
            AuctionManager.getInstance().updateAuction(auction);
            scheduleAutoClose(auction);
            return;
        }

        scheduler.schedule(() -> {
            try {
                if (auction.getStatus() == AuctionStatus.APPROVED) {
                    auction.setStatus(AuctionStatus.RUNNING);
                    AuctionManager.getInstance().updateAuction(auction);
                    auctionDAO.saveDataToFile();
                    scheduleAutoClose(auction);
                    broadcastAuctionUpdate(AuctionManager.getInstance().getAllAuctions(), "AUCTION_WENT_LIVE");
                    System.out.println("🔴 [AUTO-LIVE] \"" + auction.getItem().getNameItem() + "\" bắt đầu!");
                }
            } catch (Exception e) {
                System.err.println("❌ Lỗi scheduleApprovedToLive: " + e.getMessage());
            }
        }, secondsUntilStart, TimeUnit.SECONDS);

        System.out.println("📅 [SCHEDULE] Phiên \"" + auction.getItem().getNameItem() +
            "\" sẽ bắt đầu sau " + secondsUntilStart + " giây.");
    }

    /**
     * FIX: Kết thúc phiên đấu giá, settle tiền, broadcast AUCTION_ENDED.
     * Trước đây settleAuction() tồn tại nhưng KHÔNG BAO GIỜ được gọi.
     */
    private static synchronized void finishAuction(Auction auction) {
        // Kiểm tra tránh double-finish
        if (auction.getStatus() == AuctionStatus.FINISHED ||
            auction.getStatus() == AuctionStatus.PAID ||
            auction.getStatus() == AuctionStatus.CANCELED) {
            return;
        }

        auction.setStatus(AuctionStatus.FINISHED);
        System.out.println("⏰ [AUTO-CLOSE] Phiên \"" + auction.getItem().getNameItem() + "\" kết thúc!");

        // FIX: Settle tiền — chuyển từ bidder sang seller
        auction.settleAuction();

        AuctionManager.getInstance().updateAuction(auction);
        auctionDAO.saveDataToFile();
        userDAO.saveDataToFile();

        // Broadcast với toàn bộ list
        broadcastAuctionUpdate(AuctionManager.getInstance().getAllAuctions(), "AUCTION_ENDED");

        // FIX BUG #2: Sau khi settle, broadcast balance mới cho Seller để client cập nhật UI
        // Message format: "SELLER_BALANCE_UPDATE|<username>" để client filter đúng người
        if (auction.getHighestBidder() != null && auction.getSeller() != null) {
            com.auction.model.Seller seller = auction.getSeller();
            String sellerMsg = "SELLER_BALANCE_UPDATE|" + seller.getUserName();
            broadcast(new com.auction.protocol.Response(
                com.auction.protocol.StatusType.SUCCESS,
                sellerMsg,
                seller.getBalance()
            ));
            System.out.println("💰 [SETTLEMENT BROADCAST] Seller " + seller.getUserName()
                + " số dư mới: " + seller.getBalance());
        } else if (auction.getHighestBidder() == null && auction.getSeller() != null) {
            // BUG #3 FIX: Phiên kết thúc không có người mua — thông báo cho Seller
            String nobuyer = "AUCTION_NO_BUYER|" + auction.getSeller().getUserName()
                + "|" + auction.getItem().getNameItem();
            broadcast(new com.auction.protocol.Response(
                com.auction.protocol.StatusType.SUCCESS,
                nobuyer,
                null
            ));
            System.out.println("📭 [NO BUYER] Phiên \"" + auction.getItem().getNameItem()
                + "\" kết thúc không có người mua. Đã thông báo Seller.");
        }
    }

    // ─── BROADCAST HELPERS ────────────────────────────────────────────────────

    public static void broadcast(Response response) {
        for (ClientHandler client : clients) {
            client.sendResponse(response);
        }
    }

    public static void broadcastAuctionUpdate(Object updatedData, String message) {
        Response response = new Response(StatusType.SUCCESS, message, updatedData);
        for (ClientHandler client : clients) {
            client.sendResponse(response);
        }
    }

    public static void joinAuctionView(String auctionId, ClientHandler client) {
        auctionViewers.putIfAbsent(auctionId, new CopyOnWriteArrayList<>());
        auctionViewers.get(auctionId).add(client);
    }

    public static void leaveAuctionView(String auctionId, ClientHandler client) {
        if (auctionViewers.containsKey(auctionId)) {
            auctionViewers.get(auctionId).remove(client);
        }
    }

    public static void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        for (List<ClientHandler> viewers : auctionViewers.values()) {
            viewers.remove(clientHandler);
        }
        System.out.println("📉 Đã ngắt kết nối một client. Tổng số client: " + clients.size());
    }

    public static AuctionDAOImpl getAuctionDAO() { return auctionDAO; }
    public static UserDAOImpl getUserDAO()       { return userDAO; }
}