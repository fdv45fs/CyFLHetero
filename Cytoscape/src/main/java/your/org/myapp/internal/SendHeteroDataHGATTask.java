package your.org.myapp.internal;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * Task to send heterogeneous graph data to HGAT training server.
 * 
 * HGAT (Heterogeneous Graph Attention Network) is designed for heterogeneous graphs
 * with multiple node types (e.g., Drug-Gene networks).
 * 
 * This task:
 * 1. Extracts edges with node type information
 * 2. Sends to Python server (port 5000, endpoint /receive_hetero_data_HGAT)
 * 3. HGAT trains using unsupervised learning (no labels needed)
 * 
 * Use Case: ChG-Miner (Drug-Gene interactions)
 */
public class SendHeteroDataHGATTask extends AbstractTask {
    private static final String SERVER_URL = "http://localhost:5000/receive_hetero_data_HGAT";
    private final CyApplicationManager applicationManager;
    private static final Gson gson = new Gson();
    
    // Default node types for Drug-Gene network
    private static final String DEFAULT_SOURCE_TYPE = "drug";
    private static final String DEFAULT_TARGET_TYPE = "gene";

    public SendHeteroDataHGATTask(CyApplicationManager applicationManager) {
        this.applicationManager = applicationManager;
    }

    @Override
    public void run(TaskMonitor taskMonitor) throws Exception {
        CyNetwork currentNetwork = applicationManager.getCurrentNetwork();
        if (currentNetwork == null) {
            taskMonitor.setStatusMessage("No network selected!");
            return;
        }

        taskMonitor.setStatusMessage("Preparing heterogeneous graph data for HGAT training...");
        System.out.println("[HGAT] Starting data collection for heterogeneous graph");

        // Detect node types from node names (heuristic)
        // Assumption: Drug nodes start with "DB", Gene nodes with "P" or "Q"
        Set<String> sourceNodes = new HashSet<>();
        Set<String> targetNodes = new HashSet<>();

        // Collect edges with type information
        JsonArray edgeArray = new JsonArray();
        for (CyEdge edge : currentNetwork.getEdgeList()) {
            JsonObject edgeObject = new JsonObject();
            String source = currentNetwork.getRow(edge.getSource()).get("name", String.class);
            String target = currentNetwork.getRow(edge.getTarget()).get("name", String.class);
            
            // Detect node types (heuristic: DB* = drug, P*/Q* = gene)
            String sourceType = detectNodeType(source);
            String targetType = detectNodeType(target);
            
            edgeObject.addProperty("source", source);
            edgeObject.addProperty("target", target);
            edgeObject.addProperty("source_type", sourceType);
            edgeObject.addProperty("target_type", targetType);
            
            edgeArray.add(edgeObject);
            
            sourceNodes.add(source);
            targetNodes.add(target);
        }

        System.out.println("[HGAT] Collected " + edgeArray.size() + " edges");
        System.out.println("[HGAT] Unique source nodes: " + sourceNodes.size());
        System.out.println("[HGAT] Unique target nodes: " + targetNodes.size());

        // Create JSON payload
        JsonObject requestBody = new JsonObject();
        requestBody.add("edges", edgeArray);
        
        // Add node types (wrap strings in JsonPrimitive for compatibility)
        JsonArray nodeTypesArray = new JsonArray();
        nodeTypesArray.add(new JsonPrimitive(DEFAULT_SOURCE_TYPE));
        nodeTypesArray.add(new JsonPrimitive(DEFAULT_TARGET_TYPE));
        requestBody.add("node_types", nodeTypesArray);

        String jsonPayload = gson.toJson(requestBody);
        System.out.println("[HGAT] Sending data to server...");
        taskMonitor.setStatusMessage("Training HGAT model (this may take a few minutes)...");

        // Send HTTP POST request
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(SERVER_URL);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(jsonPayload));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                int statusCode = response.getStatusLine().getStatusCode();
                
                System.out.println("[HGAT] Server response status: " + statusCode);
                System.out.println("[HGAT] Response: " + responseBody);

                if (statusCode == 200) {
                    taskMonitor.setStatusMessage("✅ HGAT trained successfully! Ready for clustering and link prediction.");
                    System.out.println("[HGAT] Training successful!");
                } else {
                    taskMonitor.setStatusMessage("❌ HGAT training failed: " + response.getStatusLine());
                    System.err.println("[HGAT] Error response: " + responseBody);
                }
            }
        } catch (Exception e) {
            taskMonitor.setStatusMessage("Failed to train HGAT: " + e.getMessage());
            System.err.println("[HGAT] Connection error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Detect node type based on node name prefix.
     * 
     * Heuristic:
     * - DB* → drug
     * - P*, Q* → gene
     * - Default → drug (for unknown)
     */
    private String detectNodeType(String nodeName) {
        if (nodeName == null || nodeName.isEmpty()) {
            return DEFAULT_SOURCE_TYPE;
        }
        
        if (nodeName.startsWith("DB")) {
            return "drug";
        } else if (nodeName.startsWith("P") || nodeName.startsWith("Q")) {
            return "gene";
        } else {
            // Default to drug type
            return "drug";
        }
    }
}

