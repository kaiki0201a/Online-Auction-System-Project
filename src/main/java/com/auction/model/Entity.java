package com.auction.model;

import java.util.UUID;

/**
 * Lớp trừu tượng cơ sở cho mọi đối tượng trong hệ thống (User, Item, Auction...).
 * Giúp đảm bảo mọi thực thể đều có một mã ID duy nhất ngay khi được tạo ra.
 */
public abstract class Entity {
    protected String id;

    public Entity() {
        // Tự động tạo mã định danh duy nhất (UUID) khi khởi tạo đối tượng
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}