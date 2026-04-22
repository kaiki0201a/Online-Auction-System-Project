package com.auction.model;

public class Art extends Item {
    // ID phiên bản để tránh lỗi khi nâng cấp code sau này
    private static final long serialVersionUID = 1L;

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
            System.out.println("Tên tác phẩm: " + this.getNameItem());
            System.out.println("Mô tả: " + this.getDescriptionItem());
            System.out.println("Giá khởi điểm: $" + this.getStartingPrice());
            System.out.println("Nghệ sĩ sáng tác: " + this.getArtist());
            System.out.println("Năm sáng tác: " + this.getCreationYear());
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
