package your.org.myapp.internal;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
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
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyRow;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

public class SendHeteroDataTask extends AbstractTask {
    private static final String SERVER_URL = "http://localhost:5000/receive_hetero_data"; // Updated to port 5000
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
        taskMonitor.setStatusMessage("Sending data to Metapath2Vec training server...");

        // Gửi POST request
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(SERVER_URL);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(jsonPayload));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                int statusCode = response.getStatusLine().getStatusCode();
                System.out.println("Server response status: " + statusCode);
                System.out.println("Response body: " + responseBody);

                if (statusCode == 200) {
                    JsonObject serverResponseJson = gson.fromJson(responseBody, JsonObject.class);
                    if (serverResponseJson.has("status") && "success".equals(serverResponseJson.get("status").getAsString())) {
                        if (serverResponseJson.has("embeddings")) {
                            JsonObject embeddingsMapJson = serverResponseJson.getAsJsonObject("embeddings");
                            taskMonitor.setStatusMessage("Received embeddings. Updating node table...");

                            CyTable nodeTable = currentNetwork.getDefaultNodeTable();
                            String embeddingColumnName = "Metapath2Vec Embedding";
                            if (nodeTable.getColumn(embeddingColumnName) == null) {
                                nodeTable.createColumn(embeddingColumnName, String.class, false);
                                taskMonitor.setStatusMessage("Created column '" + embeddingColumnName + "'.");
                            }

                            int updatedNodesCount = 0;
                            for (CyNode node : currentNetwork.getNodeList()) {
                                CyRow nodeRow = currentNetwork.getRow(node);
                                String nodeName = nodeRow.get(CyNetwork.NAME, String.class);

                                if (nodeName != null && embeddingsMapJson.has(nodeName)) {
                                    JsonArray embeddingArrayJson = embeddingsMapJson.getAsJsonArray(nodeName);
                                    List<String> embeddingValues = new ArrayList<>();
                                    for (JsonElement element : embeddingArrayJson) {
                                        embeddingValues.add(String.format("%.4f", element.getAsDouble()));
                                    }
                                    String embeddingString = String.join(", ", embeddingValues);
                                    nodeRow.set(embeddingColumnName, embeddingString);
                                    updatedNodesCount++;
                                }
                            }
                            taskMonitor.setStatusMessage("Successfully updated " + updatedNodesCount + " nodes with embeddings.");
                        } else {
                            taskMonitor.setStatusMessage("Server response success, but no embeddings found in the response.");
                            System.err.println("No 'embeddings' field in JSON response from server.");
                        }
                    } else {
                        String errorMsg = serverResponseJson.has("message") ? serverResponseJson.get("message").getAsString() : "Unknown error from server.";
                        taskMonitor.setStatusMessage("Server reported an error: " + errorMsg);
                        System.err.println("Server error: " + errorMsg);
                    }
                } else {
                    taskMonitor.setStatusMessage("Failed to get valid response from server. Status: " + statusCode);
                    System.err.println("Server returned error status: " + statusCode + " Body: " + responseBody);
                }
            }
        } catch (Exception e) {
            taskMonitor.setStatusMessage("Failed to send data or process response: " + e.getMessage());
            e.printStackTrace();
        }
    }
}