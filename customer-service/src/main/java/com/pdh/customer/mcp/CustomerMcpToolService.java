package com.pdh.customer.mcp;

import com.pdh.customer.service.CustomerService;
import com.pdh.customer.viewmodel.CustomerVm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springaicommunity.mcp.annotation.McpMeta;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Customer MCP Tool Service
 * Exposes customer profile operations as AI-callable MCP tools
 * 
 * Tools provided:
 * - get_customer_profile: Fetch authenticated user's profile for auto-filling booking forms
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerMcpToolService {

    private final CustomerService customerService;

    /**
     * Get customer profile information for auto-filling booking forms
     * 
     * This tool retrieves customer profile data to help AI agents auto-populate
     * passenger/contact information during booking creation, reducing the need
     * to ask users for information that's already in their profile.
     */
    @McpTool(
        generateOutputSchema = true,
        name = "get_customer_profile",
        description = "Fetch customer profile information by userId. " +
            "Returns customer's name, email, phone number, and other profile details " +
            "that can be used to auto-fill booking forms. " +
            "Use this before asking user for contact information during booking."
    )
    public Map<String, Object> getCustomerProfile(
        @McpToolParam(
            description = "The unique identifier of the customer whose profile is to be fetched (userId) "
        )
     String userId) {

        try {
          
            log.info("MCP Tool: Fetching customer profile for userId: {}", userId);
            
            CustomerVm customer = customerService.getCustomerProfile(userId);
            
            if (customer == null) {
                log.warn("MCP Tool: No customer profile found for userId: {}", userId);
                return Map.of(
                    "success", false,
                    "error", "Customer profile not found",
                    "message", "No profile found for user ID: " + userId
                );
            }
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("userId", customer.id());
            result.put("username", customer.username());
            result.put("email", customer.email());
            result.put("firstName", customer.firstName() != null ? customer.firstName() : "");
            result.put("lastName", customer.lastName() != null ? customer.lastName() : "");
            result.put("phone", customer.phone() != null ? customer.phone() : "");
            
            // Construct full name for convenience
            String fullName = customer.fullName() != null ? customer.fullName() : "";
            result.put("fullName", fullName);
            
            // Include additional profile attributes if available
            if (customer.dateOfBirth() != null) {
                result.put("dateOfBirth", customer.dateOfBirth());
            }
            if (customer.address() != null) {
                result.put("address", Map.of(
                    "street", customer.address().street() != null ? customer.address().street() : "",
                    "city", customer.address().city() != null ? customer.address().city() : "",
                    "state", customer.address().state() != null ? customer.address().state() : "",
                    "country", customer.address().country() != null ? customer.address().country() : "",
                    "postalCode", customer.address().postalCode() != null ? customer.address().postalCode() : ""
                ));
            }
            
            result.put("message", "Customer profile retrieved successfully");
            
            return result;
            
        } catch (Exception e) {
            log.error("MCP Tool: Error fetching customer profile", e);
            return Map.of(
                "success", false,
                "error", e.getMessage(),
                "message", "Failed to retrieve customer profile: " + e.getMessage()
            );
        }
    }
}
