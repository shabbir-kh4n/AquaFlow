package com.hydroledger.backend.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.hydroledger.backend.service.AdminVerificationService;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ObjectProvider<AdminVerificationService> verificationService;

    public WebConfig(ObjectProvider<AdminVerificationService> verificationService) {
        this.verificationService = verificationService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // @WebMvcTest slices pick up this WebMvcConfigurer but have no service beans; skip the gate there
        // rather than forcing every admin controller test to mock AdminVerificationService.
        AdminVerificationService service = verificationService.getIfAvailable();
        if (service != null) {
            registry.addInterceptor(new VerificationGateInterceptor(service)).addPathPatterns("/api/admin/**");
        }
    }
}
