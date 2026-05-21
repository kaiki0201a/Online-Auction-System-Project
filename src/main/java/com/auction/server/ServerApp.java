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
            System.out.println("⚠️ Không có dữ liệu cũ, đang tạo sản phẩm mẫu để Test...");
            try {
                com.auction.model.Art art = new com.auction.model.Art("Tranh Đêm Đầy Sao", "Bản sao cực nét", 1000.0, "Van Gogh", 1889);
                com.auction.model.Seller dummySeller = new com.auction.model.Seller("NguoiBanVIP", "123", "seller@vip.com");

                // Dùng createAuction thay vì .add() để kích hoạt DAO lưu mẫu
                AuctionManager.getInstance().createAuction(
                        art,
                        dummySeller,
                        java.time.LocalDateTime.now(),
                        java.time.LocalDateTime.now().plusDays(1)
                );
                System.out.println("✅ Đã tạo thành công sản phẩm: " + art.getNameItem());
            } catch (Exception e) {
                System.err.println("❌ Lỗi tạo hàng mẫu: " + e.getMessage());
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
}