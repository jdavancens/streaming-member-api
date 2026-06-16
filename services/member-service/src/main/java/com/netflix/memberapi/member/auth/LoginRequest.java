package com.netflix.memberapi.member.auth;

public record LoginRequest(String email, String password) {}
