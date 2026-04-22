package com.auction.utils;
import com.auction.model.Item;
import java.util.Map;

public interface ItemFactory {
    Item createItem(String name, String description, double price, Map<String, Object> attributes);
}