package com.streaming.memberapi.profile.service;

import com.streaming.memberapi.profile.model.Profile;
import com.streaming.memberapi.profile.repository.ProfileRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProfileService {

    private static final int MAX_PROFILES = 5;

    private final ProfileRepository profileRepository;
    private final PasswordEncoder pinEncoder;

    public ProfileService(ProfileRepository profileRepository, PasswordEncoder pinEncoder) {
        this.profileRepository = profileRepository;
        this.pinEncoder = pinEncoder;
    }

    public List<Profile> findByMemberId(UUID memberId) {
        return profileRepository.findByMemberId(memberId);
    }

    public Profile create(UUID memberId, Map<String, Object> input) {
        if (profileRepository.countByMemberId(memberId) >= MAX_PROFILES) {
            throw new IllegalStateException("Maximum of " + MAX_PROFILES + " profiles per member");
        }
        Profile profile = new Profile();
        profile.setMemberId(memberId);
        profile.setProfileId(UUID.randomUUID());
        profile.setName((String) input.get("name"));
        profile.setAvatarUrl((String) input.get("avatarUrl"));
        profile.setIsKids(input.get("isKids") != null ? (Boolean) input.get("isKids") : false);
        profile.setLanguage(input.get("language") != null ? (String) input.get("language") : "en");

        String pin = (String) input.get("pin");
        if (pin != null && !pin.isBlank()) {
            profile.setPinHash(pinEncoder.encode(pin));
        }
        return profileRepository.save(profile);
    }

    public Profile update(UUID profileId, Map<String, Object> input) {
        // In Cassandra we'd need memberId as partition key; for simplicity scan (demo only)
        Profile profile = profileRepository.findAll().stream()
            .filter(p -> p.getProfileId().equals(profileId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId));

        if (input.get("name") != null) profile.setName((String) input.get("name"));
        if (input.get("avatarUrl") != null) profile.setAvatarUrl((String) input.get("avatarUrl"));
        if (input.get("language") != null) profile.setLanguage((String) input.get("language"));
        return profileRepository.save(profile);
    }

    public boolean delete(UUID profileId) {
        Profile profile = profileRepository.findAll().stream()
            .filter(p -> p.getProfileId().equals(profileId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId));
        profileRepository.delete(profile);
        return true;
    }

    public boolean verifyPin(UUID profileId, String pin) {
        return profileRepository.findAll().stream()
            .filter(p -> p.getProfileId().equals(profileId))
            .findFirst()
            .map(p -> p.getPinHash() != null && pinEncoder.matches(pin, p.getPinHash()))
            .orElse(false);
    }
}
