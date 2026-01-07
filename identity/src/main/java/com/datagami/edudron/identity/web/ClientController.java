package com.datagami.edudron.identity.web;

import com.datagami.edudron.identity.dto.ClientDTO;
import com.datagami.edudron.identity.dto.CreateClientRequest;
import com.datagami.edudron.identity.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenant")
@Tag(name = "Tenant Management", description = "Manage tenants (clients)")
public class ClientController {
    
    @Autowired
    private ClientService clientService;
    
    @PostMapping
    @Operation(summary = "Create tenant", description = "Create a new tenant (client). Only SYSTEM_ADMIN can create tenants.")
    public ResponseEntity<ClientDTO> createClient(@Valid @RequestBody CreateClientRequest request) {
        ClientDTO client = clientService.createClient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(client);
    }
    
    @GetMapping
    @Operation(summary = "List all tenants", description = "Get all tenants. SYSTEM_ADMIN can see all, others see only their tenant.")
    public ResponseEntity<List<ClientDTO>> getAllClients() {
        List<ClientDTO> clients = clientService.getAllClients();
        return ResponseEntity.ok(clients);
    }
    
    @GetMapping("/active")
    @Operation(summary = "List active tenants", description = "Get all active tenants")
    public ResponseEntity<List<ClientDTO>> getActiveClients() {
        List<ClientDTO> clients = clientService.getActiveClients();
        return ResponseEntity.ok(clients);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get tenant", description = "Get tenant details by ID")
    public ResponseEntity<ClientDTO> getClient(@PathVariable UUID id) {
        ClientDTO client = clientService.getClientById(id);
        return ResponseEntity.ok(client);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update tenant", description = "Update tenant details. Only SYSTEM_ADMIN can update tenants.")
    public ResponseEntity<ClientDTO> updateClient(
            @PathVariable UUID id,
            @Valid @RequestBody CreateClientRequest request) {
        ClientDTO client = clientService.updateClient(id, request);
        return ResponseEntity.ok(client);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete tenant", description = "Soft delete a tenant (sets isActive to false). Only SYSTEM_ADMIN can delete tenants.")
    public ResponseEntity<Void> deleteClient(@PathVariable UUID id) {
        clientService.deleteClient(id);
        return ResponseEntity.noContent().build();
    }
}


