package com.restaurant.pos.client.controller;

import com.restaurant.pos.auth.domain.User;
import com.restaurant.pos.client.domain.Client;
import com.restaurant.pos.client.service.ClientService;
import com.restaurant.pos.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
@Slf4j
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Client>>> getAllClients() {
        return ResponseEntity.ok(ApiResponse.success(clientService.getAllClients()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Client>> getClient(@PathVariable UUID id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        if (!isSuperAdmin && !user.getClientId().equals(id)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied: You can only access your own client data."));
        }

        return ResponseEntity.ok(ApiResponse.success(clientService.getClientById(id)));
    }

    @GetMapping("/by-email")
    public ResponseEntity<Client> getClientByEmail(@RequestParam String email) {
        // Based on the frontend expectation, this returns the raw object directly, not wrapped in ApiResponse
        return ResponseEntity.ok(clientService.getClientByEmail(email));
    }

    /**
     * Returns the client for the currently authenticated user.
     * The JwtAuthenticationFilter loads the full User entity as the principal,
     * so we can cast it directly and use users.client_id — always correctly linked.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Client>> getCurrentClient(Authentication authentication) {
        System.out.println("===> [DEBUG] ClientController /me: authentication=" + authentication);
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        User user = (User) authentication.getPrincipal();
        System.out.println("===> [DEBUG] ClientController /me: User=" + user.getEmail() + ", Roles=" + authentication.getAuthorities());
        UUID clientId = user.getClientId();
        log.info("Requesting client data for client ID: {}", clientId);
        return ResponseEntity.ok(ApiResponse.success(clientService.getClientById(clientId)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Client>> updateClient(@PathVariable UUID id, @RequestBody Client client, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        if (!isSuperAdmin && !user.getClientId().equals(id)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied: You can only modify your own client data."));
        }

        return ResponseEntity.ok(ApiResponse.success(clientService.updateClient(id, client)));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Client>> createClient(@RequestBody Client client) {
        return ResponseEntity.ok(ApiResponse.success(clientService.createClient(client)));
    }
}
