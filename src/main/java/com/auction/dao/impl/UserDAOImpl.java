package com.auction.dao.impl;

import com.auction.dao.FileDataManager;
import com.auction.dao.IGenericDAO;
import com.auction.model.User;
import com.auction.utils.UserManager;

import java.util.List;

// đã sửa level 2: Implement IGenericDAO<User> interface
public class UserDAOImpl implements IGenericDAO<User> {
    // File lưu trữ tài khoản sẽ nằm cùng chỗ với auctions_data.dat
    private static final String FILE_PATH = "users_data.dat";

    /**
     * LƯU DỮ LIỆU: Kéo danh sách User từ RAM và ghi đè xuống file
     */
    public boolean saveDataToFile() {
        List<User> currentUsers = UserManager.getInstance().getAllUsers();
        boolean success = FileDataManager.saveToFile(currentUsers, FILE_PATH);

        if (success) {
            System.out.println("💾 Đã lưu an toàn " + currentUsers.size() + " tài khoản xuống file.");
        } else {
            System.err.println("❌ Lỗi: Không thể lưu dữ liệu User!");
        }
        return success;
    }

    /**
     * TẢI DỮ LIỆU: Đọc file và nạp vào Két sắt (UserManager)
     */
    @SuppressWarnings("unchecked")
    public void loadDataFromFile() {
        Object data = FileDataManager.loadFromFile(FILE_PATH);

        if (data != null && data instanceof List) {
            List<User> loadedUsers = (List<User>) data;

            // Gọi hàm mới thêm ở Bước 1 để bơm dữ liệu vào Két sắt
            UserManager.getInstance().restoreUsers(loadedUsers);

            System.out.println("📦 Đã khôi phục " + loadedUsers.size() + " tài khoản từ file.");
        } else {
            System.out.println("⚠️ File dữ liệu User trống hoặc chưa tồn tại (Dùng tài khoản mặc định).");
        }
    }

    // đã sửa level 2: Implement các method từ IGenericDAO interface
    @Override
    public boolean save(User user) {
        UserManager.getInstance().addUser(user);
        return saveDataToFile();
    }

    @Override
    public boolean update(User user) {
        return saveDataToFile();
    }

    @Override
    public boolean delete(String id) {
        List<User> allUsers = UserManager.getInstance().getAllUsers();
        boolean removed = allUsers.removeIf(u -> u.getId().equals(id));
        if (removed) {
            return saveDataToFile();
        }
        return false;
    }

    @Override
    public User findById(String id) {
        return UserManager.getInstance().getUserById(id);
    }

    @Override
    public List<User> findAll() {
        return UserManager.getInstance().getAllUsers();
    }
}