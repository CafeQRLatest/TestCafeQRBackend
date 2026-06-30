package com.restaurant.pos.print.repository;

import com.restaurant.pos.print.domain.PrintJobAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PrintJobAttemptRepository extends JpaRepository<PrintJobAttempt, UUID> {
    List<PrintJobAttempt> findAllByPrintJobIdOrderByCreatedAtAsc(UUID printJobId);
    List<PrintJobAttempt> findAllByPrintJobIdInOrderByCreatedAtAsc(Collection<UUID> printJobIds);
}
