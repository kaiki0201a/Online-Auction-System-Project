package com.auction.model;

public class Vehicle extends Item{
    // ID phiên bản để tránh lỗi khi nâng cấp code sau này
    private static final long serialVersionUID = 1L;

    private String engineType;
    private double mileage;
    public Vehicle(String nameItem, String descriptionItem, double startingPrice, String engineType, double mileage) {
        super(nameItem, descriptionItem, startingPrice);
        this.engineType = engineType;
        this.mileage = mileage;
    }

    @Override
    public void printInfo() {
        System.out.println("Tên sản phẩm: " + this.getNameItem());
        System.out.println("Mô tả: " + this.getDescriptionItem());
        System.out.println("Giá khởi điểm: " + this.getStartingPrice());
        System.out.println("Loại Engine: " + this.getEngineType());
        System.out.println("Số dặm đã di chuyển: " + this.getMileage());
    }

    public String getEngineType() {
        return engineType;
    }

    public void setEngineType(String engineType) {
        this.engineType = engineType;
    }

    public double getMileage() {
        return mileage;
    }

    public void setMileage(double mileage) {
        this.mileage = mileage;
    }

}
