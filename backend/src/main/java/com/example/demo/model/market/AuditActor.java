package com.example.demo.model.market;

import java.util.Objects;

/** Autor de una entrada de auditoría: un usuario (con su id) o el sistema (sin id). */
public record AuditActor(AuditActorType type, Long userId) {

    public AuditActor {
        Objects.requireNonNull(type, "type must not be null");
        if (type == AuditActorType.USER && userId == null) {
            throw new IllegalArgumentException("USER actor requires a userId");
        }
        if (type == AuditActorType.SYSTEM && userId != null) {
            throw new IllegalArgumentException("SYSTEM actor must not have a userId");
        }
    }

    public static AuditActor system() {
        return new AuditActor(AuditActorType.SYSTEM, null);
    }

    public static AuditActor user(Long userId) {
        return new AuditActor(AuditActorType.USER, userId);
    }
}
