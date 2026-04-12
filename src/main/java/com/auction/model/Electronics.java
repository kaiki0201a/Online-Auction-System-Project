package com.auction.model;

public class Electronics extends Item {
    // ID phiên bản để tránh lỗi khi nâng cấp code sau này
    private static final long serialVersionUID = 1L;

    private String brand;
    private int warrantyMonths;

    public Electronics(String nameItem, String descriptionItem, double startingPrice, String brand, int warrantyMonths) {
        super(nameItem, descriptionItem, startingPrice);
        this.brand = brand;
        this.warrantyMonths = warrantyMonths;
    }

    @Override
    public void printInfo() {
        System.out.println("Tên sản phẩm: " + nameItem);
        System.out.println("Mô tả: " + descriptionItem);
        System.out.println("Giá khởi điểm: " + startingPrice);
        System.out.println("Thương hiệu: " + brand);
        System.out.println("Bảo hành: " + warrantyMonths + " tháng");
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public int getWarrantyMonths() {
        return warrantyMonths;
    }

    public void setWarrantyMonths(int warrantyMonths) {
        this.warrantyMonths = warrantyMonths;
    }
}