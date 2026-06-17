package com.streaming.memberapi.billing.repository;

import com.streaming.memberapi.billing.model.Plan;
import com.streaming.memberapi.billing.model.Subscription;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the real MySQL datastore layer. Spins up a MySQL container,
 * lets Hibernate create the schema from the JPA entities, and exercises the
 * {@link PlanRepository} / {@link SubscriptionRepository} including the
 * Subscription→Plan relationship and the custom finder.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class BillingRepositoryIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL =
        new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("billing_db");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    PlanRepository planRepository;
    @Autowired
    SubscriptionRepository subscriptionRepository;

    private Plan newPlan(String name, int maxStreams) {
        Plan plan = new Plan();
        plan.setName(name);
        plan.setMonthlyPrice(new BigDecimal("15.49"));
        plan.setMaxStreams(maxStreams);
        plan.setMaxDownloads(2);
        plan.setVideoQuality("HD");
        return plan;
    }

    private Subscription newSubscription(UUID memberId, Plan plan, String status) {
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setMemberId(memberId);
        subscription.setPlan(plan);
        subscription.setStatus(status);
        subscription.setPeriodStart(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        subscription.setPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS));
        return subscription;
    }

    @Test
    void persistsPlanAndReadsBack() {
        Plan saved = planRepository.save(newPlan("STANDARD", 2));

        assertThat(saved.getId()).isNotNull();
        Optional<Plan> found = planRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("STANDARD");
        assertThat(found.get().getMonthlyPrice()).isEqualByComparingTo("15.49");
        assertThat(found.get().getMaxStreams()).isEqualTo(2);
    }

    @Test
    void persistsSubscriptionWithPlanRelationship() {
        Plan plan = planRepository.save(newPlan("PREMIUM", 4));
        UUID memberId = UUID.randomUUID();

        Subscription saved = subscriptionRepository.save(newSubscription(memberId, plan, "ACTIVE"));

        Optional<Subscription> found = subscriptionRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getMemberId()).isEqualTo(memberId);
        assertThat(found.get().getStatus()).isEqualTo("ACTIVE");
        // The EAGER @ManyToOne relationship must resolve to the persisted plan.
        assertThat(found.get().getPlan()).isNotNull();
        assertThat(found.get().getPlan().getName()).isEqualTo("PREMIUM");
        assertThat(found.get().getPlan().getMaxStreams()).isEqualTo(4);
    }

    @Test
    void findByMemberIdAndStatus_returnsActiveSubscription() {
        Plan plan = planRepository.save(newPlan("BASIC", 1));
        UUID memberId = UUID.randomUUID();
        subscriptionRepository.save(newSubscription(memberId, plan, "ACTIVE"));
        subscriptionRepository.save(newSubscription(memberId, plan, "CANCELLED"));

        Optional<Subscription> active =
            subscriptionRepository.findByMemberIdAndStatus(memberId, "ACTIVE");

        assertThat(active).isPresent();
        assertThat(active.get().getStatus()).isEqualTo("ACTIVE");
        assertThat(active.get().getMemberId()).isEqualTo(memberId);
    }

    @Test
    void findByMemberIdAndStatus_returnsEmptyWhenNoMatch() {
        assertThat(subscriptionRepository.findByMemberIdAndStatus(UUID.randomUUID(), "ACTIVE"))
            .isEmpty();
    }
}
