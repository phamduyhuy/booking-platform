package com.pdh.ai.agent.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Flux;

import com.pdh.ai.agent.guard.InputValidationGuard;
import com.pdh.ai.agent.guard.ScopeGuard;

/**
 * Security Guard Advisor that validates and enforces security policies.
 * 
 * <p>This advisor provides comprehensive security checks:</p>
 * <ul>
 * <li>Input validation and sanitization</li>
 * <li>Scope enforcement (travel booking domain only)</li>
 * <li>Injection attack prevention</li>
 * <li>System prompt protection</li>
 * </ul>
 * 
 * <p>Execution order: HIGHEST_PRECEDENCE to run first in advisor chain.</p>
 * 
 * @author BookingSmart AI Team
 */
public class SecurityGuardAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(SecurityGuardAdvisor.class);
    
    private final InputValidationGuard inputValidationGuard;
    private final ScopeGuard scopeGuard;
    private final boolean enforceScope;
    private final String workflowType;
    
    /**
     * Creates a new Security Guard Advisor.
     * 
     * @param inputValidationGuard Input validation guard
     * @param scopeGuard Scope enforcement guard
     * @param enforceScope Whether to enforce scope restrictions
     * @param workflowType Workflow type for logging
     */
    public SecurityGuardAdvisor(InputValidationGuard inputValidationGuard,
                               ScopeGuard scopeGuard,
                               boolean enforceScope,
                               String workflowType) {
        this.inputValidationGuard = inputValidationGuard;
        this.scopeGuard = scopeGuard;
        this.enforceScope = enforceScope;
        this.workflowType = workflowType;
    }
    
    /**
     * Creates a Security Guard Advisor for main chat with full enforcement.
     */
    public static SecurityGuardAdvisor forChat(InputValidationGuard inputValidation, ScopeGuard scope) {
        return new SecurityGuardAdvisor(inputValidation, scope, true, "chat");
    }
    
    /**
     * Creates a Security Guard Advisor for routing with validation only.
     */
    public static SecurityGuardAdvisor forRouting(InputValidationGuard inputValidation, ScopeGuard scope) {
        return new SecurityGuardAdvisor(inputValidation, scope, false, "routing");
    }
    
    /**
     * Creates a Security Guard Advisor for parallel workflows with full enforcement.
     */
    public static SecurityGuardAdvisor forParallel(InputValidationGuard inputValidation, ScopeGuard scope) {
        return new SecurityGuardAdvisor(inputValidation, scope, true, "parallel");
    }

    @Override
    @NonNull
    public String getName() {
        return "SecurityGuardAdvisor-" + workflowType;
    }

    @Override
    public int getOrder() {
        // Run FIRST to validate input before any other processing
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    @NonNull
    public ChatClientResponse adviseCall(@NonNull ChatClientRequest request, @NonNull CallAdvisorChain chain) {
        logger.debug("🛡️ [{}] Security guard checking request", workflowType);
        
        // Extract user message from request
        String userInput = extractUserInput(request);
        
        if (userInput == null || userInput.isEmpty()) {
            logger.debug("⚠️ [{}] Empty request, skipping security check", workflowType);
            return chain.nextCall(request);
        }
        
        // 1. Input Validation
        InputValidationGuard.ValidationResult validationResult = 
            inputValidationGuard.validate(userInput);
        
        if (!validationResult.isValid()) {
            logger.error("🚫 [{}] Input validation FAILED: {}", 
                workflowType, validationResult.violations());
            
            // Throw exception to prevent processing
            throw new SecurityException(
                "Input validation failed: " + String.join(", ", validationResult.violations())
            );
        }
        
        // Log if suspicious
        if (validationResult.securityLevel() == InputValidationGuard.SecurityLevel.SUSPICIOUS) {
            logger.warn("⚠️ [{}] Suspicious input detected but allowed: {}", 
                workflowType, validationResult.violations());
        }
        
        // 2. Scope Check (if enabled)
        if (enforceScope) {
            ScopeGuard.ScopeCheckResult scopeResult = 
                scopeGuard.checkScope(validationResult.sanitizedInput());
            
            if (!scopeResult.isInScope()) {
                logger.warn("🚫 [{}] Request OUT OF SCOPE: {}", 
                    workflowType, scopeResult.reason());
                
                // Throw exception to prevent processing
                throw new SecurityException("Request out of scope: " + scopeResult.message());
            }
            
            logger.debug("✅ [{}] Scope check passed: {}", workflowType, scopeResult.reason());
        }
        
        logger.debug("✅ [{}] Security checks passed, proceeding with request", workflowType);
        
        // Continue with next advisor in chain
        return chain.nextCall(request);
    }

    @Override
    @NonNull
    public Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest request, 
                                                 @NonNull StreamAdvisorChain chain) {
        logger.debug("🛡️ [{}] Security guard checking stream request", workflowType);
        
        // Extract user message
        String userInput = extractUserInput(request);
        
        if (userInput == null || userInput.isEmpty()) {
            logger.debug("⚠️ [{}] Empty stream request, skipping security check", workflowType);
            return chain.nextStream(request);
        }
        
        // 1. Input Validation
        InputValidationGuard.ValidationResult validationResult = 
            inputValidationGuard.validate(userInput);
        
        if (!validationResult.isValid()) {
            logger.error("🚫 [{}] Stream input validation FAILED: {}", 
                workflowType, validationResult.violations());
            
            // Throw exception to prevent processing
            return Flux.error(new SecurityException(
                "Input validation failed: " + String.join(", ", validationResult.violations())
            ));
        }
        
        // Log if suspicious
        if (validationResult.securityLevel() == InputValidationGuard.SecurityLevel.SUSPICIOUS) {
            logger.warn("⚠️ [{}] Suspicious stream input detected but allowed: {}", 
                workflowType, validationResult.violations());
        }
        
        // 2. Scope Check (if enabled)
        if (enforceScope) {
            ScopeGuard.ScopeCheckResult scopeResult = 
                scopeGuard.checkScope(validationResult.sanitizedInput());
            
            if (!scopeResult.isInScope()) {
                logger.warn("🚫 [{}] Stream request OUT OF SCOPE: {}", 
                    workflowType, scopeResult.reason());
                
                // Throw exception to prevent processing
                return Flux.error(new SecurityException("Request out of scope: " + scopeResult.message()));
            }
            
            logger.debug("✅ [{}] Stream scope check passed: {}", workflowType, scopeResult.reason());
        }
        
        logger.debug("✅ [{}] Stream security checks passed", workflowType);
        
        // Continue with next advisor in chain
        return chain.nextStream(request);
    }
    
    /**
     * Extracts user input from request.
     */
    private String extractUserInput(ChatClientRequest request) {
        try {
            if (request.prompt() != null && request.prompt().getUserMessage() != null) {
                return request.prompt().getUserMessage().getText();
            }
        } catch (Exception e) {
            logger.debug("Could not extract user message: {}", e.getMessage());
        }
        return "";
    }
}
