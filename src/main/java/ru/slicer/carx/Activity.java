package ru.slicer.carx;

import java.util.Date;
import java.util.UUID;

public class Activity {
    private final UUID id;
    private final Date timestamp;
    private final int activity;

    public Activity(final UUID id, final int activity, final Date timestamp) {
        this.id = id;
        this.timestamp = timestamp;
        this.activity = activity;
    }

    public UUID getId() {
        return id;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public int getActivity() {
        return activity;
    }
}
