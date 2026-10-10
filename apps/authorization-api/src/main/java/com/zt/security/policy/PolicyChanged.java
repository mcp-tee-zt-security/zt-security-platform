package com.zt.security.policy;
import java.util.UUID;
/** Emitted in the same transaction as a policy mutation. Null workspace means tenant policy. */
public record PolicyChanged(UUID tenant,UUID workspace) {}
