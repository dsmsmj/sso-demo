package com.example.iam.sso;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@ConditionalOnBean(ClientRegistrationRepository.class)
@RequiredArgsConstructor
public class TokenRefreshService {

    private final ClientRegistrationRepository clientRegistrationRepository;

    /**
     * 用 refresh_token 换新 access_token（无感续期）。
     * 对应流程图：token 过期 → POST refresh_token → /token → 返回新 access_token。
     */
    public String refresh(OAuth2AuthorizedClient authorizedClient) {
        if (authorizedClient.getRefreshToken() == null) {
            log.warn("No refresh_token for {}, cannot refresh", authorizedClient.getPrincipalName());
            return null;
        }

        ClientRegistration reg = clientRegistrationRepository.findByRegistrationId("keycloak");
        if (reg == null) {
            return null;
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("refresh_token", authorizedClient.getRefreshToken().getTokenValue());
        params.add("client_id", reg.getClientId());
        params.add("client_secret", reg.getClientSecret());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<Map<String, Object>> resp = new RestTemplate().exchange(
                    reg.getProviderDetails().getTokenUri(),
                    HttpMethod.POST,
                    new HttpEntity<>(params, headers),
                    new ParameterizedTypeReference<>() {}
            );
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                log.debug("Token refreshed for {}", authorizedClient.getPrincipalName());
                return (String) resp.getBody().get("access_token");
            }
        } catch (Exception e) {
            log.warn("Token refresh failed: {}", e.getMessage());
        }
        return null;
    }
}
