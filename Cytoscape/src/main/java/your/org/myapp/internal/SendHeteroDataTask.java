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

import java.util.Arrays;
import java.util.List;

public class SendHeteroDataTask extends AbstractTask {
    private static final String SERVER_URL = "http://localhost:5001/receive_hetero_data";
    private final CyApplicationManager applicationManager;
    private static final Gson gson = new Gson();

    public SendHeteroDataTask(CyApplicationManager applicationManager) {
        this.applicationManager = applicationManager;
    }

    @Override
    public void run(TaskMonitor taskMonitor) throws Exception {
        CyNetwork currentNetwork = applicationManager.getCurrentNetwork();
        if (currentNetwork == null) {
            taskMonitor.setStatusMessage("No network selected!");
            return;
        }

        // 1. Thu thập cạnh
        JsonArray edgeArray = new JsonArray();
        for (CyEdge edge : currentNetwork.getEdgeList()) {
            JsonObject edgeObject = new JsonObject();
            String source = currentNetwork.getRow(edge.getSource()).get("name", String.class);
            String target = currentNetwork.getRow(edge.getTarget()).get("name", String.class);
            edgeObject.addProperty("source", source);
            edgeObject.addProperty("target", target);
            edgeArray.add(edgeObject);
        }

        // 2. Meta-path (cố định là drug -> gene -> drug)
        List<String> metapath = Arrays.asList("drug", "to", "gene", "to", "drug");
        JsonArray metapathJson = gson.toJsonTree(metapath).getAsJsonArray();

        // 3. Tạo JSON payload
        JsonObject requestBody = new JsonObject();
        requestBody.add("edge_index", edgeArray);
        requestBody.add("metapath", metapathJson);

        String jsonPayload = gson.toJson(requestBody);
        System.out.println("Sending JSON payload: " + jsonPayload);

        // Gửi POST request
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(SERVER_URL);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(jsonPayload));

            taskMonitor.setStatusMessage("Sending data to server...");
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                taskMonitor.setStatusMessage("Server response: " + response.getStatusLine());
                System.out.println("Response body: " + responseBody); // In response
            }
        } catch (Exception e) {
            taskMonitor.setStatusMessage("Failed to send data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}