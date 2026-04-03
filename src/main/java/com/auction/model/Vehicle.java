package com.auction.model;

public class Vehicle extends Item{
    String engineType;
    double mileage;
    public Vehicle(String nameItem, String descriptionItem, double startingPrice, String engineType, double mileage) {
        super(nameItem, descriptionItem, startingPrice);
        this.engineType = engineType;
        this.mileage = mileage;
    }

    @Override
    public void printInfo() {
        System.out.println("Tên sản phẩm: " + nameItem);
        System.out.println("Mô tả: " + descriptionItem);
        System.out.println("Giá khởi điểm: " + startingPrice);
        System.out.println("Loại Engine: " + engineType);
        System.out.println("Số dặm đã di chuyển: " + mileage);
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
