package com.bts.fairticket.credit.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bts.fairticket.credit.entity.CreditHistory;

public interface CreditHistoryRepository extends JpaRepository<CreditHistory, Long> {
}
