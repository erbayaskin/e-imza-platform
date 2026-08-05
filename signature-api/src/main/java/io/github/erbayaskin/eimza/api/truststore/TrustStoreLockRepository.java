package io.github.erbayaskin.eimza.api.truststore;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TrustStoreLockRepository extends JpaRepository<TrustStoreLockEntity, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lockRow from TrustStoreLockEntity lockRow where lockRow.id = :id")
    TrustStoreLockEntity lockById(@Param("id") Integer id);
}
