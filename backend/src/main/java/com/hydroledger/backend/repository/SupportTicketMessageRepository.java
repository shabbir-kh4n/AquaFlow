package com.hydroledger.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.SupportTicket;
import com.hydroledger.backend.entity.SupportTicketMessage;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, Long> {

    List<SupportTicketMessage> findByTicketOrderByCreatedAtAsc(SupportTicket ticket);

    Optional<SupportTicketMessage> findTopByTicketOrderByCreatedAtDesc(SupportTicket ticket);
}
