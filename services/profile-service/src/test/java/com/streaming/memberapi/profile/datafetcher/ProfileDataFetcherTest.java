package com.streaming.memberapi.profile.datafetcher;

import com.streaming.memberapi.profile.model.Profile;
import com.streaming.memberapi.profile.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileDataFetcherTest {

    @Mock
    ProfileService profileService;

    @InjectMocks
    ProfileDataFetcher dataFetcher;

    @Test
    void resolveMemberEntity_returnsProfilesForMember() {
        UUID memberId = UUID.randomUUID();
        Profile p = new Profile();
        p.setProfileId(UUID.randomUUID());
        List<Profile> profiles = List.of(p);
        when(profileService.findByMemberId(memberId)).thenReturn(profiles);

        Map<String, Object> result = dataFetcher.resolveMemberEntity(Map.of("id", memberId.toString()));

        assertThat(result).containsEntry("id", memberId.toString());
        assertThat(result.get("profiles")).isEqualTo(profiles);
    }

    @Test
    void createProfile_delegatesToService() {
        UUID memberId = UUID.randomUUID();
        Profile created = new Profile();
        Map<String, Object> input = Map.of("name", "Alice");
        when(profileService.create(eq(memberId), any())).thenReturn(created);

        assertThat(dataFetcher.createProfile(memberId.toString(), input)).isEqualTo(created);
    }

    @Test
    void updateProfile_delegatesToService() {
        UUID profileId = UUID.randomUUID();
        Profile updated = new Profile();
        Map<String, Object> input = Map.of("name", "Bob");
        when(profileService.update(eq(profileId), any())).thenReturn(updated);

        assertThat(dataFetcher.updateProfile(profileId.toString(), input)).isEqualTo(updated);
    }

    @Test
    void deleteProfile_delegatesToService() {
        UUID profileId = UUID.randomUUID();
        when(profileService.delete(profileId)).thenReturn(true);

        assertThat(dataFetcher.deleteProfile(profileId.toString())).isTrue();
    }

    @Test
    void verifyProfilePin_delegatesToService() {
        UUID profileId = UUID.randomUUID();
        when(profileService.verifyPin(profileId, "1234")).thenReturn(true);

        assertThat(dataFetcher.verifyProfilePin(profileId.toString(), "1234")).isTrue();
    }
}
