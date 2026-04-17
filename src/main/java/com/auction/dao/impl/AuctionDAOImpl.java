package com.auction.dao.impl;
import com.auction.dao.IAuctionDAO;
import com.auction.model.Auction;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionDAOImpl implements IAuctionDAO {
    private static final String FILE_PATH = "auction_data.dat";
    private Map<String, Auction> database;
    public AuctionDAOImpl() {
        loadDataFromFile();
    }
    private void saveDataToFile (){
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE_PATH))) {
            oos.writeObject(database);
        } catch (IOException e) {
            System.err.println("Error when writing data to file: " + e.getMessage());
        }
    }
    @SuppressWarnings("unchecked")
    private void loadDataFromFile(){
        File file = new File(FILE_PATH);
        if (!file.exists()) { // If the file is created for 1st time, creates a safe new map for multithreading
            database = new ConcurrentHashMap<>();
            return;
        }
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = ois.readObject();
            if (obj instanceof ConcurrentHashMap) {
                database = (ConcurrentHashMap<String, Auction>) obj;
            } else {
                database = new ConcurrentHashMap<>();
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error while reading data from file" + e.getMessage());
            database = new ConcurrentHashMap<>(); //Fallback
        }
    }

    @Override
    public boolean save(Auction obj){
        if(obj != null && obj.getAuctionId() != null){
            if(database.containsKey(obj.getAuctionId())){
                return false;
            }
            database.put(obj.getAuctionId(),obj);
            saveDataToFile();
            return true;
        }
        return false;
    }

    @Override
    public boolean update(Auction obj){
        if(obj!=null && database.containsKey(obj.getAuctionId())){
            database.put(obj.getAuctionId(), obj);
            saveDataToFile();
            return true;
        }
        return false;
    }
    @Override
    public boolean delete(String id){
        if(database.containsKey(id)){
            database.remove(id);
            saveDataToFile();
            return true;
        }
        return false;

    }
    @Override
    public Auction findById(String id){
        return database.get(id);
    }
    @Override
    public List<Auction> findAll(){
        return new ArrayList<>(database.values());
    }
}
