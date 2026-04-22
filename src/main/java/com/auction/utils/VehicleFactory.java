package com.auction.utils;

import com.auction.model.Item;
import com.auction.model.Vehicle;
import java.util.Map;

public class VehicleFactory implements ItemFactory {
    @Override
    public Item createItem(String name, String description, double price, Map<String, Object> attributes) {
        // Lấy dữ liệu từ Map và ép kiểu
        String engineType = (String) attributes.get("engineType");
        double mileage = (Double) attributes.get("mileage");

        // Trả về đối tượng Vehicle
        return new Vehicle(name, description, price, engineType, mileage);
    }
}