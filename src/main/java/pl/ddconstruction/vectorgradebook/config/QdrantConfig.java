package pl.ddconstruction.VectorGradebook.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutionException;

@Configuration
public class QdrantConfig {

    @Bean
    public QdrantClient qdrantClient() {
        return new QdrantClient(
                QdrantGrpcClient.newBuilder("localhost", 6334, false).build());
    }

    @Bean
    public CommandLineRunner initCollection(QdrantClient client) {
        return args -> {
            String collectionName = "students";
            try {
                // Check if collection exists
                boolean exists = client.listCollectionsAsync().get().getCollectionsList().stream()
                        .anyMatch(c -> c.getName().equals(collectionName));

                if (!exists) {
                    client.createCollectionAsync(
                            collectionName,
                            VectorParams.newBuilder()
                                    .setSize(4)
                                    .setDistance(Distance.Cosine)
                                    .build())
                            .get();
                    System.out.println("Collection 'students' created.");
                } else {
                    System.out.println("Collection 'students' already exists.");
                }
            } catch (ExecutionException | InterruptedException e) {
                System.err.println("Failed to initialize Qdrant collection: " + e.getMessage());
                // In production, might want to rethrow or handle more gracefully
            }
        };
    }
}
