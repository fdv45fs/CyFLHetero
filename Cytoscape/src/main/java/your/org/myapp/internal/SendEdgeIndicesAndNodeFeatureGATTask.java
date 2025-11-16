package your.org.myapp.internal;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyTable;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

import java.util.Collection;

public class SendEdgeIndicesAndNodeFeatureGATTask extends AbstractTask {
    private static final String SERVER_URL = "http://localhost:5000/receive_edge_indices_and_features_GAT";
    private final CyApplicationManager applicationManager;
    private static final Gson gson = new Gson();

    public SendEdgeIndicesAndNodeFeatureGATTask(CyApplicationManager applicationManager) {
        this.applicationManager = applicationManager;
    }

    @Override
    public void run(TaskMonitor taskMonitor) throws Exception {
        // Access the current network
        CyNetwork currentNetwork = applicationManager.getCurrentNetwork();
        if (currentNetwork == null) {
            taskMonitor.setStatusMessage("No network is currently selected!");
            return;
        }

        taskMonitor.setStatusMessage("Collecting edges for UNSUPERVISED GAT clustering (auto-generates features if needed)...");
        System.out.println("[GAT Unsupervised] Starting data collection for clustering (no features required)");

        // Extract edge indices
        Collection<CyEdge> edges = currentNetwork.getEdgeList();
        JsonArray edgeArray = new JsonArray();
        for (CyEdge edge : edges) {
            JsonObject edgeObject = new JsonObject();
            String source = currentNetwork.getRow(edge.getSource()).get("name", String.class);
            String target = currentNetwork.getRow(edge.getTarget()).get("name", String.class);
            edgeObject.addProperty("source", source);
            edgeObject.addProperty("target", target);
            edgeArray.add(edgeObject);
        }

        // Extract node features
        CyTable nodeTable = currentNetwork.getDefaultNodeTable();
        Collection<CyNode> nodes = currentNetwork.getNodeList();
        JsonArray nodeFeatureArray = new JsonArray();

        for (CyNode node : nodes) {
            JsonObject nodeObject = new JsonObject();
            CyRow row = nodeTable.getRow(node.getSUID());

            // Retrieve node name
            String nodeName = row.get("name", String.class);
            nodeObject.addProperty("name", nodeName);

            // For UNSUPERVISED GAT clustering:
            // - If 'Features' column exists → use it
            // - If not → send empty, Python will auto-generate
            JsonObject featuresObject = new JsonObject();
            
            // Check if 'Features' column exists
            CyColumn featuresColumn = nodeTable.getColumn("Features");
            if (featuresColumn != null) {
                Object featuresValue = row.get("Features", Object.class);
                if (featuresValue != null) {
                    featuresObject.addProperty("Features", featuresValue.toString());
                }
            }
            // If no Features column or value, featuresObject remains empty (Python will auto-generate)

            nodeObject.add("features", featuresObject);
            nodeFeatureArray.add(nodeObject);
        }

        // Create the JSON payload
        JsonObject requestBody = new JsonObject();
        requestBody.add("edge_index", edgeArray);
        requestBody.add("node_features", nodeFeatureArray);

        String jsonPayload = gson.toJson(requestBody);
        System.out.println("[GAT Unsupervised] Sending data to server (edges: " + edges.size() + ", nodes: " + nodes.size() + ")");

        // Send HTTP POST request
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(SERVER_URL);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(jsonPayload));

            taskMonitor.setStatusMessage("Training UNSUPERVISED GAT for clustering (no labels needed)...");
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                int statusCode = response.getStatusLine().getStatusCode();
                
                if (statusCode == 200) {
                    taskMonitor.setStatusMessage("✅ GAT trained successfully! Ready for clustering.");
                    System.out.println("[GAT Unsupervised] Training successful!");
                } else {
                    taskMonitor.setStatusMessage("❌ GAT training failed: " + response.getStatusLine());
                    System.err.println("[GAT Unsupervised] Error response: " + responseBody);
                }
                
                System.out.println("[GAT Unsupervised] Response: " + responseBody);
            }
        } catch (Exception e) {
            taskMonitor.setStatusMessage("Failed to train GAT: " + e.getMessage());
            System.err.println("[GAT Unsupervised] Connection error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

