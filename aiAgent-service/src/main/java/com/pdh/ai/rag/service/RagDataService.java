// package com.pdh.ai.rag.service;

// import lombok.extern.slf4j.Slf4j;
// import org.springframework.ai.document.Document;
// import org.springframework.ai.transformer.splitter.TokenTextSplitter;
// import org.springframework.ai.vectorstore.VectorStore;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Service;

// import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;

// /**
//  * Service for processing flight and hotel details and updating RAG data
//  * Handles conversion of complete entity details to vector store documents
//  */
// @Slf4j
// @Service
// public class RagDataService {

//     @Autowired
//     private VectorStore vectorStore;
//     @Autowired
//     private TokenTextSplitter tokenTextSplitter;

//     /**
//      * Process complete flight details and update RAG data
//      *
//      * @param flightDetails The complete flight details from storefront endpoint
//      */
//     public void processFlightDetails(Map<String, Object> flightDetails) {
//         try {
//             log.debug("Processing flight details: {}", flightDetails);

//             // Convert the flight details to documents for the vector store
//             List<Document> documents = convertFlightDetailsToDocuments(flightDetails);
//             documents = tokenTextSplitter.apply(documents);
//             // Add documents to vector store
//             if (!documents.isEmpty()) {

//                 vectorStore.accept(documents);
//                 log.info("Added {} flight documents to vector store", documents.size());
//             }
//         } catch (Exception e) {
//             log.error("Error processing flight details: {}", e.getMessage(), e);
//             throw new RuntimeException("Failed to process flight details", e);
//         }
//     }

//     /**
//      * Process flight fare change and update RAG data
//      *
//      * @param flightDetails The complete flight details from storefront endpoint
//      * @param scheduleId    The schedule ID that had fare changes
//      */
//     public void processFlightFareChange(Map<String, Object> flightDetails, String scheduleId) {
//         try {
//             log.debug("Processing flight fare change for schedule ID: {}", scheduleId);

//             // For now, we'll just reprocess the entire flight details
//             // In a more sophisticated implementation, we might want to update only
//             // fare-specific information
//             processFlightDetails(flightDetails);

//             log.info("Processed flight fare change for schedule ID: {}", scheduleId);
//         } catch (Exception e) {
//             log.error("Error processing flight fare change for schedule ID {}: {}", scheduleId, e.getMessage(), e);
//             throw new RuntimeException("Failed to process flight fare change", e);
//         }
//     }

//     /**
//      * Process complete hotel details and update RAG data
//      *
//      * @param hotelDetails The complete hotel details from storefront endpoint
//      */
//     public void processHotelDetails(Map<String, Object> hotelDetails) {
//         try {
//             log.debug("Processing hotel details: {}", hotelDetails);

//             // Convert the hotel details to documents for the vector store
//             List<Document> documents = convertHotelDetailsToDocuments(hotelDetails);
//             documents = tokenTextSplitter.apply(documents);

//             // Add documents to vector store
//             if (!documents.isEmpty()) {
//                 vectorStore.accept(documents);
//                 log.info("Added {} hotel documents to vector store", documents.size());
//             }
//         } catch (Exception e) {
//             log.error("Error processing hotel details: {}", e.getMessage(), e);
//             throw new RuntimeException("Failed to process hotel details", e);
//         }
//     }

//     /**
//      * Convert flight details to documents
//      *
//      * @param flightDetails The complete flight details
//      * @return List of documents
//      */
//     private List<Document> convertFlightDetailsToDocuments(Map<String, Object> flightDetails) {
//         List<Document> documents = new ArrayList<>();
//         System.out.println("Flight Details: " + flightDetails);
//         try {
//             if (flightDetails == null || flightDetails.isEmpty()) {
//                 return documents;
//             }

//             // Create content for the document
//             StringBuilder content = new StringBuilder();
//             content.append("Flight Information: ");

//             // Extract flight information from FlightDto structure
//             Object flightIdObj = flightDetails.get("flightId");
//             String flightId = flightIdObj != null ? flightIdObj.toString() : null;
//             String flightNumber = getStringValue(flightDetails, "flightNumber");
//             String airlineName = getStringValue(flightDetails, "airlineName");
//             String departureAirportIataCode = getStringValue(flightDetails, "departureAirportIataCode");
//             String arrivalAirportIataCode = getStringValue(flightDetails, "arrivalAirportIataCode");
//             String departureAirportName = getStringValue(flightDetails, "departureAirportName");
//             String arrivalAirportName = getStringValue(flightDetails, "arrivalAirportName");
//             String departureAirportCity = getStringValue(flightDetails, "departureAirportCity");
//             String arrivalAirportCity = getStringValue(flightDetails, "arrivalAirportCity");
//             String aircraftType = getStringValue(flightDetails, "aircraftType");
//             String status = getStringValue(flightDetails, "status");
//             String basePrice = getStringValue(flightDetails, "basePrice");

//             // Add flight details to content
//             appendIfNotNull(content, "Flight Number", flightNumber);
//             appendIfNotNull(content, "Airline", airlineName);
//             appendIfNotNull(content, "Departure Airport", departureAirportName);
//             appendIfNotNull(content, "Arrival Airport", arrivalAirportName);
//             appendIfNotNull(content, "Departure Airport Code", departureAirportIataCode);
//             appendIfNotNull(content, "Arrival Airport Code", arrivalAirportIataCode);
//             appendIfNotNull(content, "Departure City", departureAirportCity);
//             appendIfNotNull(content, "Arrival City", arrivalAirportCity);
//             appendIfNotNull(content, "Aircraft Type", aircraftType);
//             appendIfNotNull(content, "Status", status);

//             // Add schedule information if available
//             Object schedulesObj = flightDetails.get("schedules");
//             if (schedulesObj != null) {
//                 content.append("Schedules: ").append(schedulesObj.toString()).append(". ");
//             }

//             // Add fare information if available
//             Object faresObj = flightDetails.get("fares");
//             if (faresObj != null) {
//                 content.append("Fares: ").append(faresObj.toString()).append(". ");
//             }

//             // Create metadata
//             Map<String, Object> metadata = new HashMap<>();
//             metadata.put("flight_id", flightId);
//             metadata.put("flight_number", flightNumber);
//             metadata.put("airline_name", airlineName);
//             metadata.put("departure_airport", departureAirportName);
//             metadata.put("arrival_airport", arrivalAirportName);
//             metadata.put("departure_airport_code", departureAirportIataCode);
//             metadata.put("arrival_airport_code", arrivalAirportIataCode);
//             metadata.put("departure_city", departureAirportCity);
//             metadata.put("arrival_city", arrivalAirportCity);
//             metadata.put("aircraft_type", aircraftType);
//             metadata.put("status", status);

//             if (basePrice != null) {
//                 metadata.put("base_price", basePrice);
//             }

//             // Add schedule and fare counts
//             Object schedules = flightDetails.get("schedules");
//             if (schedules instanceof List) {
//                 metadata.put("schedule_count", ((List<?>) schedules).size());
//             }

//             Object fares = flightDetails.get("fares");
//             if (fares instanceof List) {
//                 metadata.put("fare_count", ((List<?>) fares).size());
//             }

//             // Create document
//             Document document = new Document(content.toString(), metadata);
//             documents.add(document);
//         } catch (Exception e) {
//             log.error("Error converting flight details to documents: {}", e.getMessage(), e);
//         }

//         return documents;
//     }

//     /**
//      * Convert hotel details to documents
//      *
//      * @param hotelDetails The complete hotel details
//      * @return List of documents
//      */
//     private List<Document> convertHotelDetailsToDocuments(Map<String, Object> hotelDetails) {
//         List<Document> documents = new ArrayList<>();
//         System.out.println("Hotel Details: " + hotelDetails);
//         try {
//             if (hotelDetails == null || hotelDetails.isEmpty()) {
//                 return documents;
//             }

//             // Create content for the document
//             StringBuilder content = new StringBuilder();
//             content.append("Hotel Information: ");

//             // Extract hotel information
//             String hotelId = getStringValue(hotelDetails, "hotelId");
//             String name = getStringValue(hotelDetails, "name");
//             String address = getStringValue(hotelDetails, "address");
//             String city = getStringValue(hotelDetails, "city");
//             String country = getStringValue(hotelDetails, "country");
//             String description = getStringValue(hotelDetails, "description");
//             String latitude = getStringValue(hotelDetails, "latitude");
//             String longitude = getStringValue(hotelDetails, "longitude");

//             // Add hotel details to content
//             appendIfNotNull(content, "Name", name);
//             appendIfNotNull(content, "Address", address);
//             appendIfNotNull(content, "Location", city + ", " + country);
//             appendIfNotNull(content, "Description", description);

//             // Add geographic information
//             if (latitude != null && longitude != null) {
//                 content.append("Coordinates: (").append(latitude).append(", ").append(longitude).append("). ");
//             }

//             // Add rating information if available
//             Object ratingObj = hotelDetails.get("rating");
//             if (ratingObj != null) {
//                 content.append("Rating: ").append(ratingObj).append(" stars. ");
//             }

//             // Add price information if available
//             Object pricePerNightObj = hotelDetails.get("pricePerNight");
//             if (pricePerNightObj != null) {
//                 content.append("Price: ").append(pricePerNightObj);
//                 Object currencyObj = hotelDetails.get("currency");
//                 if (currencyObj != null) {
//                     content.append(" ").append(currencyObj);
//                 }
//                 content.append(" per night. ");
//             }

//             // Add room types information if available
//             Object roomTypesObj = hotelDetails.get("roomTypes");
//             if (roomTypesObj != null) {
//                 content.append("Room Types: ").append(roomTypesObj.toString()).append(". ");
//             }

//             // Add amenities information if available
//             Object amenitiesObj = hotelDetails.get("amenities");
//             if (amenitiesObj != null) {
//                 content.append("Amenities: ").append(amenitiesObj.toString()).append(". ");
//             }

//             // Add policies information if available
//             Object policiesObj = hotelDetails.get("policies");
//             if (policiesObj != null) {
//                 content.append("Policies: ").append(policiesObj.toString()).append(". ");
//             }

//             // Create metadata
//             Map<String, Object> metadata = new HashMap<>();
//             metadata.put("source_type", "hotel");
//             metadata.put("hotel_id", hotelId);
//             metadata.put("name", name);
//             metadata.put("address", address);
//             metadata.put("city", city);
//             metadata.put("country", country);

//             if (ratingObj != null) {
//                 metadata.put("rating", ratingObj);
//             }

//             if (pricePerNightObj != null) {
//                 metadata.put("price_per_night", pricePerNightObj);
//             }

//             if (latitude != null) {
//                 metadata.put("latitude", latitude);
//             }

//             if (longitude != null) {
//                 metadata.put("longitude", longitude);
//             }

//             // Create document
//             Document document = new Document(content.toString(), metadata);
//             documents.add(document);
//         } catch (Exception e) {
//             log.error("Error converting hotel details to documents: {}", e.getMessage(), e);
//         }

//         return documents;
//     }

//     /**
//      * Helper method to get string value from map
//      *
//      * @param map The map
//      * @param key The key
//      * @return The string value or null
//      */
//     private String getStringValue(Map<String, Object> map, String key) {
//         Object value = map.get(key);
//         return value != null ? value.toString() : null;
//     }

//     /**
//      * Append a field to the content if it's not null
//      *
//      * @param content    The content builder
//      * @param fieldName  The field name
//      * @param fieldValue The field value
//      */
//     private void appendIfNotNull(StringBuilder content, String fieldName, String fieldValue) {
//         if (fieldValue != null && !fieldValue.trim().isEmpty()) {
//             content.append(fieldName).append(": ").append(fieldValue).append(". ");
//         }
//     }
// }