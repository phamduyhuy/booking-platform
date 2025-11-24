package com.pdh.customer.service;

import com.pdh.common.exceptions.*;
import com.pdh.customer.common.Constants;
import com.pdh.customer.config.KeycloakPropsConfig;
import com.pdh.customer.viewmodel.*;
import jakarta.ws.rs.core.Response;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.apache.commons.validator.routines.EmailValidator;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);
    private static final String ERROR_FORMAT = "%s: Client %s don't have access right for this resource";
    private static final int USER_PER_PAGE = 10;
    private static final String GUEST = "GUEST";
    private final Keycloak keycloak;
    private final KeycloakPropsConfig keycloakPropsConfig;

    public CustomerService(Keycloak keycloak, KeycloakPropsConfig keycloakPropsConfig) {
        this.keycloak = keycloak;
        this.keycloakPropsConfig = keycloakPropsConfig;
    }

    public static CredentialRepresentation createPasswordCredentials(String password) {
        CredentialRepresentation passwordCredentials = new CredentialRepresentation();
        passwordCredentials.setTemporary(false);
        passwordCredentials.setType(CredentialRepresentation.PASSWORD);
        passwordCredentials.setValue(password);
        return passwordCredentials;
    }

    public CustomerListVm getCustomers(int pageNo) {
        try {

            List<CustomerAdminVm> result = keycloak.realm(keycloakPropsConfig.getRealm()).users()
                    .search(null, pageNo * USER_PER_PAGE, USER_PER_PAGE).stream()
                    .filter(UserRepresentation::isEnabled)
                    .map(CustomerAdminVm::fromUserRepresentation)
                    .toList();
            int totalUser = result.size();

            return new CustomerListVm(totalUser, result, (totalUser + USER_PER_PAGE - 1) / USER_PER_PAGE);
        } catch (ForbiddenException exception) {
            throw new AccessDeniedException(
                    String.format(ERROR_FORMAT, exception.getMessage(), keycloakPropsConfig.getResource()));
        }
    }

    public void updateCustomer(String id, CustomerProfileRequestVm requestVm) {
        UserRepresentation userRepresentation = keycloak.realm(keycloakPropsConfig.getRealm()).users().get(id)
                .toRepresentation();
        if (userRepresentation != null) {
            userRepresentation.setFirstName(requestVm.firstName());
            userRepresentation.setLastName(requestVm.lastName());
            userRepresentation.setEmail(requestVm.email());
            RealmResource realmResource = keycloak.realm(keycloakPropsConfig.getRealm());
            UserResource userResource = realmResource.users().get(id);
            userResource.update(userRepresentation);
        } else {
            throw new NotFoundException(Constants.ErrorCode.USER_NOT_FOUND);
        }
    }

    public void updateCustomerPassword(String id, CustomerPasswordRequestVm passwordRequestVm) {
        try {
            RealmResource realmResource = keycloak.realm(keycloakPropsConfig.getRealm());
            UserResource userResource = realmResource.users().get(id);

            if (userResource == null) {
                throw new NotFoundException(Constants.ErrorCode.USER_NOT_FOUND);
            }

            // Create new password credential
            CredentialRepresentation newPasswordCredential = createPasswordCredentials(passwordRequestVm.newPassword());

            // Update the user's password
            userResource.resetPassword(newPasswordCredential);

        } catch (ForbiddenException exception) {
            throw new AccessDeniedException(
                    String.format(ERROR_FORMAT, exception.getMessage(), keycloakPropsConfig.getResource()));
        }
    }

    public void updateCustomerPicture(String id, CustomerPictureRequestVm pictureRequestVm) {
        try {
            UserRepresentation userRepresentation = keycloak.realm(keycloakPropsConfig.getRealm()).users().get(id)
                    .toRepresentation();
            if (userRepresentation != null) {
                // Set the picture attribute
                userRepresentation.singleAttribute("picture", pictureRequestVm.pictureUrl());

                RealmResource realmResource = keycloak.realm(keycloakPropsConfig.getRealm());
                UserResource userResource = realmResource.users().get(id);
                userResource.update(userRepresentation);
            } else {
                throw new NotFoundException(Constants.ErrorCode.USER_NOT_FOUND);
            }
        } catch (ForbiddenException exception) {
            throw new AccessDeniedException(
                    String.format(ERROR_FORMAT, exception.getMessage(), keycloakPropsConfig.getResource()));
        }
    }

    public void updateCustomerAttributes(String id, CustomerAttributesRequestVm attributesRequestVm) {
        try {
            UserRepresentation userRepresentation = keycloak.realm(keycloakPropsConfig.getRealm()).users().get(id)
                    .toRepresentation();
            if (userRepresentation != null) {
                // Update all attributes
                Map<String, String> attributes = attributesRequestVm.attributes();
                log.info("Updating attributes for user {}: {}", id, attributes);

                for (Map.Entry<String, String> entry : attributes.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();

                    if (value != null && ("phone".equalsIgnoreCase(key) || "phone_number".equalsIgnoreCase(key))) {
                        value = value.replaceAll("\\s+", "");
                    }

                    // Use the attribute name directly (frontend now sends simple names)
                    String keycloakKey = key;

                    log.info("Setting attribute {} = {} (Keycloak key: {})", key, value, keycloakKey);
                    userRepresentation.singleAttribute(keycloakKey, value);

                }

                // Log the attributes before update
                log.info("UserRepresentation attributes before update: {}", userRepresentation.getAttributes());

                RealmResource realmResource = keycloak.realm(keycloakPropsConfig.getRealm());
                UserResource userResource = realmResource.users().get(id);

                log.info("Updating user representation in Keycloak for user: {}", id);
                userResource.update(userRepresentation);
                log.info("Successfully updated user attributes in Keycloak for user: {}", id);

                // Verify the update by reading the user back
                UserRepresentation updatedUser = userResource.toRepresentation();
                log.info("UserRepresentation attributes after update: {}", updatedUser.getAttributes());
            } else {
                throw new NotFoundException(Constants.ErrorCode.USER_NOT_FOUND);
            }
        } catch (ForbiddenException exception) {
            log.error("Forbidden error updating user attributes for user {}: {}", id, exception.getMessage());
            throw new AccessDeniedException(
                    String.format(ERROR_FORMAT, exception.getMessage(), keycloakPropsConfig.getResource()));
        } catch (Exception exception) {
            log.error("Error updating user attributes for user {}: {}", id, exception.getMessage(), exception);
            throw exception;
        }
    }

    public void updateCustomerSingleAttribute(String id, String attributeName,
            CustomerSingleAttributeRequestVm attributeRequestVm) {
        try {
            UserRepresentation userRepresentation = keycloak.realm(keycloakPropsConfig.getRealm()).users().get(id)
                    .toRepresentation();
            if (userRepresentation != null) {
                // Update single attribute
                String value = attributeRequestVm.value();
                if (value != null && ("phone".equalsIgnoreCase(attributeName)
                        || "phone_number".equalsIgnoreCase(attributeName))) {
                    value = value.replaceAll("\\s+", "");
                }
                userRepresentation.singleAttribute(attributeName, value);

                RealmResource realmResource = keycloak.realm(keycloakPropsConfig.getRealm());
                UserResource userResource = realmResource.users().get(id);
                userResource.update(userRepresentation);
            } else {
                throw new NotFoundException(Constants.ErrorCode.USER_NOT_FOUND);
            }
        } catch (ForbiddenException exception) {
            throw new AccessDeniedException(
                    String.format(ERROR_FORMAT, exception.getMessage(), keycloakPropsConfig.getResource()));
        }
    }

    // make sure revoke the user's access token before deleting the user
    // if the user is not revoked, the user can still access the system with the old
    // access token
    // so we need to disable the user first, then delete the user
    public void deleteCustomer(String id) {
        UserRepresentation userRepresentation = keycloak.realm(keycloakPropsConfig.getRealm()).users().get(id)
                .toRepresentation();
        if (userRepresentation != null) {
            RealmResource realmResource = keycloak.realm(keycloakPropsConfig.getRealm());
            UserResource userResource = realmResource.users().get(id);
            userResource.logout();
            userRepresentation.setEnabled(false);
            userResource.update(userRepresentation);
            userResource.getUserSessions().forEach((session) -> {
                realmResource.deleteSession(session.getId());
            });
        } else {
            throw new NotFoundException(Constants.ErrorCode.USER_NOT_FOUND);
        }
    }

    public CustomerAdminVm getCustomerByEmail(String email) {
        try {
            if (EmailValidator.getInstance().isValid(email)) {
                List<UserRepresentation> searchResult = keycloak.realm(keycloakPropsConfig.getRealm()).users()
                        .search(email, true);
                if (searchResult.isEmpty()) {
                    throw new NotFoundException(Constants.ErrorCode.USER_WITH_EMAIL_NOT_FOUND, email);
                }
                return CustomerAdminVm.fromUserRepresentation(searchResult.getFirst());
            } else {
                throw new WrongEmailFormatException(Constants.ErrorCode.WRONG_EMAIL_FORMAT, email);
            }
        } catch (ForbiddenException exception) {
            throw new AccessDeniedException(
                    String.format(ERROR_FORMAT, exception.getMessage(), keycloakPropsConfig.getResource()));
        }
    }

    public CustomerVm getCustomerProfile(String userId) {
        try {

            return CustomerVm.fromUserRepresentation(
                    keycloak.realm(keycloakPropsConfig.getRealm()).users().get(userId).toRepresentation());

        } catch (ForbiddenException exception) {
            throw new AccessDeniedException(
                    String.format(ERROR_FORMAT, exception.getMessage(), keycloakPropsConfig.getResource()));
        }
    }

    public GuestUserVm createGuestUser() {
        // Get realm
        RealmResource realmResource = keycloak.realm(keycloakPropsConfig.getRealm());
        String randomGuestName = generateSafeString();
        String guestUserEmail = randomGuestName + "_guest@bookingsmart.huypd.me";
        CredentialRepresentation credential = createPasswordCredentials(GUEST);

        // Define user
        UserRepresentation user = new UserRepresentation();
        user.setUsername(guestUserEmail);
        user.setFirstName(GUEST);
        user.setLastName(randomGuestName);
        user.setEmail(guestUserEmail);
        user.setCredentials(Collections.singletonList(credential));
        user.setEnabled(true);
        Response response = realmResource.users().create(user);

        // get new user
        String userId = CreatedResponseUtil.getCreatedId(response);
        UserResource userResource = realmResource.users().get(userId);
        RoleRepresentation guestRealmRole = realmResource.roles().get(GUEST).toRepresentation();

        // Assign realm role GUEST to user
        userResource.roles().realmLevel().add(Collections.singletonList(guestRealmRole));

        return new GuestUserVm(userId, guestUserEmail, GUEST);
    }

    private String generateSafeString() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(bytes);
    }

    public CustomerVm create(CustomerPostVm customerPostVm) {
        // Get realm
        RealmResource realmResource = keycloak.realm(keycloakPropsConfig.getRealm());

        if (checkUsernameExists(realmResource, customerPostVm.username())) {
            throw new DuplicatedException(Constants.ErrorCode.USERNAME_ALREADY_EXITED, customerPostVm.username());
        }
        if (checkEmailExists(realmResource, customerPostVm.email())) {
            throw new DuplicatedException(Constants.ErrorCode.USER_WITH_EMAIL_ALREADY_EXITED, customerPostVm.email());
        }

        // Define user
        UserRepresentation user = new UserRepresentation();
        user.setUsername(customerPostVm.username());
        user.setFirstName(customerPostVm.firstName());
        user.setLastName(customerPostVm.lastName());
        user.setEmail(customerPostVm.email());
        CredentialRepresentation credential = createPasswordCredentials(customerPostVm.password());
        user.setCredentials(Collections.singletonList(credential));
        user.setEnabled(true);

        Response response = realmResource.users().create(user);

        // get new user
        String userId = CreatedResponseUtil.getCreatedId(response);
        UserResource userResource = realmResource.users().get(userId);

        // Assign realm role to user
        RoleRepresentation realmRole = realmResource.roles().get(customerPostVm.role()).toRepresentation();
        userResource.roles().realmLevel().add(Collections.singletonList(realmRole));
        return CustomerVm.fromUserRepresentation(user);
    }

    private boolean checkUsernameExists(RealmResource realmResource, String username) {
        // Search for users by username
        List<UserRepresentation> users = realmResource.users().search(username, true);
        return !users.isEmpty();
    }

    boolean checkEmailExists(RealmResource realmResource, String email) {
        // Search for users by email
        List<UserRepresentation> users = realmResource.users().search(null, null, null, email, 0, 1);
        return !users.isEmpty();
    }

    public CustomerAdminVm getCustomerById(String id) {
        try {
            UserRepresentation userRepresentation = keycloak.realm(keycloakPropsConfig.getRealm()).users().get(id)
                    .toRepresentation();
            if (userRepresentation == null) {
                throw new NotFoundException(Constants.ErrorCode.USER_NOT_FOUND);
            }
            return CustomerAdminVm.fromUserRepresentation(userRepresentation);
        } catch (ForbiddenException exception) {
            throw new AccessDeniedException(
                    String.format(ERROR_FORMAT, exception.getMessage(), keycloakPropsConfig.getResource()));
        }
    }

    public Map<String, Object> getCustomerStatistics() {
        try {
            RealmResource realmResource = keycloak.realm(keycloakPropsConfig.getRealm());
            int totalCustomers = realmResource.users().count();

            // For now, we assume most users are active.
            // Keycloak API doesn't provide an efficient way to count by enabled status or
            // creation date without iterating.

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalCustomers", totalCustomers);
            stats.put("activeCustomers", totalCustomers);
            stats.put("newCustomersThisMonth", 0); // Placeholder as we can't easily query this from Keycloak

            return stats;
        } catch (ForbiddenException exception) {
            throw new AccessDeniedException(
                    String.format(ERROR_FORMAT, exception.getMessage(), keycloakPropsConfig.getResource()));
        }
    }
}
