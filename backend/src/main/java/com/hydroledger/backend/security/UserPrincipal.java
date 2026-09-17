package com.hydroledger.backend.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.CommunityAdminStatus;

public class UserPrincipal implements UserDetails {

    private final CommunityAdmin admin;

    public UserPrincipal(CommunityAdmin admin) {
        this.admin = admin;
    }

    public CommunityAdmin getUser() {
        return admin;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Override
    public String getPassword() {
        return admin.getPassword();
    }

    @Override
    public String getUsername() {
        return admin.getEmail();
    }

    @Override
    public boolean isEnabled() {
        return admin.getStatus() == CommunityAdminStatus.ACTIVE;
    }

    @Override
    public boolean isAccountNonLocked() {
        return admin.getStatus() != CommunityAdminStatus.BLOCKED;
    }
}
