package com.auction.utils;
import com.auction.model.Art;
import com.auction.model.Item;
import java.util.Map;

public class ArtFactory implements ItemFactory {
    @Override
    public Item createItem(String name, String description, double price, Map<String, Object> attributes) {
        // Lấy theo tên Key
        String artist = (String) attributes.get("artist");
        int year = (Integer) attributes.get("year");

        return new Art(name, description, price, artist, year);
    }
}