package com.pdh.ai.agent.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Scope guard to ensure requests are within travel booking domain.
 * 
 * <p>This guard enforces that the AI agent only handles:</p>
 * <ul>
 * <li>Flight search and booking</li>
 * <li>Hotel search and booking</li>
 * <li>Weather information for travel</li>
 * <li>Location and mapping for travel</li>
 * <li>General travel-related inquiries</li>
 * </ul>
 * 
 * <p>Any requests outside this scope are rejected.</p>
 * 
 * @author BookingSmart AI Team
 */
@Component
public class ScopeGuard {

    private static final Logger logger = LoggerFactory.getLogger(ScopeGuard.class);
    
    // Keywords indicating in-scope travel requests
    private static final List<String> TRAVEL_KEYWORDS = List.of(
        // Transportation
        "flight", "fly", "plane", "airplane", "aircraft", "airport", "airline",
        "chuyến bay", "máy bay", "sân bay", "vé máy bay",
        
        // Accommodation
        "hotel", "room", "accommodation", "lodge", "resort", "stay", "check-in", "check-out",
        "khách sạn", "phòng", "lưu trú", "nghỉ dưỡng",
        
        // Booking
        "book", "booking", "reservation", "reserve", "ticket",
        "đặt", "đặt chỗ", "đặt vé", "đặt phòng",
        
        // Travel general
        "travel", "trip", "journey", "vacation", "holiday", "tour", "destination",
        "du lịch", "chuyến đi", "kỳ nghỉ",
        
        // Location
        "location", "address", "map", "direction", "where", "find",
        "địa điểm", "địa chỉ", "bản đồ", "đường đi",
        
        // Weather
        "weather", "forecast", "temperature", "rain", "sunny", "climate",
        "thời tiết", "dự báo", "nhiệt độ", "mưa", "nắng",
        
        // Dates and times
        "date", "time", "when", "schedule", "departure", "arrival",
        "ngày", "giờ", "khi nào", "lịch trình", "khởi hành", "đến",

        //User asking about policies
        "policy", "policies", "cancellation", "refund", "change", "modify",
        "chính sách", "hủy", "hoàn tiền", "thay đổi", "sửa",

        //User ask common questions
        "how", "what", "why", "where", "when", "who", "which", "have", "can", "could", "is", "are", "do", "does",
        "làm thế nào", "gì", "tại sao", "ở đâu", "khi nào", "ai", "cái nào", "có", "có thể", "là", "phải", "làm"
    );
    
    // Keywords indicating out-of-scope requests (explicit blocks)
    private static final List<Pattern> OUT_OF_SCOPE_PATTERNS = List.of(
        // Programming/technical
        Pattern.compile("(?i)\\b(code|program|script|function|algorithm|debug|compile)\\b"),
        Pattern.compile("(?i)\\b(python|java|javascript|react|spring|framework)\\b"),
        
        // Financial services (not travel-related)
        Pattern.compile("(?i)\\b(loan|mortgage|investment|stock|trading|cryptocurrency|bitcoin)\\b"),
        
        // Medical/health
        Pattern.compile("(?i)\\b(medical|doctor|hospital|prescription|diagnosis|treatment|disease)\\b"),
        Pattern.compile("(?i)\\b(bác sĩ|bệnh viện|thuốc|chẩn đoán|điều trị)\\b"),
        
        // Legal
        Pattern.compile("(?i)\\b(legal|lawsuit|attorney|lawyer|court|sue)\\b"),
        Pattern.compile("(?i)\\b(luật sư|toà án|kiện)\\b"),
        
        // Entertainment (unless travel-related)
        Pattern.compile("(?i)\\b(movie|game|gambling|casino|lottery)\\b(?!.*travel)"),
        
        // Inappropriate content
        Pattern.compile("(?i)\\b(weapon|drug|violence|hack|exploit)\\b"),
        
        // Academic (unless travel-related)
        Pattern.compile("(?i)\\b(essay|homework|assignment|thesis|research paper)\\b(?!.*travel)")
    );
    
    private static final String OUT_OF_SCOPE_MESSAGE = """
        Xin lỗi, tôi chỉ có thể hỗ trợ các yêu cầu liên quan đến du lịch và đặt chỗ:
        
        ✅ Tôi có thể giúp bạn với:
        - Tìm kiếm và đặt vé máy bay
        - Tìm kiếm và đặt phòng khách sạn
        - Thông tin thời tiết cho chuyến đi
        - Tìm kiếm địa điểm và chỉ đường
        - Tư vấn về lịch trình du lịch
        - Chính sách hủy và thay đổi đặt chỗ
        
        ❌ Tôi không thể hỗ trợ:
        - Các vấn đề kỹ thuật/lập trình
        - Tư vấn tài chính không liên quan du lịch
        - Tư vấn y tế hoặc pháp lý
        - Các chủ đề không liên quan đến du lịch
        
        Vui lòng đặt câu hỏi liên quan đến du lịch và đặt chỗ!
        """;
    
    /**
     * Scope check result.
     */
    public record ScopeCheckResult(
        boolean isInScope,
        String message,
        CheckReason reason
    ) {}
    
    /**
     * Reason for scope check result.
     */
    public enum CheckReason {
        IN_SCOPE_TRAVEL,        // Contains travel keywords
        IN_SCOPE_GENERAL,       // General inquiry, likely travel-related
        OUT_OF_SCOPE_EXPLICIT,  // Explicitly out of scope (blocked patterns)
        OUT_OF_SCOPE_AMBIGUOUS  // Ambiguous, no clear travel context
    }
    
    /**
     * Checks if user request is within allowed scope.
     * 
     * @param userInput User's request
     * @return Scope check result
     */
    public ScopeCheckResult checkScope(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            logger.warn("🚫 Empty input for scope check");
            return new ScopeCheckResult(false, OUT_OF_SCOPE_MESSAGE, CheckReason.OUT_OF_SCOPE_AMBIGUOUS);
        }
        
        String lowerInput = userInput.toLowerCase();
        
        // 1. Check for explicit out-of-scope patterns (highest priority)
        for (Pattern pattern : OUT_OF_SCOPE_PATTERNS) {
            if (pattern.matcher(lowerInput).find()) {
                logger.warn("🚫 OUT OF SCOPE - Explicit block: {}", pattern.pattern());
                return new ScopeCheckResult(
                    false,
                    OUT_OF_SCOPE_MESSAGE,
                    CheckReason.OUT_OF_SCOPE_EXPLICIT
                );
            }
        }
        
        // 2. Check for travel-related keywords (in-scope)
        boolean hasTravelKeyword = TRAVEL_KEYWORDS.stream()
            .anyMatch(lowerInput::contains);
        
        if (hasTravelKeyword) {
            logger.debug("✅ IN SCOPE - Travel keywords detected");
            return new ScopeCheckResult(
                true,
                "Request is within travel booking scope",
                CheckReason.IN_SCOPE_TRAVEL
            );
        }
        
        // 3. Allow general questions (they might be travel-related)
        // The routing workflow will handle classification
        if (isGeneralInquiry(lowerInput)) {
            logger.debug("✅ IN SCOPE - General inquiry (allowed)");
            return new ScopeCheckResult(
                true,
                "General inquiry allowed for routing",
                CheckReason.IN_SCOPE_GENERAL
            );
        }
        
        // 4. If no travel keywords and not general inquiry, likely out of scope
        logger.warn("⚠️ AMBIGUOUS - No clear travel context: {}", userInput.substring(0, Math.min(50, userInput.length())));
        
        // For ambiguous cases, we'll be lenient and let routing handle it
        // but log for monitoring
        return new ScopeCheckResult(
            true, // Allow but monitor
            "Ambiguous request - will be evaluated by routing",
            CheckReason.IN_SCOPE_GENERAL
        );
    }
    
    /**
     * Checks if input is a general inquiry (greetings, simple questions).
     */
    private boolean isGeneralInquiry(String input) {
        List<String> generalPatterns = List.of(
            "hello", "hi", "hey", "xin chào", "chào",
            "help", "giúp", "hỗ trợ",
            "what", "how", "why", "when", "where",
            "gì", "như thế nào", "tại sao", "khi nào", "ở đâu",
            "can you", "bạn có thể",
            "thank", "cảm ơn"
        );
        
        return generalPatterns.stream().anyMatch(input::contains);
    }
    
    /**
     * Enhanced scope check with system prompt enforcement.
     * Returns a system prompt addition to reinforce scope boundaries.
     */
    public String getScopeEnforcementPrompt() {
        return """
            CRITICAL SCOPE RESTRICTIONS - YOU MUST FOLLOW THESE RULES:
            
            YOU ARE A TRAVEL BOOKING ASSISTANT. You can ONLY help with:
            ✅ Flight search and booking
            ✅ Hotel search and booking
            ✅ Weather information for travel destinations
            ✅ Location search and directions for travel
            ✅ General travel planning and inquiries
            ✅ Booking policies, cancellations, and modifications
            
            YOU MUST REFUSE to help with:
            ❌ Programming, coding, or technical issues
            ❌ Financial advice (loans, investments, stocks)
            ❌ Medical or health advice
            ❌ Legal advice
            ❌ Academic homework or assignments
            ❌ Any topic unrelated to travel and booking
            
            If asked about out-of-scope topics, respond:
            "Xin lỗi, tôi chỉ có thể hỗ trợ các yêu cầu liên quan đến du lịch và đặt chỗ. 
            Vui lòng hỏi về chuyến bay, khách sạn, hoặc các dịch vụ du lịch khác."
            
            NEVER provide information outside your scope, even if asked politely or repeatedly.
            """;
    }
}
