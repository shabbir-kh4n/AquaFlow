package com.shabbir.backend.household;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shabbir.backend.controller.HouseholdController;
import com.shabbir.backend.dto.HouseholdResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.CommunityAdmin;
import com.shabbir.backend.entity.Resident;
import com.shabbir.backend.repository.CommunityAdminRepository;
import com.shabbir.backend.repository.ResidentRepository;
import com.shabbir.backend.repository.SystemAdminRepository;
import com.shabbir.backend.security.CustomUserDetailsService;
import com.shabbir.backend.security.JwtService;
import com.shabbir.backend.security.ResidentPrincipal;
import com.shabbir.backend.security.SecurityConfig;
import com.shabbir.backend.security.UserPrincipal;
import com.shabbir.backend.service.ApartmentService;
import com.shabbir.backend.service.HouseholdService;

@WebMvcTest(HouseholdController.class)
@Import(SecurityConfig.class)
class HouseholdControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HouseholdService householdService;

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

    @Test
    void listReturnsHouseholdsForAdmin() throws Exception {
        Apartment apartment = Apartment.builder().id(1L).name("Sunrise Towers").admin(admin).build();
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(householdService.listForAdmin(apartment))
                .thenReturn(List.of(new HouseholdResponse(1L, "A-101", "1", null, Instant.now())));

        mockMvc.perform(get("/api/admin/households").with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].houseNumber").value("A-101"));
    }

    @Test
    void listIsForbiddenForResident() throws Exception {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();

        mockMvc.perform(get("/api/admin/households").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isForbidden());
    }
}
