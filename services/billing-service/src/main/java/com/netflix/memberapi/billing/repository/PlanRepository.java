package com.netflix.memberapi.billing.repository;

import com.netflix.memberapi.billing.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {}
