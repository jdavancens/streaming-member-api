package com.streaming.memberapi.member.datafetcher;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.streaming.memberapi.member.model.Member;
import com.streaming.memberapi.member.service.MemberService;

import java.util.Map;
import java.util.UUID;

@DgsComponent
public class MemberDataFetcher {

    private final MemberService memberService;

    public MemberDataFetcher(MemberService memberService) {
        this.memberService = memberService;
    }

    @DgsQuery
    public Member member(@InputArgument String id) {
        return memberService.findById(UUID.fromString(id)).orElse(null);
    }

    @DgsMutation
    public Map<String, Object> register(@InputArgument Map<String, String> input) {
        Member member = memberService.register(
            input.get("email"),
            input.get("password"),
            input.get("fullName"),
            input.get("country")
        );
        return Map.of("member", member);
    }

    @DgsEntityFetcher(name = "Member")
    public Member resolveMemberEntity(Map<String, Object> values) {
        String id = (String) values.get("id");
        return memberService.findById(UUID.fromString(id)).orElse(null);
    }
}
