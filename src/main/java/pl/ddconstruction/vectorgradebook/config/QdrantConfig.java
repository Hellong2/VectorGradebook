package pl.ddconstruction.vectorgradebook.config;

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
                QdrantGrpcClient.newBuilder("localhost", 6334, false)
                        .build());
    }

    @Bean
    public CommandLineRunner initCollection(QdrantClient client) {
        return args -> {
            String collectionName = "students";
            try {
                client.createCollectionAsync(
                        collectionName,
                        VectorParams.newBuilder()
                                .setSize(4)
                                .setDistance(Distance.Cosine)
                                .build())
                        .get();
                System.out.println("Collection 'students' created.");
            } catch (ExecutionException e) {
                // If it already exists, Qdrant returns an error. We can ignore if it says
                // "already exists"
                // or just log it. For now, we assume failure means it likely exists or
                // connection failed.
                System.out.println("Collection creation skipped (likely exists): " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
