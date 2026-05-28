package com.auction.model;

import java.io.Serializable;
import java.util.UUID;

/**
 * Lớp trừu tượng cơ sở cho mọi đối tượng trong hệ thống (User, Item, Auction...).
 * Giúp đảm bảo mọi thực thể đều có một mã ID duy nhất ngay khi được tạo ra.
 */
public abstract class Entity implements Serializable {
    // ID phiên bản để tránh lỗi khi nâng cấp code sau này
    private static final long serialVersionUID = 1L;

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