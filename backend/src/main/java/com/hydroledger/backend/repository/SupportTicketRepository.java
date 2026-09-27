package com.hydroledger.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.SupportTicket;
import com.hydroledger.backend.entity.SupportTicketRole;
import com.hydroledger.backend.entity.SupportTicketStatus;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByApartmentOrderByCreatedAtDesc(Apartment apartment);

    List<SupportTicket> findByResidentOrderByCreatedAtDesc(Resident resident);

    List<SupportTicket> findByCommunityAdminOrderByCreatedAtDesc(CommunityAdmin communityAdmin);

    Optional<SupportTicket> findByIdAndResident(Long id, Resident resident);

    Optional<SupportTicket> findByIdAndCommunityAdmin(Long id, CommunityAdmin communityAdmin);

    List<SupportTicket> findAllByOrderByCreatedAtDesc();

    List<SupportTicket> findByStatusOrderByCreatedAtDesc(SupportTicketStatus status);

    // Community admin's "Resident Tickets" inbox: tickets residents in their own apartment raised
    // and specifically addressed to the admin - separate from the admin's own tickets to System Admin.
    List<SupportTicket> findByApartmentAndRaisedByRoleAndToCommunityAdminTrueOrderByCreatedAtDesc(
            Apartment apartment, SupportTicketRole raisedByRole);

    Optional<SupportTicket> findByIdAndApartmentAndToCommunityAdminTrue(Long id, Apartment apartment);
}
