package com.kyrioslab.dsvc.node.util;

import com.kyrioslab.dsvc.node.messages.LocalMessage;

/**
 * Created by Ivan Kirilyuk on 08.01.15.
 *
 */
public class PartTime {

    public static final int TIME_INCREMENT = 1;

    private final String partId;
    private final LocalMessage.PlaceOnTrackMessage savedMsg;
    private long time = 0;

    public PartTime(String partId, LocalMessage.PlaceOnTrackMessage savedMsg) {
        this.partId = partId;
        this.savedMsg = savedMsg;
    }

    public void inc() {
        time += TIME_INCREMENT;
    }

    public void reset() {
        this.time = 0;
    }

    public String getPartId() {
        return partId;
    }

    public long getTime() {
        return time;
    }

    public LocalMessage.PlaceOnTrackMessage getSavedMsg() {
        return savedMsg;
    }
}
