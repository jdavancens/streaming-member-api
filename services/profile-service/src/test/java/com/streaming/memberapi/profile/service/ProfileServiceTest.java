package com.streaming.memberapi.profile.service;

import com.streaming.memberapi.profile.model.Profile;
import com.streaming.memberapi.profile.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileServiceTest {

    @Mock
    ProfileRepository profileRepository;
    @Mock
    PasswordEncoder pinEncoder;

    @InjectMocks
    ProfileService profileService;

    private Profile profile(UUID memberId, UUID profileId, String name) {
        Profile p = new Profile();
        p.setMemberId(memberId);
        p.setProfileId(profileId);
        p.setName(name);
        return p;
    }

    @Test
    void findByMemberId_delegatesToRepository() {
        UUID memberId = UUID.randomUUID();
        List<Profile> profiles = List.of(profile(memberId, UUID.randomUUID(), "Kids"));
        when(profileRepository.findByMemberId(memberId)).thenReturn(profiles);

        assertThat(profileService.findByMemberId(memberId)).isEqualTo(profiles);
    }

    @Test
    void create_buildsProfileWithDefaults() {
        UUID memberId = UUID.randomUUID();
        when(profileRepository.countByMemberId(memberId)).thenReturn(0L);
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> input = new HashMap<>();
        input.put("name", "Alice");

        Profile result = profileService.create(memberId, input);

        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getMemberId()).isEqualTo(memberId);
        assertThat(result.getProfileId()).isNotNull();
        assertThat(result.getIsKids()).isFalse();
        assertThat(result.getLanguage()).isEqualTo("en");
        assertThat(result.getPinHash()).isNull();
    }

    @Test
    void create_encodesPinAndAppliesProvidedValues() {
        UUID memberId = UUID.randomUUID();
        when(profileRepository.countByMemberId(memberId)).thenReturn(2L);
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pinEncoder.encode("1234")).thenReturn("PIN_HASH");

        Map<String, Object> input = new HashMap<>();
        input.put("name", "Bob");
        input.put("avatarUrl", "http://x/a.png");
        input.put("isKids", true);
        input.put("language", "fr");
        input.put("pin", "1234");

        Profile result = profileService.create(memberId, input);

        assertThat(result.getIsKids()).isTrue();
        assertThat(result.getLanguage()).isEqualTo("fr");
        assertThat(result.getAvatarUrl()).isEqualTo("http://x/a.png");
        assertThat(result.getPinHash()).isEqualTo("PIN_HASH");
        assertThat(result.isHasPinLock()).isTrue();
    }

    @Test
    void create_rejectsWhenMaxProfilesReached() {
        UUID memberId = UUID.randomUUID();
        when(profileRepository.countByMemberId(memberId)).thenReturn(5L);

        assertThatThrownBy(() -> profileService.create(memberId, Map.of("name", "X")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Maximum of 5");
        verify(profileRepository, never()).save(any());
    }

    @Test
    void create_blankPinIsNotHashed() {
        UUID memberId = UUID.randomUUID();
        when(profileRepository.countByMemberId(memberId)).thenReturn(0L);
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> input = new HashMap<>();
        input.put("name", "Bob");
        input.put("pin", "   ");

        Profile result = profileService.create(memberId, input);

        assertThat(result.getPinHash()).isNull();
        verify(pinEncoder, never()).encode(any());
    }

    @Test
    void update_appliesNonNullFields() {
        UUID profileId = UUID.randomUUID();
        Profile existing = profile(UUID.randomUUID(), profileId, "Old");
        existing.setLanguage("en");
        when(profileRepository.findAll()).thenReturn(List.of(existing));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> input = new HashMap<>();
        input.put("name", "New");
        input.put("language", "es");

        Profile result = profileService.update(profileId, input);

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getLanguage()).isEqualTo("es");
    }

    @Test
    void update_throwsWhenProfileMissing() {
        when(profileRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> profileService.update(UUID.randomUUID(), Map.of("name", "X")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Profile not found");
    }

    @Test
    void delete_removesProfile() {
        UUID profileId = UUID.randomUUID();
        Profile existing = profile(UUID.randomUUID(), profileId, "Old");
        when(profileRepository.findAll()).thenReturn(List.of(existing));

        boolean result = profileService.delete(profileId);

        assertThat(result).isTrue();
        verify(profileRepository).delete(existing);
    }

    @Test
    void delete_throwsWhenProfileMissing() {
        when(profileRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> profileService.delete(UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Profile not found");
    }

    @Test
    void verifyPin_trueWhenPinMatches() {
        UUID profileId = UUID.randomUUID();
        Profile existing = profile(UUID.randomUUID(), profileId, "Old");
        existing.setPinHash("HASH");
        when(profileRepository.findAll()).thenReturn(List.of(existing));
        when(pinEncoder.matches("1234", "HASH")).thenReturn(true);

        assertThat(profileService.verifyPin(profileId, "1234")).isTrue();
    }

    @Test
    void verifyPin_falseWhenNoPinSet() {
        UUID profileId = UUID.randomUUID();
        Profile existing = profile(UUID.randomUUID(), profileId, "Old");
        existing.setPinHash(null);
        when(profileRepository.findAll()).thenReturn(List.of(existing));

        assertThat(profileService.verifyPin(profileId, "1234")).isFalse();
    }

    @Test
    void verifyPin_falseWhenProfileMissing() {
        when(profileRepository.findAll()).thenReturn(List.of());

        assertThat(profileService.verifyPin(UUID.randomUUID(), "1234")).isFalse();
    }
}
