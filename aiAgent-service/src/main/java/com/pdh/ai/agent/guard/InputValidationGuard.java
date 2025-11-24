package com.pdh.ai.agent.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Input validation guard to sanitize and validate user requests.
 * 
 * <p>This guard provides protection against:</p>
 * <ul>
 * <li>Prompt injection attacks</li>
 * <li>System prompt manipulation attempts</li>
 * <li>Malicious input patterns</li>
 * <li>Excessive input length</li>
 * </ul>
 * 
 * @author BookingSmart AI Team
 */
@Component
public class InputValidationGuard {

    private static final Logger logger = LoggerFactory.getLogger(InputValidationGuard.class);
    
    // Security patterns to detect and remove
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        Pattern.compile("(?i)ignore\\s+(previous|all)\\s+(instructions?|prompts?)", Pattern.MULTILINE),
        Pattern.compile("(?i)system\\s+prompt", Pattern.MULTILINE),
        Pattern.compile("(?i)you\\s+are\\s+now", Pattern.MULTILINE),
        Pattern.compile("(?i)forget\\s+(everything|all|previous)", Pattern.MULTILINE),
        Pattern.compile("(?i)disregard\\s+(previous|all)", Pattern.MULTILINE),
        Pattern.compile("(?i)new\\s+instructions?", Pattern.MULTILINE),
        Pattern.compile("(?i)\\[\\s*system\\s*\\]", Pattern.MULTILINE),
        Pattern.compile("(?i)\\[\\s*assistant\\s*\\]", Pattern.MULTILINE),
        Pattern.compile("(?i)act\\s+as\\s+(if|a)", Pattern.MULTILINE),
        Pattern.compile("(?i)pretend\\s+(you|to|that)", Pattern.MULTILINE),
        Pattern.compile("(?i)roleplay\\s+as", Pattern.MULTILINE),
        Pattern.compile("(?i)simulate\\s+being", Pattern.MULTILINE)
    );
    
    // Suspicious patterns that should be monitored (but not necessarily blocked)
    private static final List<Pattern> SUSPICIOUS_PATTERNS = List.of(
        Pattern.compile("(?i)<script[^>]*>.*?</script>", Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("(?i)javascript:", Pattern.MULTILINE),
        Pattern.compile("(?i)on(click|load|error)\\s*=", Pattern.MULTILINE),
        Pattern.compile("\\$\\{.*?\\}", Pattern.MULTILINE) // Template injection
    );
    
    private static final int MAX_INPUT_LENGTH = 2000;
    private static final int MIN_INPUT_LENGTH = 1;
    
    /**
     * Validation result containing sanitized input and security metadata.
     */
    public record ValidationResult(
        boolean isValid,
        String sanitizedInput,
        List<String> violations,
        SecurityLevel securityLevel
    ) {}
    
    /**
     * Security level of the validated input.
     */
    public enum SecurityLevel {
        SAFE,           // No issues detected
        SUSPICIOUS,     // Contains suspicious patterns but allowed
        DANGEROUS,      // Contains injection attempts - blocked
        INVALID         // Invalid format or length
    }
    
    /**
     * Validates and sanitizes user input.
     * 
     * @param userInput Raw user input
     * @return Validation result with sanitized input
     */
    public ValidationResult validate(String userInput) {
        List<String> violations = new ArrayList<>();
        
        // Check for null or empty
        if (userInput == null || userInput.trim().isEmpty()) {
            logger.warn("🚫 Empty or null input detected");
            return new ValidationResult(
                false,
                "",
                List.of("Input cannot be empty"),
                SecurityLevel.INVALID
            );
        }
        
        // Check length constraints
        if (userInput.length() > MAX_INPUT_LENGTH) {
            logger.warn("🚫 Input exceeds maximum length: {} > {}", userInput.length(), MAX_INPUT_LENGTH);
            return new ValidationResult(
                false,
                userInput.substring(0, MAX_INPUT_LENGTH),
                List.of("Input exceeds maximum length of " + MAX_INPUT_LENGTH + " characters"),
                SecurityLevel.INVALID
            );
        }
        
        if (userInput.length() < MIN_INPUT_LENGTH) {
            logger.warn("🚫 Input below minimum length: {} < {}", userInput.length(), MIN_INPUT_LENGTH);
            return new ValidationResult(
                false,
                userInput,
                List.of("Input must be at least " + MIN_INPUT_LENGTH + " character"),
                SecurityLevel.INVALID
            );
        }
        
        // Check for injection attempts
        String sanitizedInput = userInput;
        SecurityLevel securityLevel = SecurityLevel.SAFE;
        
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(sanitizedInput).find()) {
                String violation = "Potential prompt injection detected: " + pattern.pattern();
                violations.add(violation);
                logger.warn("🚨 SECURITY: {}", violation);
                securityLevel = SecurityLevel.DANGEROUS;
                
                // Remove the malicious pattern
                sanitizedInput = pattern.matcher(sanitizedInput).replaceAll("[REMOVED]");
            }
        }
        
        // Check for suspicious patterns (monitor but don't block)
        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(sanitizedInput).find()) {
                String warning = "Suspicious pattern detected: " + pattern.pattern();
                violations.add(warning);
                logger.info("⚠️ MONITOR: {}", warning);
                
                if (securityLevel == SecurityLevel.SAFE) {
                    securityLevel = SecurityLevel.SUSPICIOUS;
                }
            }
        }
        
        // Trim and normalize whitespace
        sanitizedInput = sanitizedInput.trim().replaceAll("\\s+", " ");
        
        // If dangerous content found, mark as invalid
        boolean isValid = securityLevel != SecurityLevel.DANGEROUS;
        
        if (!isValid) {
            logger.error("🛑 Input validation FAILED - Security level: {}, Violations: {}", 
                securityLevel, violations);
        } else if (securityLevel == SecurityLevel.SUSPICIOUS) {
            logger.warn("⚠️ Input validation PASSED with WARNINGS - Violations: {}", violations);
        } else {
            logger.debug("✅ Input validation PASSED - Security level: SAFE");
        }
        
        return new ValidationResult(
            isValid,
            sanitizedInput,
            violations,
            securityLevel
        );
    }
    
    /**
     * Quick sanitization without full validation (for less critical paths).
     * 
     * @param userInput Raw user input
     * @return Sanitized input
     */
    public String sanitize(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return "";
        }
        
        String sanitized = userInput;
        
        // Remove known injection patterns
        for (Pattern pattern : INJECTION_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("");
        }
        
        // Trim and normalize
        return sanitized.trim().replaceAll("\\s+", " ");
    }
}
