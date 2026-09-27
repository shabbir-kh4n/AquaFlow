package com.hydroledger.backend.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hydroledger.backend.dto.AddTicketMessageRequest;
import com.hydroledger.backend.dto.CreateSupportTicketRequest;
import com.hydroledger.backend.dto.SupportTicketDetailResponse;
import com.hydroledger.backend.dto.SupportTicketMessageResponse;
import com.hydroledger.backend.dto.SupportTicketResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.SupportTicket;
import com.hydroledger.backend.entity.SupportTicketMessage;
import com.hydroledger.backend.entity.SupportTicketRole;
import com.hydroledger.backend.entity.SupportTicketStatus;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.HouseholdRepository;
import com.hydroledger.backend.repository.SupportTicketMessageRepository;
import com.hydroledger.backend.repository.SupportTicketRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private final SupportTicketRepository supportTicketRepository;
    private final SupportTicketMessageRepository supportTicketMessageRepository;
    private final HouseholdRepository householdRepository;
    private final ApartmentService apartmentService;
    private final EmailService emailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional
    public SupportTicketResponse createFromResident(Resident resident, CreateSupportTicketRequest request) {
        Household household = resident.getHousehold();
        if (household == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No household is linked to this account yet");
        }
        // Same lazy-loading precaution as AlertEngineService.listForResident: resident.getHousehold()
        // may be a detached lazy proxy loaded during JWT authentication.
        Household fetched = householdRepository.findWithApartmentById(household.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Household not found"));

        boolean toAdmin = Boolean.TRUE.equals(request.toAdmin());
        boolean toSystemAdmin = Boolean.TRUE.equals(request.toSystemAdmin());
        if (!toAdmin && !toSystemAdmin) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Select at least one recipient (Community Admin and/or System Admin)");
        }

        SupportTicket ticket = SupportTicket.builder()
                .apartment(fetched.getApartment())
                .raisedByRole(SupportTicketRole.RESIDENT)
                .resident(resident)
                .subject(request.subject())
                .toCommunityAdmin(toAdmin)
                .toSystemAdmin(toSystemAdmin)
                .build();
        ticket = supportTicketRepository.save(ticket);

        addMessage(ticket, SupportTicketRole.RESIDENT, resident.getFullName(), request.message());

        return toResponse(ticket, resident.getFullName());
    }

    @Transactional
    public SupportTicketResponse createFromAdmin(CommunityAdmin admin, CreateSupportTicketRequest request) {
        Apartment apartment = apartmentService.getOrCreate(admin);

        SupportTicket ticket = SupportTicket.builder()
                .apartment(apartment)
                .raisedByRole(SupportTicketRole.ADMIN)
                .communityAdmin(admin)
                .subject(request.subject())
                .build();
        ticket = supportTicketRepository.save(ticket);

        addMessage(ticket, SupportTicketRole.ADMIN, admin.getFullName(), request.message());

        return toResponse(ticket, admin.getFullName());
    }

    public List<SupportTicketResponse> listForResident(Resident resident) {
        return supportTicketRepository.findByResidentOrderByCreatedAtDesc(resident).stream()
                .map(ticket -> toResponse(ticket, resident.getFullName()))
                .toList();
    }

    public List<SupportTicketResponse> listForAdmin(CommunityAdmin admin) {
        return supportTicketRepository.findByCommunityAdminOrderByCreatedAtDesc(admin).stream()
                .map(ticket -> toResponse(ticket, admin.getFullName()))
                .toList();
    }

    public List<SupportTicketResponse> listInboxForAdmin(CommunityAdmin admin) {
        Apartment apartment = apartmentService.getOrCreate(admin);
        return supportTicketRepository
                .findByApartmentAndRaisedByRoleAndToCommunityAdminTrueOrderByCreatedAtDesc(
                        apartment, SupportTicketRole.RESIDENT)
                .stream()
                .map(ticket -> toResponse(ticket, raiserName(ticket)))
                .toList();
    }

    public List<SupportTicketResponse> listForSystemAdmin(SupportTicketStatus statusFilter) {
        List<SupportTicket> tickets = statusFilter == null
                ? supportTicketRepository.findAllByOrderByCreatedAtDesc()
                : supportTicketRepository.findByStatusOrderByCreatedAtDesc(statusFilter);
        return tickets.stream().map(ticket -> toResponse(ticket, raiserName(ticket))).toList();
    }

    public SupportTicketDetailResponse getDetailForResident(Long id, Resident resident) {
        SupportTicket ticket = supportTicketRepository.findByIdAndResident(id, resident)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Support ticket not found"));
        return toDetail(ticket, resident.getFullName());
    }

    public SupportTicketDetailResponse getDetailForAdmin(Long id, CommunityAdmin admin) {
        SupportTicket ticket = supportTicketRepository.findByIdAndCommunityAdmin(id, admin)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Support ticket not found"));
        return toDetail(ticket, admin.getFullName());
    }

    public SupportTicketDetailResponse getInboxDetailForAdmin(Long id, CommunityAdmin admin) {
        Apartment apartment = apartmentService.getOrCreate(admin);
        SupportTicket ticket = supportTicketRepository.findByIdAndApartmentAndToCommunityAdminTrue(id, apartment)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Support ticket not found"));
        return toDetail(ticket, raiserName(ticket));
    }

    public SupportTicketDetailResponse getDetailForSystemAdmin(Long id) {
        SupportTicket ticket = requireTicket(id);
        return toDetail(ticket, raiserName(ticket));
    }

    @Transactional
    public SupportTicketDetailResponse addMessageFromResident(Long id, Resident resident, AddTicketMessageRequest request) {
        SupportTicket ticket = supportTicketRepository.findByIdAndResident(id, resident)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Support ticket not found"));
        reopenIfNeeded(ticket);
        addMessage(ticket, SupportTicketRole.RESIDENT, resident.getFullName(), request.message());
        return toDetail(ticket, resident.getFullName());
    }

    @Transactional
    public SupportTicketDetailResponse addMessageFromAdmin(Long id, CommunityAdmin admin, AddTicketMessageRequest request) {
        SupportTicket ticket = supportTicketRepository.findByIdAndCommunityAdmin(id, admin)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Support ticket not found"));
        reopenIfNeeded(ticket);
        addMessage(ticket, SupportTicketRole.ADMIN, admin.getFullName(), request.message());
        return toDetail(ticket, admin.getFullName());
    }

    @Transactional
    public SupportTicketDetailResponse addInboxMessageFromAdmin(Long id, CommunityAdmin admin, AddTicketMessageRequest request) {
        Apartment apartment = apartmentService.getOrCreate(admin);
        SupportTicket ticket = supportTicketRepository.findByIdAndApartmentAndToCommunityAdminTrue(id, apartment)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Support ticket not found"));
        if (ticket.getStatus() == SupportTicketStatus.OPEN) {
            ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
        }
        addMessage(ticket, SupportTicketRole.ADMIN, admin.getFullName(), request.message());
        notifyRaiser(ticket, "Your community admin replied to your support ticket \"" + ticket.getSubject() + "\": "
                + request.message());
        return toDetail(ticket, raiserName(ticket));
    }

    @Transactional
    public SupportTicketDetailResponse addMessageFromSystemAdmin(Long id, AddTicketMessageRequest request) {
        SupportTicket ticket = requireTicket(id);
        if (ticket.getStatus() == SupportTicketStatus.OPEN) {
            ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
        }
        addMessage(ticket, SupportTicketRole.SYSTEM_ADMIN, "System Admin", request.message());
        notifyRaiser(ticket, "A System Admin replied to your support ticket \"" + ticket.getSubject() + "\": "
                + request.message());
        return toDetail(ticket, raiserName(ticket));
    }

    @Transactional
    public SupportTicketResponse updateStatus(Long id, SupportTicketStatus status) {
        SupportTicket ticket = requireTicket(id);
        ticket.setStatus(status);
        ticket.setResolvedAt(status == SupportTicketStatus.RESOLVED ? Instant.now() : null);
        supportTicketRepository.save(ticket);

        notifyRaiser(ticket, "Your support ticket \"" + ticket.getSubject() + "\" status changed to "
                + status.name().replace('_', ' ') + ".");

        return toResponse(ticket, raiserName(ticket));
    }

    private void reopenIfNeeded(SupportTicket ticket) {
        if (ticket.getStatus() == SupportTicketStatus.RESOLVED || ticket.getStatus() == SupportTicketStatus.CLOSED) {
            ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
            ticket.setResolvedAt(null);
        }
    }

    private void addMessage(SupportTicket ticket, SupportTicketRole senderRole, String senderName, String message) {
        SupportTicketMessage ticketMessage = SupportTicketMessage.builder()
                .ticket(ticket)
                .senderRole(senderRole)
                .senderName(senderName)
                .message(message)
                .build();
        supportTicketMessageRepository.save(ticketMessage);
    }

    private void notifyRaiser(SupportTicket ticket, String updateMessage) {
        String link = ticket.getRaisedByRole() == SupportTicketRole.RESIDENT
                ? frontendUrl + "/resident/support"
                : frontendUrl + "/admin/support";

        if (ticket.getRaisedByRole() == SupportTicketRole.RESIDENT && ticket.getResident() != null) {
            Resident resident = ticket.getResident();
            emailService.sendSupportTicketUpdateEmail(resident.getEmail(), resident.getFullName(),
                    ticket.getSubject(), updateMessage, link);
        } else if (ticket.getCommunityAdmin() != null) {
            CommunityAdmin admin = ticket.getCommunityAdmin();
            emailService.sendSupportTicketUpdateEmail(admin.getEmail(), admin.getFullName(),
                    ticket.getSubject(), updateMessage, link);
        }
    }

    private SupportTicket requireTicket(Long id) {
        return supportTicketRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Support ticket not found"));
    }

    private String raiserName(SupportTicket ticket) {
        return ticket.getRaisedByRole() == SupportTicketRole.RESIDENT
                ? (ticket.getResident() != null ? ticket.getResident().getFullName() : null)
                : (ticket.getCommunityAdmin() != null ? ticket.getCommunityAdmin().getFullName() : null);
    }

    private SupportTicketResponse toResponse(SupportTicket ticket, String raiserName) {
        String preview = supportTicketMessageRepository.findTopByTicketOrderByCreatedAtDesc(ticket)
                .map(SupportTicketMessage::getMessage)
                .orElse(null);

        return new SupportTicketResponse(
                ticket.getId(),
                ticket.getApartment().getId(),
                ticket.getApartment().getName(),
                ticket.getRaisedByRole(),
                raiserName,
                ticket.getSubject(),
                ticket.isToCommunityAdmin(),
                ticket.isToSystemAdmin(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt(),
                preview
        );
    }

    private SupportTicketDetailResponse toDetail(SupportTicket ticket, String raiserName) {
        List<SupportTicketMessageResponse> messages = supportTicketMessageRepository
                .findByTicketOrderByCreatedAtAsc(ticket).stream()
                .map(m -> new SupportTicketMessageResponse(m.getId(), m.getSenderRole(), m.getSenderName(),
                        m.getMessage(), m.getCreatedAt()))
                .toList();

        return new SupportTicketDetailResponse(
                ticket.getId(),
                ticket.getApartment().getId(),
                ticket.getApartment().getName(),
                ticket.getRaisedByRole(),
                raiserName,
                ticket.getSubject(),
                ticket.isToCommunityAdmin(),
                ticket.isToSystemAdmin(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt(),
                messages
        );
    }
}
