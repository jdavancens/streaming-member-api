package com.netflix.memberapi.profile.datafetcher;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.netflix.memberapi.profile.model.Profile;
import com.netflix.memberapi.profile.service.ProfileService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@DgsComponent
public class ProfileDataFetcher {

    private final ProfileService profileService;

    public ProfileDataFetcher(ProfileService profileService) {
        this.profileService = profileService;
    }

    @DgsEntityFetcher(name = "Member")
    public Map<String, Object> resolveMemberEntity(Map<String, Object> values) {
        String memberId = (String) values.get("id");
        List<Profile> profiles = profileService.findByMemberId(UUID.fromString(memberId));
        return Map.of("id", memberId, "profiles", profiles);
    }

    @DgsMutation
    public Profile createProfile(@InputArgument String memberId,
                                 @InputArgument Map<String, Object> input) {
        return profileService.create(UUID.fromString(memberId), input);
    }

    @DgsMutation
    public Profile updateProfile(@InputArgument String profileId,
                                 @InputArgument Map<String, Object> input) {
        return profileService.update(UUID.fromString(profileId), input);
    }

    @DgsMutation
    public Boolean deleteProfile(@InputArgument String profileId) {
        return profileService.delete(UUID.fromString(profileId));
    }

    @DgsMutation
    public Boolean verifyProfilePin(@InputArgument String profileId,
                                    @InputArgument String pin) {
        return profileService.verifyPin(UUID.fromString(profileId), pin);
    }
}
