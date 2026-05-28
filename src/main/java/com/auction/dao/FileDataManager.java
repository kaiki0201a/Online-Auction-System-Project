package com.auction.dao;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * FileDataManager — Tiện ích đọc/ghi dữ liệu ra file bằng Java Object Serialization.
 *
 * Xử lý các trường hợp lỗi phổ biến:
 *  - File chưa tồn tại (lần đầu chạy) → trả về null, DAO tự khởi tạo
 *  - InvalidClassException (file .dat cũ, serialVersionUID thay đổi) → backup + xóa + null
 *  - StreamCorruptedException (file bị ghi dở) → backup + xóa + null
 *  - IOException / ClassNotFoundException → log lỗi, trả về null
 */
public class FileDataManager {

    /**
     * Lưu một đối tượng bất kỳ xuống file (synchronized để thread-safe).
     *
     * @param data     Đối tượng cần lưu (VD: List<Auction>, List<User>)
     * @param filePath Đường dẫn file lưu trữ
     * @return true nếu lưu thành công, false nếu thất bại
     */
    public static synchronized boolean saveToFile(Object data, String filePath) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(data);
            return true;
        } catch (IOException e) {
            System.err.println("❌ Lỗi khi ghi file " + filePath + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Đọc dữ liệu từ file lên RAM khi server khởi động.
     *
     * Xử lý đặc biệt:
     *  - Nếu file không tồn tại → null (bình thường, lần đầu chạy)
     *  - Nếu InvalidClassException (class đổi) → backup *.dat.bak, xóa file gốc, return null
     *  - Nếu file bị hỏng → backup *.dat.bak, xóa file gốc, return null
     *  → Server KHÔNG bị crash, DAO tự khởi tạo danh sách rỗng
     *
     * @param filePath Đường dẫn file cần đọc
     * @return Object chứa dữ liệu, hoặc null nếu lỗi/không tồn tại
     */
    public static Object loadFromFile(String filePath) {
        File file = new File(filePath);

        if (!file.exists()) {
            System.out.println("ℹ️  [FileDataManager] File '" + filePath + "' chưa tồn tại (server chạy lần đầu).");
            return null;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return ois.readObject();

        } catch (InvalidClassException e) {
            // Class đã thay đổi (thêm field, đổi serialVersionUID)
            handleCorruptedFile(file, "InvalidClassException — class đã thay đổi sau khi update code");
            return null;

        } catch (StreamCorruptedException e) {
            // File bị ghi dở (server tắt giữa chừng)
            handleCorruptedFile(file, "StreamCorruptedException — file bị hỏng hoặc ghi chưa hoàn chỉnh");
            return null;

        } catch (ClassNotFoundException e) {
            System.err.println("❌ [FileDataManager] Không tìm thấy class khi đọc '"
                    + filePath + "': " + e.getMessage());
            return null;

        } catch (IOException e) {
            System.err.println("❌ [FileDataManager] Lỗi I/O khi đọc '"
                    + filePath + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Xử lý file bị lỗi:
     *  1. Backup file cũ sang *.dat.bak để debug sau này
     *  2. Xóa file gốc → server sẽ tạo lại dữ liệu sạch ở lần chạy tiếp theo
     *  3. Log thông báo rõ ràng
     */
    private static void handleCorruptedFile(File file, String reason) {
        System.err.println("⚠️  [FileDataManager] File '" + file.getName() + "' không thể đọc: " + reason);

        // Backup file cũ
        File backup = new File(file.getPath() + ".bak");
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("📦 [FileDataManager] Đã backup file cũ sang: " + backup.getName());
        } catch (IOException ex) {
            System.err.println("⚠️  [FileDataManager] Không thể tạo backup: " + ex.getMessage());
        }

        // Xóa file gốc để lần sau server khởi tạo lại sạch
        if (file.delete()) {
            System.out.println("🗑️  [FileDataManager] Đã xóa file lỗi '" + file.getName()
                    + "'. Server sẽ khởi tạo dữ liệu mới trong lần chạy này.");
        } else {
            System.err.println("❌ [FileDataManager] Không thể xóa file lỗi '" + file.getName() + "'.");
        }
    }
}
