package com.rujiman.mediatracker.models;

/**
 * Item de una lista de planes (ver, jugar, escuchar).
 * Extiende TrackedItem; sólo añade folderId.
 */
public class PlanItem extends TrackedItem {

    private String folderId;

    public PlanItem() {}

    public PlanItem(MediaItem item) {
        fillFromMediaItem(item);
    }

    public String getFolderId()           { return folderId; }
    public void setFolderId(String id)    { this.folderId = id; }
}
