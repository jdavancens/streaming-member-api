package com.netflix.memberapi.billing.config;

import com.netflix.memberapi.billing.model.Plan;
import com.netflix.memberapi.billing.repository.PlanRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final PlanRepository planRepository;

    public DataInitializer(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Override
    public void run(String... args) {
        if (planRepository.count() > 0) return;

        Plan mobile = new Plan();
        mobile.setName("MOBILE");
        mobile.setMonthlyPrice(new BigDecimal("6.99"));
        mobile.setMaxStreams(1);
        mobile.setMaxDownloads(1);
        mobile.setVideoQuality("SD");

        Plan basic = new Plan();
        basic.setName("BASIC");
        basic.setMonthlyPrice(new BigDecimal("9.99"));
        basic.setMaxStreams(1);
        basic.setMaxDownloads(1);
        basic.setVideoQuality("HD");

        Plan standard = new Plan();
        standard.setName("STANDARD");
        standard.setMonthlyPrice(new BigDecimal("15.49"));
        standard.setMaxStreams(2);
        standard.setMaxDownloads(2);
        standard.setVideoQuality("HD");

        Plan premium = new Plan();
        premium.setName("PREMIUM");
        premium.setMonthlyPrice(new BigDecimal("22.99"));
        premium.setMaxStreams(4);
        premium.setMaxDownloads(6);
        premium.setVideoQuality("UHD");

        planRepository.saveAll(List.of(mobile, basic, standard, premium));
    }
}
