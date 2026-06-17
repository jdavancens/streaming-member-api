package com.streaming.memberapi.profile.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileTest {

    @Test
    void isHasPinLock_falseWhenPinHashNull() {
        Profile p = new Profile();
        p.setPinHash(null);
        assertThat(p.isHasPinLock()).isFalse();
    }

    @Test
    void isHasPinLock_falseWhenPinHashBlank() {
        Profile p = new Profile();
        p.setPinHash("   ");
        assertThat(p.isHasPinLock()).isFalse();
    }

    @Test
    void isHasPinLock_trueWhenPinHashPresent() {
        Profile p = new Profile();
        p.setPinHash("HASH");
        assertThat(p.isHasPinLock()).isTrue();
    }
}
