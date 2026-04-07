package com.auction.model;

public abstract class Item extends Entity{
    // ID phiên bản để tránh lỗi khi nâng cấp code sau này
    private static final long serialVersionUID = 1L;

    // Fields
    protected String nameItem, descriptionItem;
    protected double startingPrice;

    // Constructor

    public Item(String nameItem, String descriptionItem, double startingPrice) {
        super();
        this.nameItem = nameItem;
        this.descriptionItem = descriptionItem;
        this.startingPrice = startingPrice;
    }

    // Method
    // Hàm lấy thông tin
    public abstract void printInfo();

    // Getter & Setter
    public String getNameItem() {
        return nameItem;
    }

    public void setNameItem(String nameItem) {
        this.nameItem = nameItem;
    }

    public String getDescriptionItem() {
        return descriptionItem;
    }

    public void setDescriptionItem(String descriptionItem) {
        this.descriptionItem = descriptionItem;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(double startingPrice) {
        this.startingPrice = startingPrice;
    }
}
