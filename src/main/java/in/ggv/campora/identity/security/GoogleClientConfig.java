package in.ggv.campora.identity.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import in.ggv.campora.common.config.CamporaProperties;

/** Google sign-in is available only when both credentials are configured. */
@Configuration
@ConditionalOnExpression("!'${campora.google.client-id:}'.isBlank() and !'${campora.google.client-secret:}'.isBlank()")
class GoogleClientConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(CamporaProperties properties) {
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(properties.google().clientId())
                .clientSecret(properties.google().clientSecret())
                .build());
    }
}
