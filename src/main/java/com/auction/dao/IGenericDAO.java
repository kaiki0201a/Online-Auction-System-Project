package com.auction.dao;

import java.util.List;

public interface IGenericDAO<T> {
    boolean save(T obj);
    boolean update(T obj);
    boolean delete(String id);
    T findById(String id);
    List<T> findAll();
}