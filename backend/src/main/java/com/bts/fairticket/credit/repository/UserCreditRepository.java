package com.bts.fairticket.credit.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bts.fairticket.credit.entity.UserCredit;

public interface UserCreditRepository extends JpaRepository<UserCredit, String> {
}
