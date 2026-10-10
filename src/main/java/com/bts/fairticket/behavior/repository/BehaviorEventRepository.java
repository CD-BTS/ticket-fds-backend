package com.bts.fairticket.behavior.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bts.fairticket.behavior.entity.BehaviorEvent;

public interface BehaviorEventRepository extends JpaRepository<BehaviorEvent, Long> {
}
