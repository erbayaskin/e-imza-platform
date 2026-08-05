package io.github.erbayaskin.eimza.api.signing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/signing-sessions")
public class SigningSessionController {

    private final SigningSessionService service;
    private final io.github.erbayaskin.eimza.api.security.TenantAccess tenantAccess;
    private final SigningWorkflowService workflow;

    public SigningSessionController(
            SigningSessionService service,
            io.github.erbayaskin.eimza.api.security.TenantAccess tenantAccess,
            SigningWorkflowService workflow) {
        this.service = service;
        this.tenantAccess = tenantAccess;
        this.workflow = workflow;
    }

    @PostMapping("/{sessionId}/manifest")
    SigningManifestResponse prepareManifest(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID sessionId,
            Authentication authentication,
            @Valid @RequestBody PrepareSigningManifestRequest request) {
        tenantAccess.requireRequestedTenant(tenantId, authentication);
        return workflow.prepareManifest(tenantId, sessionId, request);
    }

    @PostMapping("/{sessionId}/agent-connected")
    SigningSessionResponse agentConnected(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID sessionId,
            Authentication authentication) {
        tenantAccess.requireRequestedTenant(tenantId, authentication);
        return workflow.agentConnected(tenantId, sessionId);
    }

    @PostMapping("/{sessionId}/approve")
    SigningSessionResponse approve(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID sessionId,
            Authentication authentication) {
        tenantAccess.requireRequestedTenant(tenantId, authentication);
        return workflow.approve(tenantId, sessionId);
    }

    @PostMapping("/{sessionId}/complete")
    SignatureArtifactResponse complete(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID sessionId,
            Authentication authentication,
            @Valid @RequestBody CompleteSigningSessionRequest request) {
        tenantAccess.requireRequestedTenant(tenantId, authentication);
        return workflow.complete(tenantId, sessionId, request);
    }

    @PostMapping("/{sessionId}/server-sign")
    SignatureArtifactResponse serverSign(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID sessionId,
            Authentication authentication,
            @Valid @RequestBody ServerSideSigningRequest request) {
        tenantAccess.requireRequestedTenant(tenantId, authentication);
        try {
            return workflow.serverSign(tenantId, sessionId, request);
        } finally {
            request.destroy();
        }
    }

    @GetMapping("/{sessionId}/artifact")
    SignatureArtifactResponse artifact(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID sessionId,
            Authentication authentication) {
        tenantAccess.requireRequestedTenant(tenantId, authentication);
        return workflow.artifact(tenantId, sessionId);
    }

    @PostMapping
    ResponseEntity<SigningSessionResponse> create(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("Idempotency-Key") @NotBlank
                    @jakarta.validation.constraints.Size(min = 8, max = 128)
                    String idempotencyKey,
            Authentication authentication,
            @Valid @RequestBody CreateSigningSessionRequest request) {
        tenantAccess.requireRequestedTenant(tenantId, authentication);
        var subjectId =
                authentication == null ? "local-development-user" : authentication.getName();
        var response = service.create(tenantId, subjectId, idempotencyKey, request);
        return ResponseEntity.created(
                        URI.create("/api/v1/signing-sessions/" + response.sessionId()))
                .body(response);
    }

    @GetMapping("/{sessionId}")
    SigningSessionResponse get(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID sessionId,
            Authentication authentication) {
        tenantAccess.requireRequestedTenant(tenantId, authentication);
        return service.get(tenantId, sessionId);
    }

    @PostMapping("/{sessionId}/cancel")
    SigningSessionResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID sessionId,
            Authentication authentication) {
        tenantAccess.requireRequestedTenant(tenantId, authentication);
        return service.cancel(tenantId, sessionId);
    }
}
