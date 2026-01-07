package com.datagami.edudron.content.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureStorageConfig {

    @Value("${azure.storage.account-name:}")
    private String accountName;

    @Value("${azure.storage.container-name:edudron-media}")
    private String containerName;

    @Value("${azure.storage.connection-string:}")
    private String connectionString;

    @Bean
    public BlobServiceClient blobServiceClient() {
        if (!connectionString.isEmpty()) {
            // Use connection string if provided
            return new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();
        } else if (!accountName.isEmpty()) {
            // Use managed identity if account name is provided
            return new BlobServiceClientBuilder()
                    .endpoint(String.format("https://%s.blob.core.windows.net", accountName))
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();
        } else {
            // Return null if not configured (optional for local development)
            return null;
        }
    }

    @Bean
    public String containerName() {
        return containerName;
    }
}


