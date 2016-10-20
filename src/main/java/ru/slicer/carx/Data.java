package ru.slicer.carx;

import java.util.UUID;

public class Data {
    private final UUID uuid;
    private final int money;
    private final String country;
    private final String clob;

    public Data(final UUID uuid, final int money, final String clob, final String country) {
        this.uuid = uuid;
        this.money = money;
        this.clob = clob;
        this.country = country;
    }

    public int getMoney() {
        return money;
    }

    public String getClob() {
        return clob;
    }

    public String getCountry() {
        return country;
    }

    public UUID getUserId() {
        return uuid;
    }
}
