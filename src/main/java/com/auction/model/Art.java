package com.auction.model;

public class Art extends Item {
    // Fields
    protected String artist;
    protected int creationYear;
    // Constructor
    public Art(String nameItem, String descriptionItem, double startingPrice, String artist, int creationYear ) {
        super(nameItem, descriptionItem, startingPrice);
        this.artist = artist;
        this.creationYear = creationYear;
    }
    // Overriding Item, return item's information
    @Override
    public void printInfo() {
            System.out.println("--- Thông tin Tác phẩm Nghệ thuật ---");
            System.out.println("Tên tác phẩm: " + this.nameItem);
            System.out.println("Mô tả: " + this.descriptionItem);
            System.out.println("Giá khởi điểm: $" + this.startingPrice);
            System.out.println("Nghệ sĩ sáng tác: " + this.artist);
            System.out.println("Năm sáng tác: " + this.creationYear);
    }
    // Getter and Setter
    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public int getCreationYear() {
        return creationYear;
    }

    public void setCreationYear(int creationYear) {
        this.creationYear = creationYear;
    }
}
