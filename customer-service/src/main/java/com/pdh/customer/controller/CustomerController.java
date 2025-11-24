package com.pdh.customer.controller;

import com.pdh.customer.service.CustomerService;
import com.pdh.customer.viewmodel.*;
import com.pdh.common.utils.AuthenticationUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@Tag(name = "Customers", description = "Customer management and profile operations")
@SecurityRequirement(name = "oauth2")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    // BACKOFFICE ADMIN ENDPOINTS
    @GetMapping("/backoffice/admin/customers")
    public ResponseEntity<CustomerListVm> getCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        CustomerListVm customers = customerService.getCustomers(page);
        return ResponseEntity.ok(customers);
    }

    @GetMapping("/backoffice/admin/customers/statistics")
    public ResponseEntity<Map<String, Object>> getCustomerStatistics() {
        Map<String, Object> stats = customerService.getCustomerStatistics();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/backoffice/admin/customers/{id}")
    public ResponseEntity<CustomerAdminVm> getCustomerById(@PathVariable String id) {
        CustomerAdminVm customer = customerService.getCustomerById(id);
        return ResponseEntity.ok(customer);
    }

    @GetMapping("/backoffice/admin/customers/search")
    public ResponseEntity<CustomerAdminVm> getCustomerByEmail(@RequestParam String email) {
        CustomerAdminVm customer = customerService.getCustomerByEmail(email);
        return ResponseEntity.ok(customer);
    }

    @PostMapping("/backoffice/admin/customers")
    public ResponseEntity<CustomerVm> createCustomer(@Valid @RequestBody CustomerPostVm customerPostVm) {
        CustomerVm customer = customerService.create(customerPostVm);
        return ResponseEntity.status(HttpStatus.CREATED).body(customer);
    }

    @PutMapping("/backoffice/admin/customers/{id}")
    public ResponseEntity<Void> updateCustomer(
            @PathVariable String id,
            @Valid @RequestBody CustomerProfileRequestVm customerProfileRequestVm) {
        customerService.updateCustomer(id, customerProfileRequestVm);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/backoffice/admin/customers/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable String id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }

    // BACKOFFICE PARTNER ENDPOINTS
    @GetMapping("/backoffice/partner/customers")
    public ResponseEntity<CustomerListVm> getCustomersForPartner(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        CustomerListVm customers = customerService.getCustomers(page);
        return ResponseEntity.ok(customers);
    }

    // STOREFRONT ENDPOINTS
    @GetMapping("/storefront/profile")
    public ResponseEntity<CustomerVm> getCustomerProfile() {
        String userId = AuthenticationUtils.extractUserId();
        CustomerVm customer = customerService.getCustomerProfile(userId);
        return ResponseEntity.ok(customer);
    }

    @GetMapping("/storefront/profile/{userId}")
    public ResponseEntity<CustomerVm> getCustomerProfile(@PathVariable String userId) {

        CustomerVm customer = customerService.getCustomerProfile(userId);
        return ResponseEntity.ok(customer);
    }

    @PutMapping("/storefront/profile")
    public ResponseEntity<Void> updateCustomerProfile(
            @Valid @RequestBody CustomerProfileRequestVm customerProfileRequestVm) {
        String userId = AuthenticationUtils.extractUserId();
        customerService.updateCustomer(userId, customerProfileRequestVm);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/storefront/profile/password")
    public ResponseEntity<Void> updateCustomerPassword(
            @Valid @RequestBody CustomerPasswordRequestVm passwordRequestVm) {
        String userId = AuthenticationUtils.extractUserId();
        customerService.updateCustomerPassword(userId, passwordRequestVm);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/storefront/profile/picture")
    public ResponseEntity<Void> updateCustomerPicture(@Valid @RequestBody CustomerPictureRequestVm pictureRequestVm) {
        String userId = AuthenticationUtils.extractUserId();
        customerService.updateCustomerPicture(userId, pictureRequestVm);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/storefront/profile/attributes")
    public ResponseEntity<Void> updateCustomerAttributes(
            @Valid @RequestBody CustomerAttributesRequestVm attributesRequestVm) {
        String userId = AuthenticationUtils.extractUserId();
        customerService.updateCustomerAttributes(userId, attributesRequestVm);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/storefront/profile/attributes/{attributeName}")
    public ResponseEntity<Void> updateCustomerSingleAttribute(
            @PathVariable String attributeName,
            @Valid @RequestBody CustomerSingleAttributeRequestVm attributeRequestVm) {
        String userId = AuthenticationUtils.extractUserId();
        customerService.updateCustomerSingleAttribute(userId, attributeName, attributeRequestVm);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/storefront/guest")
    public ResponseEntity<GuestUserVm> createGuestUser() {
        GuestUserVm guestUser = customerService.createGuestUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(guestUser);
    }
}
