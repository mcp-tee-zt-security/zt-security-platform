package com.zt.security.stitch;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;

/** Versioned connector contract: source permissions come only from an authenticated sync actor. */
public final class StitchContracts {
    private StitchContracts(){}
    public record Grant(@NotBlank @Pattern(regexp="SUBJECT|GROUP|AI_SUBJECT") String principalKind,
        @NotBlank @Size(max=512) String principalId,@NotBlank @Pattern(regexp="READ|SEARCH") String permission,
        @NotBlank @Pattern(regexp="ALLOW|DENY") String effect){}
    public record Chunk(@NotBlank @Size(max=16000) String content,@Size(max=1536) List<@NotNull Double> embedding){}
    public record Resource(@NotBlank @Pattern(regexp="[A-Za-z0-9_.:-]{1,128}") String resourceId,
        @Pattern(regexp="[A-Za-z0-9_.:-]{1,128}") String parentId,
        @NotBlank @Pattern(regexp="FOLDER|CHANNEL|FILE|ATTACHMENT|MESSAGE") String kind,
        @NotBlank @Size(max=256) String title,@NotBlank @Size(max=512) String ownerSubject,
        @NotBlank @Pattern(regexp="ALLOW|DENY") String aiAccess,@Positive long sourceVersion,
        Instant purgeAfter,@Size(max=64000) String content,
        @NotNull @Size(max=500) List<@Valid Grant> acl,@NotNull @Size(max=128) List<@Valid Chunk> chunks,
        @Size(max=1398104) String fileBase64){}
    public record Delegate(@NotBlank @Size(max=512) String aiSubject){}
    public record Subject(@NotBlank @Size(max=512) String subject,@NotNull @Size(max=100) Set<@NotBlank @Size(max=256) String> groups,boolean active,@Positive long sourceVersion){}
    public record Retrieve(@NotBlank @Size(max=128) String resourceId,UUID sessionId){}
    public record Search(@NotBlank @Size(max=256) String query,UUID sessionId,
        @Size(max=1536) List<@NotNull Double> queryEmbedding){}
    public record Receipt(@NotBlank @Size(max=256) String receiptId){}
    public record Revocation(@NotBlank @Size(max=512) String jti,@NotNull @Future Instant expiresAt){}
    public record Retention(@Min(1) @Max(365) int receiptTtlDays){}
    public record Checkpoint(boolean ready,@Positive long expectedAclVersion){}
}
