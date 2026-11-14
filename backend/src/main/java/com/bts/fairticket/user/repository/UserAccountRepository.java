package com.bts.fairticket.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bts.fairticket.user.entity.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {

	Optional<UserAccount> findByServiceIdAndExternalUserId(String serviceId, String externalUserId);
}
