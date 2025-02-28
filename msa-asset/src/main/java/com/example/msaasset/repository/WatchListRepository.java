package com.example.msaasset.repository;

import com.example.msaasset.entity.WatchList;
import com.example.msaasset.entity.Stock;
import com.example.msaasset.entity.WatchListKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchListRepository extends JpaRepository<WatchList, Long> {

    boolean existsById(WatchListKey id);
    List<WatchList> findByIdUserEmail(String userEmail);

    void deleteById(WatchListKey watchListKey);
}
