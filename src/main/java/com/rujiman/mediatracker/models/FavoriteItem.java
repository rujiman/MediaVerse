package com.rujiman.mediatracker.models;

/**
 * Modelo para representar un item guardado en favoritos
 */
public class FavoriteItem {
    private String id; // ID único (generado)
    private MediaType type;
    private String title;
    private String description;
    private String imageUrl;
    private Integer year;
    private Integer score;
    private String externalUrl;
    private boolean viewed; // visto/jugado
    private long addedDate; // timestamp cuando se agregó

    public FavoriteItem() {}

    public FavoriteItem(MediaItem item) {
        this.id = generateId();
        this.type = item.getType();
        this.title = item.getTitle();
        this.description = item.getDescription();
        this.imageUrl = item.getImageUrl();
        this.year = item.getYear();
        this.score = item.getScore();
        this.externalUrl = item.getExternalUrl();
        this.viewed = false;
        this.addedDate = System.currentTimeMillis();
    }

    private String generateId() {
        return System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
    }

    // ===== GETTERS Y SETTERS =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public MediaType getType() { return type; }
    public void setType(MediaType type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }

    public boolean isViewed() { return viewed; }
    public void setViewed(boolean viewed) { this.viewed = viewed; }

    public long getAddedDate() { return addedDate; }
    public void setAddedDate(long addedDate) { this.addedDate = addedDate; }
}
