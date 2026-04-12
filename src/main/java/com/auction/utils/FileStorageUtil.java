package com.auction.utils;

import java.io.*;

public class FileStorageUtil {

    /**
     * HÀM 1: Ghi đối tượng xuống ổ cứng
     * Dùng cú pháp 'try-with-resources' để tự động đóng luồng sau khi lưu xong
     */
    public static void saveDataToFile(Object data, String filePath) {
        // ObjectOutputStream là ống nước để bơm dữ liệu từ RAM xuống File
        try (FileOutputStream fos = new FileOutputStream(filePath);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {

            oos.writeObject(data); // Lệnh thực thi ghi dữ liệu
            System.out.println("💾 [Hệ thống] Đã sao lưu dữ liệu an toàn xuống: " + filePath);

        } catch (IOException e) {
            System.err.println("❌ [Lỗi Hệ Thống] Không thể ghi file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * HÀM 2: Đọc đối tượng từ ổ cứng lên RAM
     */
    public static Object readDataFromFile(String filePath) {
        File file = new File(filePath);

        // Kiểm tra xem file đã tồn tại chưa (Nếu chạy chương trình lần đầu thì file chưa có)
        if (!file.exists()) {
            System.out.println("⚠️ [Hệ thống] Không tìm thấy file dữ liệu cũ. Sẽ khởi tạo dữ liệu mới.");
            return null;
        }

        // ObjectInputStream là ống nước hút dữ liệu từ File lên RAM
        try (FileInputStream fis = new FileInputStream(file);
             ObjectInputStream ois = new ObjectInputStream(fis)) {

            Object data = ois.readObject(); // Lệnh thực thi đọc dữ liệu
            System.out.println("📂 [Hệ thống] Đã khôi phục dữ liệu từ: " + filePath);
            return data;

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("❌ [Lỗi Hệ Thống] File dữ liệu bị hỏng hoặc lỗi định dạng: " + e.getMessage());
            return null;
        }
    }
}