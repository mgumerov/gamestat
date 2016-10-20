package ru.slicer.carx;

import java.util.UUID;

public class User {
    private final UUID id;

    public User(final UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
