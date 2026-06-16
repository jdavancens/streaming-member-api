package com.streaming.memberapi.billing.repository;

import com.streaming.memberapi.billing.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByMemberIdAndStatus(UUID memberId, String status);
}
