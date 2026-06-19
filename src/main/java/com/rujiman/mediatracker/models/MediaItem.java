package com.rujiman.mediatracker.models;

import java.util.List;

public class MediaItem {

    private int id;
    private MediaType type;

    private String title;
    private String description;
    private String imageUrl;

    private String platform;     // Netflix, Crunchyroll, Spotify, etc.
    private String externalUrl;  // URL para abrir la plataforma

    // ===== Datos extra estilo AniList =====
    private Integer year;
    private String studio;
    private String format;
    private Integer episodes;
    private List<String> genres;
    private Integer score;
    private String status;


// ===== Datos extra series/peliculas =====
private List<String> platforms;






    // ===== Getters y Setters =====

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public MediaType getType() { return type; }
    public void setType(MediaType type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getStudio() { return studio; }
    public void setStudio(String studio) { this.studio = studio; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public Integer getEpisodes() { return episodes; }
    public void setEpisodes(Integer episodes) { this.episodes = episodes; }

    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<String> getPlatforms() { return platforms; }
    public void setPlatforms(List<String> platforms) { this.platforms = platforms; }
}
