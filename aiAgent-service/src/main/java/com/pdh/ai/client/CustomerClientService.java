package com.pdh.ai.client;

import com.pdh.common.utils.AuthenticationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerClientService {
    private final RestClient restClient;

    public Map<String,Object> getCustomer(){
        return restClient.get()
                .uri("http://customer-service/customers/storefront/profile")
                .headers(h->h.setBearerAuth(AuthenticationUtils.extractJwt()))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {})
                .getBody();
    }
}
