package com.datagami.edudron.identity.service;

import com.datagami.edudron.identity.domain.Client;
import com.datagami.edudron.identity.dto.ClientDTO;
import com.datagami.edudron.identity.dto.CreateClientRequest;
import com.datagami.edudron.identity.repo.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClientService {
    
    private static final Logger log = LoggerFactory.getLogger(ClientService.class);
    
    @Autowired
    private ClientRepository clientRepository;
    
    public ClientDTO createClient(CreateClientRequest request) {
        log.info("Creating new client: {}", request.getName());
        
        // Check if slug already exists
        if (clientRepository.findBySlug(request.getSlug()).isPresent()) {
            throw new IllegalArgumentException("A client with slug '" + request.getSlug() + "' already exists");
        }
        
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setSlug(request.getSlug());
        client.setName(request.getName());
        client.setGstin(request.getGstin());
        client.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        client.setCreatedAt(java.time.OffsetDateTime.now());
        
        Client saved = clientRepository.save(client);
        log.info("Created client: {} with ID: {}", saved.getName(), saved.getId());
        
        return toDTO(saved);
    }
    
    @Transactional(readOnly = true)
    public List<ClientDTO> getAllClients() {
        return clientRepository.findAllByOrderByName().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ClientDTO> getActiveClients() {
        return clientRepository.findByIsActiveTrueOrderByName().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public ClientDTO getClientById(UUID id) {
        Client client = clientRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Client not found: " + id));
        return toDTO(client);
    }
    
    public ClientDTO updateClient(UUID id, CreateClientRequest request) {
        log.info("Updating client: {}", id);
        
        Client client = clientRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Client not found: " + id));
        
        // Check if slug is being changed and if new slug already exists
        if (!client.getSlug().equals(request.getSlug())) {
            if (clientRepository.findBySlug(request.getSlug()).isPresent()) {
                throw new IllegalArgumentException("A client with slug '" + request.getSlug() + "' already exists");
            }
        }
        
        client.setName(request.getName());
        client.setSlug(request.getSlug());
        client.setGstin(request.getGstin());
        if (request.getIsActive() != null) {
            client.setIsActive(request.getIsActive());
        }
        
        Client saved = clientRepository.save(client);
        log.info("Updated client: {}", saved.getId());
        
        return toDTO(saved);
    }
    
    public void deleteClient(UUID id) {
        log.info("Deleting client: {}", id);
        
        Client client = clientRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Client not found: " + id));
        
        // Soft delete by setting isActive to false
        client.setIsActive(false);
        clientRepository.save(client);
        
        log.info("Soft deleted client: {}", id);
    }
    
    private ClientDTO toDTO(Client client) {
        return new ClientDTO(
            client.getId(),
            client.getSlug(),
            client.getName(),
            client.getGstin(),
            client.getIsActive(),
            client.getCreatedAt()
        );
    }
}

