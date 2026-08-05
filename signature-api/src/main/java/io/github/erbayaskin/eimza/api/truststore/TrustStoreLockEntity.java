package io.github.erbayaskin.eimza.api.truststore;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "trust_store_lock")
class TrustStoreLockEntity {

    @Id
    private Integer id;

    @Version
    private long lockVersion;

    protected TrustStoreLockEntity() {
    }
}
