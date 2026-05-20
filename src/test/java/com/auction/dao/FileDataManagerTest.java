package com.auction.dao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FileDataManagerTest {

    private static final String TEST_FILE_PATH = "test_data.dat";

    // Chạy trước mỗi hàm Test: Đảm bảo môi trường sạch sẽ
    @BeforeEach
    public void setUp() {
        File file = new File(TEST_FILE_PATH);
        if (file.exists()) {
            file.delete();
        }
    }

    // Chạy sau mỗi hàm Test: Dọn dẹp file rác
    @AfterEach
    public void tearDown() {
        File file = new File(TEST_FILE_PATH);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    public void testSaveAndLoadDataSuccess() {
        // 1. Chuẩn bị dữ liệu giả (Mock data)
        List<String> originalData = Arrays.asList("Đấu giá 1", "Đấu giá 2", "Đấu giá 3");

        // 2. Thực thi hàm lưu file
        boolean isSaved = FileDataManager.saveToFile(originalData, TEST_FILE_PATH);
        assertTrue(isSaved, "Hàm saveToFile phải trả về true khi lưu thành công");

        // 3. Thực thi hàm đọc file
        Object loadedObject = FileDataManager.loadFromFile(TEST_FILE_PATH);
        assertNotNull(loadedObject, "Dữ liệu đọc lên không được null");
        assertTrue(loadedObject instanceof List, "Dữ liệu đọc lên phải là một List");

        // 4. So sánh dữ liệu (Assert)
        @SuppressWarnings("unchecked")
        List<String> loadedData = (List<String>) loadedObject;

        assertEquals(originalData.size(), loadedData.size(), "Kích thước list phải bằng nhau");
        assertEquals(originalData.get(0), loadedData.get(0), "Phần tử đầu tiên phải khớp nhau 100%");
        assertEquals(originalData.get(2), loadedData.get(2), "Phần tử cuối cùng phải khớp nhau 100%");
    }

    @Test
    public void testLoadFromNonExistentFile() {
        // Thử đọc một file không tồn tại, kỳ vọng trả về null
        Object loadedObject = FileDataManager.loadFromFile("file_ma.dat");
        assertNull(loadedObject, "Phải trả về null nếu file không tồn tại");
    }
}