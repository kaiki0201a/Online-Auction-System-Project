package com.auction.model;

public class Electronics extends Item {
    private String brand;
    private int warrantyMonths;

    public Electronics(String nameItem, String descriptionItem, double startingPrice, String brand, int warrantyMonths) {
        super(nameItem, descriptionItem, startingPrice);
        this.brand = brand;
        this.warrantyMonths = warrantyMonths;
    }

    @Override
    public void getInfo() {
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