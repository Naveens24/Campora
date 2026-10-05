package in.ggv.campora.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import in.ggv.campora.identity.security.OnboardingInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final OnboardingInterceptor onboardingInterceptor;

    public WebConfig(OnboardingInterceptor onboardingInterceptor) {
        this.onboardingInterceptor = onboardingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(onboardingInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/", "/login", "/register", "/logout", "/verify", "/verify/**",
                        "/onboarding/**", "/error", "/error/**", "/css/**", "/js/**", "/img/**",
                        "/webjars/**", "/favicon.svg", "/actuator/**");
    }
}
