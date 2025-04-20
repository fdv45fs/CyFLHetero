package your.org.myapp.internal;

import java.io.IOException;
import java.util.List;

import javax.swing.JOptionPane;

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
import com.google.gson.JsonObject;

public class PredictLinksTask extends AbstractTask {

    private final CyApplicationManager cyApplicationManager;
    private final String pythonServerUrl = "http://localhost:5001/predict_links"; // Endpoint for link prediction
    private static final Gson gson = new Gson(); // Shared Gson instance

    public PredictLinksTask(CyApplicationManager cyApplicationManager) {
        this.cyApplicationManager = cyApplicationManager;
    }

    @Override
    public void run(TaskMonitor taskMonitor) {
        taskMonitor.setTitle("Predict Link Score");

        CyNetwork currentNetwork = cyApplicationManager.getCurrentNetwork();
        if (currentNetwork == null) {
            taskMonitor.setStatusMessage("No network selected.");
            // JOptionPane.showMessageDialog(null, "Please select a network view first.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<CyNode> selectedNodes = CyTableUtil.getNodesInState(currentNetwork, "selected", true);

        if (selectedNodes.size() != 2) {
            taskMonitor.setStatusMessage("Please select exactly two nodes for link prediction.");
            // JOptionPane.showMessageDialog(null, "Please select exactly two nodes.", "Link Prediction", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Assuming node names are stored in the "name" column
        String node1Name = currentNetwork.getRow(selectedNodes.get(0)).get(CyNetwork.NAME, String.class);
        String node2Name = currentNetwork.getRow(selectedNodes.get(1)).get(CyNetwork.NAME, String.class);

        if (node1Name == null || node2Name == null) {
            taskMonitor.setStatusMessage("Selected nodes must have names.");
            // JOptionPane.showMessageDialog(null, "Selected nodes are missing names.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        taskMonitor.setStatusMessage("Sending request to prediction server for nodes: " + node1Name + " and " + node2Name);

        // Prepare JSON request body using Gson
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("node1_name", node1Name);
        requestBody.addProperty("node2_name", node2Name);

        String responseString = null;
        int statusCode = -1;

        // Send request using Apache HttpClient
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(pythonServerUrl);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(gson.toJson(requestBody))); // Use Gson to convert JsonObject to String

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                responseString = EntityUtils.toString(response.getEntity()); // Read response body
                statusCode = response.getStatusLine().getStatusCode(); // Get status code
                taskMonitor.setStatusMessage("Server responded with status: " + statusCode);
            }
        } catch (IOException e) {
            taskMonitor.setStatusMessage("Failed to connect to the prediction server: " + e.getMessage());
            // JOptionPane.showMessageDialog(null, "Error communicating with the Python server: " + e.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
            return; // Exit task on connection error
        } catch (Exception e) {
             taskMonitor.setStatusMessage("An unexpected error occurred during HTTP request: " + e.getMessage());
             // JOptionPane.showMessageDialog(null, "An unexpected error occurred: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
             return; // Exit task on other errors
        }

        // Parse JSON response using Gson
        JsonObject jsonResponse = null;
        try {
            if (responseString != null && !responseString.isEmpty()) {
                 jsonResponse = gson.fromJson(responseString, JsonObject.class);
            } else {
                taskMonitor.setStatusMessage("Received empty response from server.");
                 // JOptionPane.showMessageDialog(null, "Received empty response from server (Status: " + statusCode + ").", "Server Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (Exception e) {
            taskMonitor.setStatusMessage("Failed to parse JSON response: " + e.getMessage() + "\nResponse: " + responseString);
            // JOptionPane.showMessageDialog(null, "Error parsing server response: " + e.getMessage(), "Parsing Error", JOptionPane.ERROR_MESSAGE);
            return; // Exit task on parsing error
        }

        // Check response status and process result
        if (statusCode == 200 && jsonResponse != null && "success".equals(jsonResponse.get("status").getAsString())) {
            try {
                 double score = jsonResponse.get("score").getAsDouble();
                 taskMonitor.setStatusMessage("Link prediction successful. Updating edge table...");

                 // --- Add score to edge table ---
                 CyTable edgeTable = currentNetwork.getDefaultEdgeTable();
                 String scoreColumnName = "Predicted Link Score";
                 String interactionColumnName = "interaction"; // Tên cột interaction
                 String nameColumnName = CyNetwork.NAME; // Tên cột name chuẩn

                 // Tạo cột điểm số nếu chưa tồn tại
                 if (edgeTable.getColumn(scoreColumnName) == null) {
                     edgeTable.createColumn(scoreColumnName, Double.class, false);
                     taskMonitor.setStatusMessage("Created column '" + scoreColumnName + "'.");
                 }
                 // Tạo cột interaction nếu chưa tồn tại
                 if (edgeTable.getColumn(interactionColumnName) == null) {
                     edgeTable.createColumn(interactionColumnName, String.class, false);
                     taskMonitor.setStatusMessage("Created column '" + interactionColumnName + "'.");
                 }
                  // Tạo cột name nếu chưa tồn tại (thường là đã có sẵn)
                 if (edgeTable.getColumn(nameColumnName) == null) {
                     edgeTable.createColumn(nameColumnName, String.class, false);
                     taskMonitor.setStatusMessage("Created column '" + nameColumnName + "'.");
                 }

                 // Lấy 2 nút đã chọn
                 CyNode node1 = selectedNodes.get(0);
                 CyNode node2 = selectedNodes.get(1);

                 // Tìm các cạnh nối giữa 2 nút này
                 List<CyEdge> connectingEdges = currentNetwork.getConnectingEdgeList(node1, node2, CyEdge.Type.ANY);

                 if (!connectingEdges.isEmpty()) {
                     for (CyEdge edge : connectingEdges) {
                         CyRow edgeRow = currentNetwork.getRow(edge);
                         edgeRow.set(scoreColumnName, score);
                         // Có thể cập nhật cả interaction và name cho cạnh hiện có nếu muốn
                         // edgeRow.set(interactionColumnName, "predicted_interaction");
                         // edgeRow.set(nameColumnName, String.format("%s (predicted_interaction) %s", node1Name, node2Name));
                     }
                     taskMonitor.setStatusMessage(String.format("Completed: Score %.4f added to %d edge(s) between %s and %s.",
                                                   score, connectingEdges.size(), node1Name, node2Name));
                 } else {
                      // Tạo cạnh mới vì không tìm thấy cạnh hiện có
                      CyEdge newEdge = currentNetwork.addEdge(node1, node2, false); // false = undirected edge
                      if (newEdge != null) {
                          CyRow edgeRow = currentNetwork.getRow(newEdge);

                          // Đặt giá trị cho các cột của cạnh mới
                          String interactionValue = "predicted_interaction";
                          String edgeName = String.format("%s (%s) %s", node1Name, interactionValue, node2Name);

                          // Đảm bảo cột tồn tại trước khi set (đã kiểm tra ở trên)
                          edgeRow.set(scoreColumnName, score);
                          edgeRow.set(interactionColumnName, interactionValue);
                          edgeRow.set(nameColumnName, edgeName);

                          taskMonitor.setStatusMessage(String.format("Completed: New edge created between %s and %s with score %.4f.",
                                                        node1Name, node2Name, score));
                      } else {
                           taskMonitor.setStatusMessage(String.format("Warning: Predicted score %.4f, but failed to create new edge between %s and %s.",
                                                         score, node1Name, node2Name));
                      }
                 }
                 // --- End add score to edge table ---

            } catch (Exception e) {
                 taskMonitor.setStatusMessage("Error processing successful response or updating table: " + e.getMessage());
                 // JOptionPane.showMessageDialog(null, "Error processing result or updating edge table: " + e.getMessage(), "Processing Error", JOptionPane.ERROR_MESSAGE);
            }

        } else {
            // Handle errors reported by the server or unexpected status codes
            String errorMessage = "Unknown error";
            if (jsonResponse != null && jsonResponse.has("message")) {
                errorMessage = jsonResponse.get("message").getAsString();
            } else if (responseString != null && !responseString.isEmpty()) {
                 errorMessage = "Server returned status " + statusCode + ". Response: " + responseString.substring(0, Math.min(responseString.length(), 100)) + "..."; // Show partial response
            } else {
                 errorMessage = "Server returned status " + statusCode + " with no details.";
            }
            taskMonitor.setStatusMessage("Prediction failed: " + errorMessage);
            // JOptionPane.showMessageDialog(null, "Prediction failed: " + errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
} 