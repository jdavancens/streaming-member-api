package com.streaming.memberapi.member.repository;

import com.streaming.memberapi.member.model.Member;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberRepository extends CassandraRepository<Member, UUID> {

    @Query("SELECT * FROM members WHERE email = ?0 ALLOW FILTERING")
    Optional<Member> findByEmail(String email);
}
