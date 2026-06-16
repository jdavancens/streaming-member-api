package com.netflix.memberapi.profile.repository;

import com.netflix.memberapi.profile.model.Profile;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends CassandraRepository<Profile, UUID> {

    @Query("SELECT * FROM profiles WHERE member_id = ?0")
    List<Profile> findByMemberId(UUID memberId);

    @Query("SELECT * FROM profiles WHERE member_id = ?0 AND profile_id = ?1")
    Optional<Profile> findByMemberIdAndProfileId(UUID memberId, UUID profileId);

    @Query("SELECT COUNT(*) FROM profiles WHERE member_id = ?0")
    long countByMemberId(UUID memberId);
}
