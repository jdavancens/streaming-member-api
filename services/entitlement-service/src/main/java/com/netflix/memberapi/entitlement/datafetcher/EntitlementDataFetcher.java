package com.netflix.memberapi.entitlement.datafetcher;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.netflix.memberapi.entitlement.service.EntitlementService;
import com.netflix.memberapi.entitlement.service.EntitlementService.StreamEntitlement;
import com.netflix.memberapi.entitlement.service.EntitlementService.StreamSession;

import java.util.Map;

@DgsComponent
public class EntitlementDataFetcher {

    private final EntitlementService entitlementService;

    public EntitlementDataFetcher(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @DgsEntityFetcher(name = "Member")
    public Map<String, Object> resolveMemberEntity(Map<String, Object> values) {
        String memberId = (String) values.get("id");
        StreamEntitlement entitlement = entitlementService.canStream(memberId);
        return Map.of("id", memberId, "canStream", entitlement);
    }

    @DgsMutation
    public StreamSession acquireStream(@InputArgument String memberId,
                                       @InputArgument String deviceId) {
        return entitlementService.acquireStream(memberId, deviceId);
    }

    @DgsMutation
    public Boolean releaseStream(@InputArgument String memberId,
                                  @InputArgument String streamId) {
        return entitlementService.releaseStream(memberId, streamId);
    }

    @DgsMutation
    public Boolean heartbeatStream(@InputArgument String memberId,
                                    @InputArgument String streamId) {
        return entitlementService.heartbeat(memberId, streamId);
    }
}
