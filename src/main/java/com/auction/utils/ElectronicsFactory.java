package com.auction.utils;

import com.auction.model.Electronics;
import com.auction.model.Item;
import java.util.Map;

public class ElectronicsFactory implements ItemFactory {
    @Override
    public Item createItem(String name, String description, double price, Map<String, Object> attributes) {
        // Lấy dữ liệu từ Map và ép kiểu
        String brand = (String) attributes.get("brand");
        int warrantyMonths = (Integer) attributes.get("warrantyMonths");

        // Trả về đối tượng Electronics
        return new Electronics(name, description, price, brand, warrantyMonths);
    }
}