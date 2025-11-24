package com.pdh.customer.viewmodel;

import org.keycloak.representations.idm.UserRepresentation;
import java.util.Map;

public record CustomerVm(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String picture,
        String phone,
        String dateOfBirth,
        String gender,
        String language,
        String currency,
        String theme,
        String density,
        String notifications,
        AddressInfo address) {
    public static CustomerVm fromUserRepresentation(UserRepresentation userRepresentation) {
        String fullName = (userRepresentation.getFirstName() != null ? userRepresentation.getFirstName() : "") +
                " " + (userRepresentation.getLastName() != null ? userRepresentation.getLastName() : "");

        // Get attributes from user attributes
        Map<String, java.util.List<String>> attributes = userRepresentation.getAttributes();

        String picture = getAttributeValue(attributes, "picture");
        String phone = getAttributeValue(attributes, "phone");
        String dateOfBirth = getAttributeValue(attributes, "dateOfBirth");
        String gender = getAttributeValue(attributes, "gender");
        String language = getAttributeValue(attributes, "language");
        String currency = getAttributeValue(attributes, "currency");
        String theme = getAttributeValue(attributes, "theme");
        String density = getAttributeValue(attributes, "density");
        String notifications = getAttributeValue(attributes, "notifications");
        String addressStreet = getAttributeValue(attributes, "street");
        String addressCity = getAttributeValue(attributes, "city");
        String addressState = getAttributeValue(attributes, "state");
        String addressCountry = getAttributeValue(attributes, "country");
        String addressPostalCode = getAttributeValue(attributes, "postalCode");

        // Create address object
        AddressInfo address = new AddressInfo(
                addressStreet,
                addressCity,
                addressState,
                addressCountry,
                addressPostalCode);

        return new CustomerVm(
                userRepresentation.getId(),
                userRepresentation.getUsername(),
                userRepresentation.getEmail(),
                userRepresentation.getFirstName(),
                userRepresentation.getLastName(),
                fullName.trim(),
                picture,
                phone,
                dateOfBirth,
                gender,
                language,
                currency,
                theme,
                density,
                notifications,
                address);
    }

    private static String getAttributeValue(Map<String, java.util.List<String>> attributes, String key) {
        if (attributes != null && attributes.containsKey(key)) {
            var valueList = attributes.get(key);
            if (valueList != null && !valueList.isEmpty()) {
                return valueList.get(0);
            }
        }
        return null;
    }
}
