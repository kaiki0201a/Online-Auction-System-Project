package com.auction.server; // Hoặc com.auction.server.network

import com.auction.dao.impl.AuctionDAOImpl;
import com.auction.protocol.AuctionListUpdate;
import com.auction.protocol.Response;

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
    private static final int PORT = 8888; // Bạn có thể chọn cổng nào cũng được (VD: 8080, 9999)

    // Danh sách lưu trữ các ClientHandler đang kết nối
    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    // Map quản lý các ClientHandler đang xem từng phiên đấu giá cụ thể
    private static final Map<String, List<ClientHandler>> auctionViewers = new ConcurrentHashMap<>();

    // DAO để tải dữ liệu ban đầu
    private static final AuctionDAOImpl auctionDAO = new AuctionDAOImpl();

    // Hồ chứa luồng (Thread Pool) để quản lý đa luồng hiệu quả
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(50);

    public static void main(String[] args) {
        System.out.println("🚀 Đang khởi động Server Đấu Giá...");

        // Nạp dữ liệu từ file khi server khởi động
        auctionDAO.loadDataFromFile();
        System.out.println("📦 Dữ liệu đã được nạp lên bộ nhớ.");

        // --- CHÈN THÊM ĐOẠN CODE NÀY ĐỂ BƠM HÀNG MẪU ---
        if (com.auction.utils.AuctionManager.getInstance().getAllAuctions().isEmpty()) {
            System.out.println("⚠️ Không có dữ liệu cũ, đang tạo sản phẩm mẫu để Test...");
            try {
                // Tạo một bức tranh
                com.auction.model.Art art = new com.auction.model.Art("Tranh Đêm Đầy Sao", "Bản sao cực nét", 1000.0, "Van Gogh", 1889);
                com.auction.model.Seller dummySeller = new com.auction.model.Seller("NguoiBanVIP", "123", "seller@vip.com");

                // Tạo phiên đấu giá, kết thúc sau 1 ngày nữa
                com.auction.model.Auction mockAuction = new com.auction.model.Auction(
                        art,
                        dummySeller,
                        java.time.LocalDateTime.now(),
                        java.time.LocalDateTime.now().plusDays(1)
                );

                // Ép nó vào danh sách quản lý của Server
                com.auction.utils.AuctionManager.getInstance().getAllAuctions().add(mockAuction);
                System.out.println("✅ Đã tạo thành công sản phẩm: " + art.getNameItem());
            } catch (Exception e) {
                System.out.println("Lỗi tạo hàng mẫu: " + e.getMessage());
            }
        }
        // ------------------------------------------------

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("✅ Server đang lắng nghe tại cổng " + PORT);

            // Vòng lặp vô tận: Luôn mở cửa đón khách
            while (true) {
                // Code sẽ đứng im ở đây chờ đến khi có 1 Client gọi NetworkClient.connect()
                Socket clientSocket = serverSocket.accept();
                System.out.println("👋 Có khách mới kết nối: " + clientSocket.getInetAddress());

                // Giao khách này cho một "Bạn phục vụ" (ClientHandler)
                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler); // Thêm vào danh sách quản lý

                // Sử dụng Thread Pool để thực thi luồng riêng biệt
                threadPool.execute(handler);
            }

        } catch (IOException e) {
            System.err.println("❌ Lỗi Server: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    // Đăng ký client vào danh sách xem phiên đấu giá
    public static void joinAuctionView(String auctionId, ClientHandler client) {
        auctionViewers.putIfAbsent(auctionId, new CopyOnWriteArrayList<>());
        auctionViewers.get(auctionId).add(client);
    }

    // Hủy đăng ký client khỏi danh sách xem phiên đấu giá
    public static void leaveAuctionView(String auctionId, ClientHandler client) {
        if (auctionViewers.containsKey(auctionId)) {
            auctionViewers.get(auctionId).remove(client);
        }
    }

    // Gửi thông báo đến các client đang xem phiên đấu giá đó
    public static void broadcastToAuction(String auctionId, AuctionListUpdate updatePackage) {
        List<ClientHandler> viewers = auctionViewers.get(auctionId);
        if (viewers != null) {
            for (ClientHandler client : viewers) {
                client.sendResponse(new Response(com.auction.protocol.StatusType.SUCCESS, "UPDATE_AUCTION", updatePackage));
            }
        }
    }

    // Gửi thông báo đến TẤT CẢ các client đang kết nối
    public static void broadcast(Response response) {
        for (ClientHandler client : clients) {
            client.sendResponse(response);
        }
    }

    // Xóa client khỏi danh sách khi bị ngắt kết nối
    public static void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        for (List<ClientHandler> viewers : auctionViewers.values()) {
            viewers.remove(clientHandler);
        }
        System.out.println("📉 Đã xóa một client khỏi danh sách. Tổng số client hiện tại: " + clients.size());
    }

    // Getter cho AuctionDAO (nếu ClientHandler cần lấy DAO)
    public static AuctionDAOImpl getAuctionDAO() {
        return auctionDAO;
    }
}