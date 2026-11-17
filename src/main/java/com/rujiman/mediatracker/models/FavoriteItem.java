package com.rujiman.mediatracker.models;

/**
 * Item guardado como favorito. Extiende TrackedItem; no duplica ningún
 * campo — sólo añade los propios de la lista de favoritos.
 */
public class FavoriteItem extends TrackedItem {

    public FavoriteItem() {}

    public FavoriteItem(MediaItem item) {
        fillFromMediaItem(item);
    }
}
