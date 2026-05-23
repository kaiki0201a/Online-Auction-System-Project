package com.auction.server;

import com.auction.dao.impl.AuctionDAOImpl;
import com.auction.dao.impl.UserDAOImpl;
import com.auction.protocol.AuctionListUpdate;
import com.auction.protocol.Response;
import com.auction.protocol.StatusType;
import com.auction.utils.AuctionManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ServerApp {
    private static final int PORT = 8888;

    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static final Map<String, List<ClientHandler>> auctionViewers = new ConcurrentHashMap<>();

    // DAO quản lý dữ liệu
    private static final AuctionDAOImpl auctionDAO = new AuctionDAOImpl();
    // 👉 THÊM MỚI: UserDAO để quản lý tài khoản
    private static final UserDAOImpl userDAO = new UserDAOImpl();

    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        System.out.println("🚀 Đang khởi động Server Đấu Giá...");

        // 👉 TIÊM DAO VÀO MANAGER ĐỂ TRÁNH LỖI DIP LÚC UNIT TEST
        AuctionManager.getInstance().setAuctionDAO(auctionDAO);

        // 1. TẢI DỮ LIỆU TỪ FILE VÀO RAM KHI KHỞI ĐỘNG
        userDAO.loadDataFromFile(); // 👉 THÊM MỚI: Tải tài khoản trước
        auctionDAO.loadDataFromFile();
        System.out.println("📦 Dữ liệu đã được nạp lên bộ nhớ hoàn tất.");

        // --- ĐOẠN CODE BƠM HÀNG MẪU ĐỂ TEST ---
        if (AuctionManager.getInstance().getAllAuctions().isEmpty()) {
            System.out.println("⚠️ Không có dữ liệu cũ, đang tạo sản phẩm mẫu để demo...");
            try {
                // Dùng seller đã được khởi tạo trong UserManager
                com.auction.model.Seller demoSeller = (com.auction.model.Seller)
                        com.auction.utils.UserManager.getInstance().getUser("seller");
                if (demoSeller == null) {
                    demoSeller = new com.auction.model.Seller("seller", "123", "seller@gmail.com");
                    com.auction.utils.UserManager.getInstance().addUser(demoSeller);
                }

                // Mẫu 1: Tranh nghệ thuật — đang chạy (RUNNING)
                com.auction.model.Art art = new com.auction.model.Art(
                    "Tranh Đêm Đầy Sao (Phiên bản kỹ thuật số)", "Tác phẩm nổi tiếng của Van Gogh, bản sao số cực nét.",
                    1500.0, "Vincent van Gogh", 1889);
                com.auction.model.Auction auction1 = AuctionManager.getInstance().createAuction(
                    art, demoSeller,
                    java.time.LocalDateTime.now(),
                    java.time.LocalDateTime.now().plusDays(2));
                auction1.setStatus(com.auction.model.AuctionStatus.RUNNING); // Admin đã duyệt sẵn
                AuctionManager.getInstance().updateAuction(auction1);

                // Mẫu 2: Đồ điện tử — đang chạy (RUNNING)
                com.auction.model.Electronics elec = new com.auction.model.Electronics(
                    "MacBook Pro M3 Max 16-inch", "Chip M3 Max, 48GB RAM, 1TB SSD, màu Space Black mới 100%.",
                    3500.0, "Apple", 12);  // 12 months warranty
                com.auction.model.Auction auction2 = AuctionManager.getInstance().createAuction(
                    elec, demoSeller,
                    java.time.LocalDateTime.now(),
                    java.time.LocalDateTime.now().plusDays(1));
                auction2.setStatus(com.auction.model.AuctionStatus.RUNNING);
                AuctionManager.getInstance().updateAuction(auction2);

                // Mẫu 3: Xe hơi — đang chờ duyệt (PENDING_APPROVAL) để demo quy trình duyệt
                com.auction.model.Vehicle car = new com.auction.model.Vehicle(
                    "Ferrari 488 GTB 2022", "Siêu xe Ferrari 488 GTB, màu đỏ Rosso Corsa, ít dùng.",
                    85000.0, "V8 Turbo", 12500.0);  // engineType, mileage
                AuctionManager.getInstance().createAuction(
                    car, demoSeller,
                    java.time.LocalDateTime.now(),
                    java.time.LocalDateTime.now().plusDays(5));
                // Status mặc định = PENDING_APPROVAL → Admin cần duyệt

                System.out.println("✅ Đã tạo 3 sản phẩm mẫu (2 RUNNING + 1 PENDING_APPROVAL chờ Admin duyệt)");
            } catch (Exception e) {
                System.err.println("❌ Lỗi tạo hàng mẫu: " + e.getMessage());
                e.printStackTrace();
            }
        }
        // ------------------------------------------------

        // 2. CƠ CHẾ LƯU DỮ LIỆU TỰ ĐỘNG TRƯỚC KHI TẮT SERVER
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n⚠️ Server đang tắt. Tiến hành lưu toàn bộ dữ liệu...");

            // 👉 THÊM MỚI: Gọi lưu cả 2 file
            userDAO.saveDataToFile();
            auctionDAO.saveDataToFile();

            threadPool.shutdown();
            try {
                // 👉 Đợi luồng ghi file chạy xong mới sập nguồn (An toàn dữ liệu)
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
            }
            System.out.println("💾 Đã lưu dữ liệu an toàn xuống file.");
        }));

        // 3. KHỞI TẠO KẾT NỐI SOCKET
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

    // --- CÁC HÀM HỖ TRỢ BROADCAST (PUSH NOTIFICATION) ---

    public static void joinAuctionView(String auctionId, ClientHandler client) {
        auctionViewers.putIfAbsent(auctionId, new CopyOnWriteArrayList<>());
        auctionViewers.get(auctionId).add(client);
    }

    public static void leaveAuctionView(String auctionId, ClientHandler client) {
        if (auctionViewers.containsKey(auctionId)) {
            auctionViewers.get(auctionId).remove(client);
        }
    }

    public static void broadcastToAuction(String auctionId, AuctionListUpdate updatePackage) {
        List<ClientHandler> viewers = auctionViewers.get(auctionId);
        if (viewers != null) {
            for (ClientHandler client : viewers) {
                client.sendResponse(new Response(StatusType.SUCCESS, "UPDATE_AUCTION_DATA", updatePackage));
            }
        }
    }

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

    public static void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        for (List<ClientHandler> viewers : auctionViewers.values()) {
            viewers.remove(clientHandler);
        }
        System.out.println("📉 Đã ngắt kết nối một client. Tổng số client hiện tại: " + clients.size());
    }

    public static AuctionDAOImpl getAuctionDAO() {
        return auctionDAO;
    }

    public static UserDAOImpl getUserDAO() {
        return userDAO;
    }
}