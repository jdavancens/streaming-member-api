package com.netflix.memberapi.profile.model;

import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.UUID;

@Table("profiles")
public class Profile {

    @PrimaryKeyColumn(name = "member_id", type = PrimaryKeyType.PARTITIONED)
    private UUID memberId;

    @PrimaryKeyColumn(name = "profile_id", type = PrimaryKeyType.CLUSTERED)
    private UUID profileId;

    @Column("name")
    private String name;

    @Column("avatar_url")
    private String avatarUrl;

    @Column("is_kids")
    private Boolean isKids;

    @Column("language")
    private String language;

    @Column("pin_hash")
    private String pinHash;

    public Profile() {}

    public UUID getMemberId() { return memberId; }
    public void setMemberId(UUID memberId) { this.memberId = memberId; }
    public UUID getProfileId() { return profileId; }
    public void setProfileId(UUID profileId) { this.profileId = profileId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public Boolean getIsKids() { return isKids; }
    public void setIsKids(Boolean isKids) { this.isKids = isKids; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }
    public boolean isHasPinLock() { return pinHash != null && !pinHash.isBlank(); }
}
