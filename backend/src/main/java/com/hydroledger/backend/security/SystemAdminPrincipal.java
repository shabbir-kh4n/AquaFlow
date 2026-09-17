package com.hydroledger.backend.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.hydroledger.backend.entity.SystemAdmin;

public class SystemAdminPrincipal implements UserDetails {

    private final SystemAdmin systemAdmin;

    public SystemAdminPrincipal(SystemAdmin systemAdmin) {
        this.systemAdmin = systemAdmin;
    }

    public SystemAdmin getUser() {
        return systemAdmin;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"));
    }

    @Override
    public String getPassword() {
        return systemAdmin.getPassword();
    }

    @Override
    public String getUsername() {
        return systemAdmin.getEmail();
    }
}
