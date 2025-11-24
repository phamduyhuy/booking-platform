package com.pdh.ai.model.dto;

import java.util.Map;
import java.util.Collections;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuredResultItem {
    @JsonProperty(required = true, value = "type")
    @JsonPropertyDescription("Type of result: flight, hotel, or info")
    private String type;
    
    @JsonProperty(required = true, value = "title")
    @JsonPropertyDescription("Main title or heading for the result")
    private String title;
    
    @JsonProperty(required = true, value = "subtitle")
    @JsonPropertyDescription("Secondary text or brief description")
    private String subtitle;
    
    @JsonProperty(required = false, value = "description")
    @JsonPropertyDescription("Detailed description of the result")
    private String description;
    
    @JsonProperty(required = false, value = "imageUrl")
    @JsonPropertyDescription("Optional URL to an image representing this result, example: hotel photo, airline logo, destination image or result from mapbox static_map_image_tool")
    private String imageUrl;
    
    @JsonProperty(required = true, value = "ids")
    @JsonPropertyDescription("Map of identifier keys for this result. For flights: flightId, scheduleId, fareId. For hotels: hotelId, roomTypeId. Example: {\n  \"flightId\": \"5\",\n  \"fareId\": \"3ce03247-aa44-458b-811b-8ef4dda89dcc\",\n  \"scheduleId\": \"d35213bd-a3b9-4cf2-9730-2f9e6406100c\"\n}, for hotels: {\n  \"hotelId\": \"10\",\n  \"roomTypeId\": \"17\"\n}")
    @Builder.Default
    private Map<String, String> ids = Collections.emptyMap();
    
    @JsonProperty(required = false, value = "metadata")
    @JsonPropertyDescription("Additional key-value pairs with specific details like price, duration, airline, location, rating, amenities, etc. Example (flight): {\n  \"airline\": \"Vietnam Airlines\",\n  \"departure_time\": \"16:55\",\n  \"arrival_time\": \"18:30\",\n  \"duration\": \"1h 35m\",\n  \"price\": \"1350325\",\n  \"aircraft\": \"A380\"\n} Example (hotel): {\n  \"location\": \"Hạ Long, Vietnam\",\n  \"rating\": 5,\n  \"price\": \"1700000\",\n  \"amenities\": [\"Free WiFi\", \"Swimming Pool\"],\n  \"available_rooms\": [{\n    \"roomId\": \"17\",\n    \"roomType\": \"Balcony Room\",\n    \"capacity\": 2,\n    \"pricePerNight\": 1700000.0,\n    \"available\": false\n  }]\n}")
    @Builder.Default
    private Map<String, Object> metadata = Collections.emptyMap();
}
