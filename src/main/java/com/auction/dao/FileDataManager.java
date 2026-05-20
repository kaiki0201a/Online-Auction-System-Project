package com.auction.dao;

import java.io.*;

public class FileDataManager {

    /**
     * Hàm lưu một đối tượng bất kỳ xuống file
     * @param data Đối tượng cần lưu (VD: List<Auction>, List<User>,...)
     * @param filePath Đường dẫn tới file lưu trữ (truyền vào từ các lớp DAO)
     * @return true nếu lưu thành công, false nếu thất bại
     */
    public static synchronized boolean saveToFile(Object data, String filePath) {
        // Sử dụng try-with-resources để Java tự động đóng file (close)
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {

            oos.writeObject(data);
            return true;

        } catch (IOException e) {
            System.err.println("❌ Lỗi khi ghi file " + filePath + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Hàm đọc dữ liệu từ file lên RAM
     * @param filePath Đường dẫn file cần đọc
     * @return Object chứa dữ liệu (cần ép kiểu khi sử dụng), hoặc null nếu file không tồn tại
     */
    public static Object loadFromFile(String filePath) {
        File file = new File(filePath);

        // Nếu file chưa tồn tại (lần đầu tiên chạy Server), trả về null để DAO tự xử lý
        if (!file.exists()) {
            return null;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {

            return ois.readObject();

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("❌ Lỗi khi đọc file " + filePath + ": " + e.getMessage());
            return null;
        }
    }
}