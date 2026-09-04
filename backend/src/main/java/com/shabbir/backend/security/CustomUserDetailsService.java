package com.shabbir.backend.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.shabbir.backend.repository.CommunityAdminRepository;
import com.shabbir.backend.repository.ResidentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final CommunityAdminRepository communityAdminRepository;
    private final ResidentRepository residentRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return communityAdminRepository.findByEmail(email)
                .<UserDetails>map(UserPrincipal::new)
                .or(() -> residentRepository.findByEmailAndPasswordSetTrue(email).map(ResidentPrincipal::new))
                .orElseThrow(() -> new UsernameNotFoundException("No user found with email " + email));
    }
}
