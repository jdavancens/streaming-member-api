package com.netflix.memberapi.billing.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String name;

    @Column(name = "monthly_price", nullable = false, precision = 6, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(name = "max_streams", nullable = false)
    private Integer maxStreams;

    @Column(name = "max_downloads", nullable = false)
    private Integer maxDownloads;

    @Column(name = "video_quality", nullable = false, length = 10)
    private String videoQuality;

    public Plan() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public void setMonthlyPrice(BigDecimal monthlyPrice) { this.monthlyPrice = monthlyPrice; }
    public Integer getMaxStreams() { return maxStreams; }
    public void setMaxStreams(Integer maxStreams) { this.maxStreams = maxStreams; }
    public Integer getMaxDownloads() { return maxDownloads; }
    public void setMaxDownloads(Integer maxDownloads) { this.maxDownloads = maxDownloads; }
    public String getVideoQuality() { return videoQuality; }
    public void setVideoQuality(String videoQuality) { this.videoQuality = videoQuality; }
}
