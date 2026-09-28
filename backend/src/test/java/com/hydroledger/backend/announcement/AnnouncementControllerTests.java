package com.hydroledger.backend.announcement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hydroledger.backend.controller.AnnouncementController;
import com.hydroledger.backend.controller.ResidentAnnouncementController;
import com.hydroledger.backend.dto.AnnouncementRequest;
import com.hydroledger.backend.dto.AnnouncementResponse;
import com.hydroledger.backend.entity.AnnouncementCategory;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;
import com.hydroledger.backend.security.CustomUserDetailsService;
import com.hydroledger.backend.security.JwtService;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.security.SecurityConfig;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.AnnouncementService;
import com.hydroledger.backend.service.ApartmentService;

@WebMvcTest({AnnouncementController.class, ResidentAnnouncementController.class})
@Import(SecurityConfig.class)
class AnnouncementControllerTests {

    private static final String VALID_BODY =
            "{\"title\":\"Water shutdown\",\"body\":\"Tank cleaning on Sunday.\",\"category\":\"WATER_SUPPLY\",\"pinned\":true}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnnouncementService announcementService;

    @MockitoBean
    private ApartmentService apartmentService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CommunityAdminRepository communityAdminRepository;

    @MockitoBean
    private ResidentRepository residentRepository;

    @MockitoBean
    private SystemAdminRepository systemAdminRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private final CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").build();
    private final Apartment apartment = Apartment.builder().id(1L).name("Sunrise Towers").admin(admin).build();
    private final Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").createdBy(admin).build();

    private AnnouncementResponse sample() {
        return new AnnouncementResponse(7L, "Water shutdown", "Tank cleaning on Sunday.", AnnouncementCategory.WATER_SUPPLY,
                true, Instant.now(), Instant.now(), null, false);
    }

    @Test
    void listReturns200() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(announcementService.listForAdmin(apartment)).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/admin/announcements").with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].category").value("WATER_SUPPLY"));
    }

    @Test
    void createReturns201() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(announcementService.create(eq(apartment), any(AnnouncementRequest.class))).thenReturn(sample());

        mockMvc.perform(post("/api/admin/announcements")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Water shutdown"));
    }

    @Test
    void createRejectsBlankTitleAndMissingCategory() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);

        mockMvc.perform(post("/api/admin/announcements")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \",\"body\":\"Body\"}"))
                .andExpect(status().isBadRequest());
        verify(announcementService, never()).create(any(), any());
    }

    @Test
    void updateReturns200() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(announcementService.update(eq(apartment), eq(7L), any(AnnouncementRequest.class))).thenReturn(sample());

        mockMvc.perform(put("/api/admin/announcements/7")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void updateReturns404WhenNotOwned() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(announcementService.update(eq(apartment), eq(99L), any(AnnouncementRequest.class)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Announcement not found"));

        mockMvc.perform(put("/api/admin/announcements/99")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);

        mockMvc.perform(delete("/api/admin/announcements/7").with(user(new UserPrincipal(admin))))
                .andExpect(status().isNoContent());
        verify(announcementService).delete(apartment, 7L);
    }

    @Test
    void deleteReturns404WhenNotOwned() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        doThrow(new ApiException(HttpStatus.NOT_FOUND, "Announcement not found"))
                .when(announcementService).delete(apartment, 99L);

        mockMvc.perform(delete("/api/admin/announcements/99").with(user(new UserPrincipal(admin))))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminEndpointsAreForbiddenForResident() throws Exception {
        mockMvc.perform(get("/api/admin/announcements").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/announcements")
                        .with(user(new ResidentPrincipal(resident)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
        verify(announcementService, never()).create(any(), any());
    }

    @Test
    void residentListReturns200() throws Exception {
        when(announcementService.listForResident(any(Resident.class))).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/resident/announcements").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Water shutdown"));
    }

    @Test
    void residentListIsForbiddenForAdmin() throws Exception {
        mockMvc.perform(get("/api/resident/announcements").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }
}
