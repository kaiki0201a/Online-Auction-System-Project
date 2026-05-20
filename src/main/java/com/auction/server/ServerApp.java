package com.auction.server;

import com.auction.dao.impl.AuctionDAOImpl;
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

public class ServerApp {
    private static final int PORT = 8888;

    // Danh sách lưu trữ các ClientHandler đang kết nối (Thread-safe)
    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    // Map quản lý các ClientHandler đang xem từng phiên đấu giá cụ thể (Hỗ trợ Observer Pattern)
    private static final Map<String, List<ClientHandler>> auctionViewers = new ConcurrentHashMap<>();

    // DAO để quản lý dữ liệu (Cần có thêm UserDAO/UserManager để quản lý User)
    private static final AuctionDAOImpl auctionDAO = new AuctionDAOImpl();

    // Hồ chứa luồng (Thread Pool) tự động co giãn.
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        System.out.println("🚀 Đang khởi động Server Đấu Giá...");

        // 1. TẢI DỮ LIỆU TỪ FILE VÀO RAM KHI KHỞI ĐỘNG
        auctionDAO.loadDataFromFile();
        // TODO: Thêm lệnh load data của UserDAO vào đây
        System.out.println("📦 Dữ liệu đã được nạp lên bộ nhớ.");

        // --- ĐOẠN CODE BƠM HÀNG MẪU ĐỂ TEST ---
        if (AuctionManager.getInstance().getAllAuctions().isEmpty()) {
            System.out.println("⚠️ Không có dữ liệu cũ, đang tạo sản phẩm mẫu để Test...");
            try {
                com.auction.model.Art art = new com.auction.model.Art("Tranh Đêm Đầy Sao", "Bản sao cực nét", 1000.0, "Van Gogh", 1889);
                com.auction.model.Seller dummySeller = new com.auction.model.Seller("NguoiBanVIP", "123", "seller@vip.com");
                com.auction.model.Auction mockAuction = new com.auction.model.Auction(
                        art,
                        dummySeller,
                        java.time.LocalDateTime.now(),
                        java.time.LocalDateTime.now().plusDays(1)
                );
                AuctionManager.getInstance().getAllAuctions().add(mockAuction);
                System.out.println("✅ Đã tạo thành công sản phẩm: " + art.getNameItem());
            } catch (Exception e) {
                System.err.println("❌ Lỗi tạo hàng mẫu: " + e.getMessage());
            }
        }
        // ------------------------------------------------

        // 2. CƠ CHẾ LƯU DỮ LIỆU TỰ ĐỘNG TRƯỚC KHI TẮT SERVER (SHUTDOWN HOOK)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n⚠️ Server đang tắt. Tiến hành lưu toàn bộ dữ liệu...");
            auctionDAO.saveDataToFile();
            // TODO: Gọi hàm save User (vd: userDAO.saveDataToFile())
            System.out.println("💾 Đã lưu dữ liệu an toàn xuống file.");
            threadPool.shutdown();
        }));

        // 3. KHỞI TẠO KẾT NỐI SOCKET
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("✅ Server đang lắng nghe tại cổng " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("👋 Có khách mới kết nối: " + clientSocket.getInetAddress());

                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler);

                threadPool.execute(handler); // Quăng việc cho luồng xử lý
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

    // Chỉ thông báo cho những ai đang xem chi tiết phiên đấu giá đó
    public static void broadcastToAuction(String auctionId, AuctionListUpdate updatePackage) {
        List<ClientHandler> viewers = auctionViewers.get(auctionId);
        if (viewers != null) {
            for (ClientHandler client : viewers) {
                client.sendResponse(new Response(StatusType.SUCCESS, "UPDATE_AUCTION_DATA", updatePackage));
            }
        }
    }

    // Thông báo cho TẤT CẢ mọi người (Dùng khi có phiên đấu giá mới tạo, hoặc kết thúc)
    public static void broadcast(Response response) {
        for (ClientHandler client : clients) {
            client.sendResponse(response);
        }
    }

    // 👉 HÀM ĐƯỢC THÊM VÀO ĐỂ FIX LỖI "Cannot resolve method" Ở CLIENT HANDLER
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