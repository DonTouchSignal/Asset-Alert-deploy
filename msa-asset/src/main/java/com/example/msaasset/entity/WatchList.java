package com.example.msaasset.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchList {

    @EmbeddedId
    private WatchListKey id;

    @Column(nullable = false)
    private String createdAt;

    public WatchList(String userEmail, String symbol) {
        this.id = new WatchListKey(userEmail, symbol);
        this.createdAt = String.valueOf(System.currentTimeMillis());
    }

    public WatchList(WatchListKey id) {
        this.id = id;
        this.createdAt = String.valueOf(System.currentTimeMillis());
    }


    public String getSymbol() {
        return this.id != null ? this.id.getSymbol() : null;
    }

}

