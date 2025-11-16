package your.org.myapp.internal;

import java.io.IOException;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.*;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class PredictAllLinksTask extends AbstractTask {

    // Server URLs for different models
    private static final String METAPATH2VEC_URL = "http://localhost:5000/predict_all_links_metapath2vec";
    private static final String HGAT_URL = "http://localhost:5000/predict_all_links_HGAT";
    private static final String GTN_URL = "http://localhost:5000/predict_all_links_GTN";
    
    private final CyApplicationManager cyApplicationManager;
    private static final Gson gson = new Gson();
    private static final int TOP_N = 10; // Number of top links to retrieve

    public PredictAllLinksTask(CyApplicationManager cyApplicationManager) {
        this.cyApplicationManager = cyApplicationManager;
    }

    @Override
    public void run(TaskMonitor taskMonitor) {
        taskMonitor.setTitle("Predict All Links");

        CyNetwork currentNetwork = cyApplicationManager.getCurrentNetwork();
        if (currentNetwork == null) {
            taskMonitor.setStatusMessage("No network selected.");
            showError("Please select a network first.");
            return;
        }

        // Determine which model to use
        String currentModel = ModelState.getCurrentModel();
        String pythonServerUrl;
        
        if ("MetaPath2Vec".equals(currentModel)) {
            pythonServerUrl = METAPATH2VEC_URL;
            System.out.println("[PredictAllLinks] Using MetaPath2Vec");
        } else if ("HGAT".equals(currentModel)) {
            pythonServerUrl = HGAT_URL;
            System.out.println("[PredictAllLinks] Using HGAT");
        } else if ("GTN".equals(currentModel)) {
            pythonServerUrl = GTN_URL;
            System.out.println("[PredictAllLinks] Using GTN");
        } else {
            taskMonitor.setStatusMessage("Predict all links not supported for model: " + currentModel);
            showError("Predict all links is only supported for MetaPath2Vec, HGAT, and GTN models.\nCurrent model: " + currentModel);
            return;
        }

        taskMonitor.setStatusMessage("Sending request to " + currentModel + " prediction server for all links...");

        // Prepare JSON request body
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("top_n", TOP_N);
        
        // HGAT and GTN need node type information
        if ("HGAT".equals(currentModel)) {
            requestBody.addProperty("source_type", "drug");
            requestBody.addProperty("target_type", "gene");
            System.out.println("[PredictAllLinks] HGAT: predicting drug-gene links");
        } else if ("GTN".equals(currentModel)) {
            requestBody.addProperty("source_type", "drug");
            requestBody.addProperty("target_type", "gene");
            System.out.println("[PredictAllLinks] GTN: predicting drug-gene links");
        }

        String responseString = null;
        int statusCode = -1;

        // Send request using Apache HttpClient
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(pythonServerUrl);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(gson.toJson(requestBody)));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                responseString = EntityUtils.toString(response.getEntity());
                statusCode = response.getStatusLine().getStatusCode();
                taskMonitor.setStatusMessage("Server responded with status: " + statusCode);
            }
        } catch (IOException e) {
            taskMonitor.setStatusMessage("Failed to connect to the prediction server: " + e.getMessage());
            showError("Error communicating with the Python server: " + e.getMessage());
            return;
        } catch (Exception e) {
            taskMonitor.setStatusMessage("An unexpected error occurred during HTTP request: " + e.getMessage());
            showError("An unexpected error occurred: " + e.getMessage());
            return;
        }

        // Parse JSON response
        JsonObject jsonResponse = null;
        try {
            if (responseString != null && !responseString.isEmpty()) {
                jsonResponse = gson.fromJson(responseString, JsonObject.class);
            } else {
                taskMonitor.setStatusMessage("Received empty response from server.");
                showError("Received empty response from server.");
                return;
            }
        } catch (Exception e) {
            taskMonitor.setStatusMessage("Failed to parse JSON response: " + e.getMessage());
            showError("Error parsing server response: " + e.getMessage());
            return;
        }

        // Check response status and process result
        if (statusCode == 200 && jsonResponse != null && "success".equals(jsonResponse.get("status").getAsString())) {
            try {
                JsonArray topLinks = jsonResponse.getAsJsonArray("top_links");
                int totalLinks = jsonResponse.get("total_links").getAsInt();
                
                taskMonitor.setStatusMessage(String.format("Received %d top links out of %d total possible links", 
                                                          topLinks.size(), totalLinks));

                // Create or update edge table columns
                CyTable edgeTable = currentNetwork.getDefaultEdgeTable();
                String scoreColumnName = "Predicted Link Score";
                String interactionColumnName = "interaction";
                String nameColumnName = CyNetwork.NAME;

                // Create columns if they don't exist
                if (edgeTable.getColumn(scoreColumnName) == null) {
                    edgeTable.createColumn(scoreColumnName, Double.class, false);
                }
                if (edgeTable.getColumn(interactionColumnName) == null) {
                    edgeTable.createColumn(interactionColumnName, String.class, false);
                }
                if (edgeTable.getColumn(nameColumnName) == null) {
                    edgeTable.createColumn(nameColumnName, String.class, false);
                }

                // Process each top link
                int linksAdded = 0;
                int linksUpdated = 0;
                StringBuilder resultMessage = new StringBuilder();
                resultMessage.append(String.format("Top %d Predicted Links:\n\n", topLinks.size()));

                for (JsonElement linkElement : topLinks) {
                    JsonObject link = linkElement.getAsJsonObject();
                    String node1Name = link.get("node1").getAsString();
                    String node2Name = link.get("node2").getAsString();
                    double score = link.get("score").getAsDouble();

                    // Find nodes in the network
                    CyNode node1 = findNodeByName(currentNetwork, node1Name);
                    CyNode node2 = findNodeByName(currentNetwork, node2Name);

                    if (node1 == null || node2 == null) {
                        taskMonitor.setStatusMessage(String.format("Warning: Nodes not found for link %s - %s", 
                                                                  node1Name, node2Name));
                        continue;
                    }

                    // Check if edge already exists
                    List<CyEdge> connectingEdges = currentNetwork.getConnectingEdgeList(node1, node2, CyEdge.Type.ANY);

                    if (!connectingEdges.isEmpty()) {
                        // Update existing edge(s)
                        for (CyEdge edge : connectingEdges) {
                            CyRow edgeRow = currentNetwork.getRow(edge);
                            edgeRow.set(scoreColumnName, score);
                        }
                        linksUpdated += connectingEdges.size();
                    } else {
                        // Create new edge
                        CyEdge newEdge = currentNetwork.addEdge(node1, node2, false);
                        if (newEdge != null) {
                            CyRow edgeRow = currentNetwork.getRow(newEdge);
                            String interactionValue = "predicted_link";
                            String edgeName = String.format("%s (%s) %s", node1Name, interactionValue, node2Name);
                            
                            edgeRow.set(scoreColumnName, score);
                            edgeRow.set(interactionColumnName, interactionValue);
                            edgeRow.set(nameColumnName, edgeName);
                            linksAdded++;
                        }
                    }

                    resultMessage.append(String.format("%d. %s <-> %s (Score: %.4f)\n", 
                                                      linksAdded + linksUpdated, node1Name, node2Name, score));
                }

                taskMonitor.setStatusMessage(String.format("Completed: %d new edges added, %d existing edges updated", 
                                                          linksAdded, linksUpdated));
                
                // Show results in a dialog
                final String finalMessage = resultMessage.toString();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, 
                        finalMessage,
                        "Link Prediction Results",
                        JOptionPane.INFORMATION_MESSAGE);
                });

            } catch (Exception e) {
                taskMonitor.setStatusMessage("Error processing response or updating network: " + e.getMessage());
                showError("Error processing results: " + e.getMessage());
                e.printStackTrace();
            }

        } else {
            // Handle errors
            String errorMessage = "Unknown error";
            if (jsonResponse != null && jsonResponse.has("message")) {
                errorMessage = jsonResponse.get("message").getAsString();
            } else if (responseString != null && !responseString.isEmpty()) {
                errorMessage = "Server returned status " + statusCode + ". Response: " + 
                              responseString.substring(0, Math.min(responseString.length(), 100)) + "...";
            } else {
                errorMessage = "Server returned status " + statusCode + " with no details.";
            }
            taskMonitor.setStatusMessage("Prediction failed: " + errorMessage);
            showError("Prediction failed: " + errorMessage);
        }
    }

    /**
     * Find a node in the network by its name
     */
    private CyNode findNodeByName(CyNetwork network, String nodeName) {
        for (CyNode node : network.getNodeList()) {
            String name = network.getRow(node).get(CyNetwork.NAME, String.class);
            if (nodeName.equals(name)) {
                return node;
            }
        }
        return null;
    }

    /**
     * Show error message in a dialog
     */
    private void showError(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
        });
    }
}
