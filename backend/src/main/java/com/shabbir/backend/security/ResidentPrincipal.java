package com.shabbir.backend.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.shabbir.backend.entity.Resident;

public class ResidentPrincipal implements UserDetails {

    private final Resident resident;

    public ResidentPrincipal(Resident resident) {
        this.resident = resident;
    }

    public Resident getUser() {
        return resident;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_RESIDENT"));
    }

    @Override
    public String getPassword() {
        return resident.getPassword();
    }

    @Override
    public String getUsername() {
        return resident.getEmail();
    }
}
