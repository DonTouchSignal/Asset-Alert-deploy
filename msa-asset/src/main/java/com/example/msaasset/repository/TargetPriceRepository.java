package com.example.msaasset.repository;

import com.example.msaasset.entity.TargetPrice;
import com.example.msaasset.entity.Stock;
import com.example.msaasset.entity.TargetPriceKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TargetPriceRepository extends JpaRepository<TargetPrice, Long> {

    void deleteById(TargetPriceKey targetPriceKey);

    List<TargetPrice> findByIdUserEmail(String userEmail);
}

