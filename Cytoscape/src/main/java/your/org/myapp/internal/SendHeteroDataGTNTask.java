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

import java.util.HashSet;
import java.util.Set;

/**
 * Task to send heterogeneous graph data to GTN training server.
 * 
 * GTN (Graph Transformer Network) automatically learns meta-paths for heterogeneous graphs
 * with multiple node types (e.g., Drug-Gene networks).
 * 
 * This task:
 * 1. Extracts edges with node type information
 * 2. Sends to Python server (port 5000, endpoint /receive_hetero_data_GTN)
 * 3. GTN trains using unsupervised learning with automatic meta-path discovery
 * 
 * Use Case: ChG-Miner (Drug-Gene interactions)
 */
public class SendHeteroDataGTNTask extends AbstractTask {
    private static final String SERVER_URL = "http://localhost:5000/receive_hetero_data_GTN";
    private final CyApplicationManager applicationManager;
    private static final Gson gson = new Gson();
    
    // Default node types for Drug-Gene network
    private static final String DEFAULT_SOURCE_TYPE = "drug";
    private static final String DEFAULT_TARGET_TYPE = "gene";

    public SendHeteroDataGTNTask(CyApplicationManager applicationManager) {
        this.applicationManager = applicationManager;
    }

    @Override
    public void run(TaskMonitor taskMonitor) throws Exception {
        CyNetwork currentNetwork = applicationManager.getCurrentNetwork();
        if (currentNetwork == null) {
            taskMonitor.setStatusMessage("No network selected!");
            return;
        }

        taskMonitor.setStatusMessage("Preparing heterogeneous graph data for GTN training...");
        System.out.println("[GTN] Starting data collection for heterogeneous graph");

        // Build node type mapping
        JsonObject nodeTypes = new JsonObject();
        JsonArray edgeArray = new JsonArray();
        
        Set<String> allNodes = new HashSet<>();
        
        // First pass: collect all edges and detect node types
        for (CyEdge edge : currentNetwork.getEdgeList()) {
            String source = currentNetwork.getRow(edge.getSource()).get("name", String.class);
            String target = currentNetwork.getRow(edge.getTarget()).get("name", String.class);
            
            // Detect node types (heuristic: DB* = drug, P*/Q* = gene)
            String sourceType = detectNodeType(source);
            String targetType = detectNodeType(target);
            
            // Store node types
            if (!nodeTypes.has(source)) {
                nodeTypes.addProperty(source, sourceType);
            }
            if (!nodeTypes.has(target)) {
                nodeTypes.addProperty(target, targetType);
            }
            
            // Create edge entry
            JsonArray edge_pair = new JsonArray();
            edge_pair.add(new JsonPrimitive(source));
            edge_pair.add(new JsonPrimitive(target));
            edgeArray.add(edge_pair);
            
            allNodes.add(source);
            allNodes.add(target);
        }

        System.out.println("[GTN] Collected " + edgeArray.size() + " edges");
        System.out.println("[GTN] Unique nodes: " + allNodes.size());

        // Create JSON payload matching Python's expected format
        JsonObject requestBody = new JsonObject();
        requestBody.add("edges", edgeArray);
        requestBody.add("node_types", nodeTypes);

        String jsonPayload = gson.toJson(requestBody);
        System.out.println("[GTN] Sending data to server...");
        taskMonitor.setStatusMessage("Training GTN model (this may take a few minutes)...");

        // Send HTTP POST request
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(SERVER_URL);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(jsonPayload));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                int statusCode = response.getStatusLine().getStatusCode();
                
                System.out.println("[GTN] Server response status: " + statusCode);
                System.out.println("[GTN] Response: " + responseBody);

                if (statusCode == 200) {
                    taskMonitor.setStatusMessage("✅ GTN trained successfully! Ready for clustering and link prediction.");
                    System.out.println("[GTN] Training successful!");
                } else {
                    taskMonitor.setStatusMessage("❌ GTN training failed: " + response.getStatusLine());
                    System.err.println("[GTN] Error response: " + responseBody);
                }
            }
        } catch (Exception e) {
            taskMonitor.setStatusMessage("Failed to train GTN: " + e.getMessage());
            System.err.println("[GTN] Connection error: " + e.getMessage());
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

