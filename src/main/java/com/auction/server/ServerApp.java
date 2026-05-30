package com.auction.server;

import com.auction.dao.impl.AuctionDAOImpl;
import com.auction.dao.impl.UserDAOImpl;
import com.auction.model.Auction;
import com.auction.model.AuctionStatus;
import com.auction.model.Bidder;
import com.auction.model.BidTransaction;
import com.auction.model.Seller;
import com.auction.model.User;
import com.auction.protocol.Response;
import com.auction.protocol.StatusType;
import com.auction.utils.AuctionManager;
import com.auction.utils.UserManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * ServerApp — Main server với auto-live scheduler và auto-close trigger.
 *
 * REFACTOR (không đổi behavior):
 * - main() tách thành seedDemoData() và scheduleRunningAuctions().
 * - finishAuction() tách thành syncSellerObject(), broadcastWinnerSettlement(), broadcastNoWinnerNotice().
 */
public class ServerApp {

    private static final int PORT = 8888;

    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static final Map<String, List<ClientHandler>> auctionViewers = new ConcurrentHashMap<>();

    private static final AuctionDAOImpl auctionDAO = new AuctionDAOImpl();
    private static final UserDAOImpl userDAO = new UserDAOImpl();

    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    // Scheduler cho auto-live và auto-close
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    // Map lưu ScheduledFuture cho từng auction để cancel + reschedule khi endTime đổi
    private static final ConcurrentHashMap<String, ScheduledFuture<?>> autoCloseTasks = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("🚀 Đang khởi động Server Đấu Giá...");

        AuctionManager.getInstance().setAuctionDAO(auctionDAO);

        // 1. Tải dữ liệu từ file vào RAM
        userDAO.loadDataFromFile();
        auctionDAO.loadDataFromFile();
        System.out.println("📦 Dữ liệu đã được nạp lên bộ nhớ hoàn tất.");

        // 2. Sau khi load, schedule auto-close/auto-live cho các auction còn hiệu lực
        scheduleRunningAuctions();

        // 3. Seed dữ liệu mẫu nếu chưa có
        if (AuctionManager.getInstance().getAllAuctions().isEmpty()) {
            System.out.println("⚠️ Không có dữ liệu cũ, đang tạo sản phẩm mẫu để demo...");
            seedDemoData();
        }

        // 4. Auto-live checker — chạy mỗi 30 giây để chuyển APPROVED → RUNNING khi đến giờ
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

        // 5. Shutdown hook
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

        // 6. Bắt đầu lắng nghe kết nối
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
     * Extract: Lên lịch auto-close và auto-live cho tất cả auction đang hoạt động sau khi load.
     * Tách từ main() để tăng readability.
     */
    private static void scheduleRunningAuctions() {
        for (Auction a : AuctionManager.getInstance().getAllAuctions()) {
            if (a.getStatus() == AuctionStatus.RUNNING || a.getStatus() == AuctionStatus.OPEN) {
                scheduleAutoClose(a);
            } else if (a.getStatus() == AuctionStatus.APPROVED) {
                scheduleApprovedToLive(a);
            }
        }
    }

    /**
     * Extract: Tạo dữ liệu mẫu khi server khởi động lần đầu tiên (không có file dữ liệu).
     * Tách từ main() để tăng readability.
     */
    private static void seedDemoData() {
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

    /**
     * Lên lịch tự động kết thúc phiên.
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

        // Cancel task cũ nếu có (tránh double-finish khi reschedule)
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

        System.out.println("⏰ [AUTO-CLOSE] Phiên \"" + auction.getItem().getNameItem()
            + "\" sẽ kết thúc sau " + secondsUntilEnd + " giây.");
    }

    /**
     * Lên lịch chuyển APPROVED → RUNNING khi đến startTime.
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

        System.out.println("📅 [SCHEDULE] Phiên \"" + auction.getItem().getNameItem()
            + "\" sẽ bắt đầu sau " + secondsUntilStart + " giây.");
    }

    /**
     * Kết thúc phiên đấu giá, settle tiền, broadcast AUCTION_ENDED.
     */
    private static synchronized void finishAuction(Auction auction) {
        // Kiểm tra tránh double-finish
        if (auction.getStatus() == AuctionStatus.FINISHED
            || auction.getStatus() == AuctionStatus.PAID
            || auction.getStatus() == AuctionStatus.CANCELED) {
            return;
        }

        auction.setStatus(AuctionStatus.FINISHED);
        System.out.println("⏰ [AUTO-CLOSE] Phiên \"" + auction.getItem().getNameItem() + "\" kết thúc!");

        // Sync Seller object từ UserManager trước khi settle.
        // Sau khi deserialize Auction từ file, auction.getSeller() là bản COPY riêng biệt,
        // không phải tham chiếu đến object trong UserManager.
        // Nếu không sync, settleAuction() sẽ cộng tiền vào bản copy — userDAO.saveDataToFile()
        // sẽ lưu balance cũ từ UserManager, không phải balance mới.
        syncSellerObject(auction);

        // Settle tiền — chuyển từ bidder sang seller
        auction.settleAuction();

        AuctionManager.getInstance().updateAuction(auction);
        auctionDAO.saveDataToFile();
        userDAO.saveDataToFile();

        // Broadcast với toàn bộ list
        broadcastAuctionUpdate(AuctionManager.getInstance().getAllAuctions(), "AUCTION_ENDED");

        if (auction.getHighestBidder() != null && auction.getSeller() != null) {
            broadcastWinnerSettlement(auction);
        } else if (auction.getHighestBidder() == null && auction.getSeller() != null) {
            broadcastNoWinnerNotice(auction);
        }
    }

    /**
     * Extract: Đồng bộ Seller object từ UserManager vào Auction trước khi settle.
     * Giải quyết vấn đề desync sau restart server khi Auction được deserialize từ file.
     */
    private static void syncSellerObject(Auction auction) {
        Seller auctionSeller = auction.getSeller();
        if (auctionSeller == null) return;

        User managedSellerUser = UserManager.getInstance().getUser(auctionSeller.getUserName());
        if (managedSellerUser instanceof Seller managedSeller
                && managedSeller != auctionSeller) {
            // Hai object khác nhau (desync) — đồng bộ balance hiện tại từ UserManager
            // vào Auction's seller để settleAuction() cộng đúng vào object được quản lý
            auction.setSeller(managedSeller);
            System.out.println("🔄 [SYNC] Đã đồng bộ Seller object '" + managedSeller.getUserName()
                + "' từ UserManager vào Auction (tránh desync sau restart).");
        }
    }

    /**
     * Extract: Broadcast kết quả settlement sau khi phiên có người thắng.
     * Gửi cập nhật balance cho Seller, AuctionEarning, và tất cả Bidder thua cuộc.
     */
    private static void broadcastWinnerSettlement(Auction auction) {
        Seller seller = auction.getSeller();

        // Broadcast balance mới cho Seller
        String sellerMsg = "SELLER_BALANCE_UPDATE|" + seller.getUserName();
        broadcast(new Response(
            StatusType.SUCCESS,
            sellerMsg,
            seller.getBalance()
        ));
        System.out.println("💰 [SETTLEMENT BROADCAST] Seller " + seller.getUserName()
            + " số dư mới: " + seller.getBalance());

        // Broadcast AuctionEarning để client sync lịch sử nhận tiền ngay lập tức
        if (!seller.getEarningHistory().isEmpty()) {
            com.auction.model.AuctionEarning latestEarning = seller.getEarningHistory().get(0);
            String earningMsg = "SELLER_EARNING_UPDATE|" + seller.getUserName();
            broadcast(new Response(
                StatusType.SUCCESS,
                earningMsg,
                latestEarning
            ));
            System.out.println("📋 [EARNING BROADCAST] Seller " + seller.getUserName()
                + " nhận earning: " + latestEarning.getItemName());
        }

        // Broadcast balance update cho TẤT CẢ bidders đã tham gia (trừ winner).
        // Trong quá trình đấu giá, mỗi khi bị vượt giá, highestBidder cũ đã được hoàn tiền
        // trong RAM server, nhưng client chưa nhận được balance update sau khi phiên kết thúc.
        broadcastLoserBalances(auction);
    }

    /**
     * Extract: Broadcast cập nhật balance cho các bidder thua cuộc.
     */
    private static void broadcastLoserBalances(Auction auction) {
        String winnerName = auction.getHighestBidder().getUserName();
        Set<String> notifiedBidders = new HashSet<>();
        notifiedBidders.add(winnerName); // Winner đã nhận balance qua PLACE_BID response

        for (BidTransaction bt : auction.getBidHistory()) {
            String bidderName = bt.getBidder().getUserName();
            if (notifiedBidders.contains(bidderName)) continue;
            notifiedBidders.add(bidderName);

            // Lấy bidder từ UserManager (đảm bảo lấy object đúng)
            User managedUser = UserManager.getInstance().getUser(bidderName);
            if (managedUser instanceof Bidder loser) {
                String loserMsg = "BIDDER_BALANCE_UPDATE|" + bidderName;
                broadcast(new Response(
                    StatusType.SUCCESS,
                    loserMsg,
                    loser.getBalance()
                ));
                System.out.println("💸 [LOSER BALANCE BROADCAST] Bidder " + bidderName
                    + " số dư sau phiên: " + loser.getBalance());
            }
        }
    }

    /**
     * Extract: Broadcast thông báo khi phiên kết thúc mà không có người mua.
     */
    private static void broadcastNoWinnerNotice(Auction auction) {
        String nobuyer = "AUCTION_NO_BUYER|" + auction.getSeller().getUserName()
            + "|" + auction.getItem().getNameItem();
        broadcast(new Response(
            StatusType.SUCCESS,
            nobuyer,
            null
        ));
        System.out.println("📭 [NO BUYER] Phiên \"" + auction.getItem().getNameItem()
            + "\" kết thúc không có người mua. Đã thông báo Seller.");
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