package com.auction.dao;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FileDataManager {

    // Đường dẫn tới file lưu trữ (nằm trong thư mục gốc của project)
    private static final String DATA_FILE = "auction_data.dat";

    /**
     * Hàm lưu toàn bộ dữ liệu xuống file
     * @param dataList Danh sách các đối tượng cần lưu (VD: List<Auction>)
     */
    public void saveData(List<?> dataList) {
        // Sử dụng try-with-resources để Java tự động đóng file (close) sau khi xong
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {

            oos.writeObject(dataList);
            System.out.println("💾 Đã lưu dữ liệu xuống file thành công!");

        } catch (IOException e) {
            System.err.println("❌ Lỗi khi ghi file: " + e.getMessage());
        }
    }

    /**
     * Hàm đọc dữ liệu từ file lên RAM
     * @return Danh sách các đối tượng đã được phục hồi
     */
    public List<?> loadData() {
        File file = new File(DATA_FILE);

        // Nếu file chưa tồn tại (lần đầu tiên chạy Server), trả về danh sách rỗng
        if (!file.exists()) {
            System.out.println("⚠️ Không tìm thấy file dữ liệu cũ. Bắt đầu với dữ liệu trống.");
            return new ArrayList<>();
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {

            List<?> loadedData = (List<?>) ois.readObject();
            System.out.println("📂 Đã nạp dữ liệu từ file thành công!");
            return loadedData;

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("❌ Lỗi khi đọc file: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}