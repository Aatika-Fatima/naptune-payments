package org.naptune.payments.payout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;

@Embeddable
public record ApiIdempotencyId(@Column(name="client_id") String clientId,
                              @Column(name="idempotency_key") String idempotencyKey)implements Serializable {

}