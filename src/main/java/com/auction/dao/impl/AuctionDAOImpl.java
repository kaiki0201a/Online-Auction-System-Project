package com.auction.dao.impl;
import com.auction.dao.IAuctionDAO;
import com.auction.model.Auction;

import com.auction.dao.FileDataManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionDAOImpl implements IAuctionDAO {
    private Map<String, Auction> database = new ConcurrentHashMap<>();
    private FileDataManager fileManager = new FileDataManager();

    public void loadDataFromFile() {
        List<?> loadedData = fileManager.loadData();
        if (loadedData != null) {
            for (Object obj : loadedData) {
                if (obj instanceof Auction) {
                    Auction auction = (Auction) obj;
                    database.put(auction.getId(), auction);
                }
            }
        }
    }

    private void saveToFile() {
        fileManager.saveData(new ArrayList<>(database.values()));
    }

    @Override
    public boolean save(Auction obj){
        if(obj != null && obj.getId() != null){
            if(database.containsKey(obj.getId())){
                return false;
            }
            database.put(obj.getId(), obj);
            saveToFile();
            return true;
        }
        return false;
    }

    @Override
    public boolean update(Auction obj){
        if(obj != null && database.containsKey(obj.getId())){
            database.put(obj.getId(), obj);
            saveToFile();
            return true;
        }
        return false;
    }

    @Override
    public boolean delete(String id){
        if(database.containsKey(id)){
            database.remove(id);
            saveToFile();
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
