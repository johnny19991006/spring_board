package com.study.board.model;

public class Stock {
    private String name;
    private String code;

    public Stock(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }
}
