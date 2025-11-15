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
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

import java.util.Collection;

public class SendEdgeIndicesDGITask extends AbstractTask {
    private static final String SERVER_URL = "http://localhost:5000/receive_edge_indices_DGI";
    private final CyApplicationManager applicationManager;
    private static final Gson gson = new Gson();

    public SendEdgeIndicesDGITask(CyApplicationManager applicationManager) {
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

        taskMonitor.setStatusMessage("Collecting edges for DGI unsupervised training...");
        System.out.println("[DGI] Starting data collection (NO features needed!)");

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

        System.out.println("[DGI] Collected " + edges.size() + " edges");

        // Create JSON payload (edges only, no features needed!)
        JsonObject requestBody = new JsonObject();
        requestBody.add("edge_index", edgeArray);

        String jsonPayload = gson.toJson(requestBody);
        System.out.println("[DGI] Sending data to server...");

        // Send HTTP POST request
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(SERVER_URL);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(jsonPayload));

            taskMonitor.setStatusMessage("Training DGI (unsupervised, no features needed)...");
            System.out.println("[DGI] Waiting for server response...");
            
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                int statusCode = response.getStatusLine().getStatusCode();
                
                if (statusCode == 200) {
                    taskMonitor.setStatusMessage("✅ DGI trained successfully! Ready for clustering.");
                    System.out.println("[DGI] Training successful!");
                    System.out.println("[DGI] Response: " + responseBody);
                } else {
                    taskMonitor.setStatusMessage("❌ DGI training failed: " + response.getStatusLine());
                    System.err.println("[DGI] Error response: " + responseBody);
                }
            }
        } catch (Exception e) {
            taskMonitor.setStatusMessage("Failed to train DGI: " + e.getMessage());
            System.err.println("[DGI] Connection error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

