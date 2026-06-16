package com.netflix.memberapi.billing.repository;

import com.netflix.memberapi.billing.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByMemberIdAndStatus(UUID memberId, String status);
}
