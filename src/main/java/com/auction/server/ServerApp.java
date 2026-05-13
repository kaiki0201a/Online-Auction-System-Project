package com.auction.server; // Hoặc com.auction.server.network

import com.auction.dao.impl.AuctionDAOImpl;
import com.auction.protocol.Response;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerApp {
    private static final int PORT = 8888; // Bạn có thể chọn cổng nào cũng được (VD: 8080, 9999)

    // Danh sách lưu trữ các ClientHandler đang kết nối
    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    // DAO để tải dữ liệu ban đầu
    private static final AuctionDAOImpl auctionDAO = new AuctionDAOImpl();

    public static void main(String[] args) {
        System.out.println("🚀 Đang khởi động Server Đấu Giá...");

        // Nạp dữ liệu từ file khi server khởi động
        auctionDAO.loadDataFromFile();
        System.out.println("📦 Dữ liệu đã được nạp lên bộ nhớ.");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("✅ Server đang lắng nghe tại cổng " + PORT);

            // Vòng lặp vô tận: Luôn mở cửa đón khách
            while (true) {
                // Code sẽ đứng im ở đây chờ đến khi có 1 Client gọi NetworkClient.connect()
                Socket clientSocket = serverSocket.accept();
                System.out.println("👋 Có khách mới kết nối: " + clientSocket.getInetAddress());

                // Giao khách này cho một "Bạn phục vụ" (ClientHandler) chạy trên 1 Luồng (Thread) riêng biệt
                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler); // Thêm vào danh sách quản lý
                new Thread(handler).start();
            }

        } catch (IOException e) {
            System.err.println("❌ Lỗi Server: " + e.getMessage());
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
        System.out.println("📉 Đã xóa một client khỏi danh sách. Tổng số client hiện tại: " + clients.size());
    }

    // Getter cho AuctionDAO (nếu ClientHandler cần lấy DAO)
    public static AuctionDAOImpl getAuctionDAO() {
        return auctionDAO;
    }
}