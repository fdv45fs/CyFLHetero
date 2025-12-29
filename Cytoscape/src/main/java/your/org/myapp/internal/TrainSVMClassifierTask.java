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
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TrainSVMClassifierTask extends AbstractTask {
    private static final String SERVER_URL = "http://localhost:5000/train_svm_classifier";
    private static final Gson gson = new Gson();
    private final CyApplicationManager applicationManager;

    public TrainSVMClassifierTask(CyApplicationManager applicationManager) {
        this.applicationManager = applicationManager;
    }

    @Override
    public void run(TaskMonitor taskMonitor) throws Exception {
        taskMonitor.setStatusMessage("Preparing SVM classifier training...");
        System.out.println("[SVM Classifier] Starting task");

        // Get current network
        CyNetwork currentNetwork = applicationManager.getCurrentNetwork();
        if (currentNetwork == null) {
            taskMonitor.setStatusMessage("No network is currently selected!");
            System.err.println("[SVM Classifier] No network selected");
            
            SwingUtilities.invokeLater(() -> 
                JOptionPane.showMessageDialog(null, 
                    "No network is currently selected!",
                    "Error", JOptionPane.ERROR_MESSAGE)
            );
            return;
        }

        taskMonitor.setStatusMessage("Reading node labels from table...");
        
        // Read node names and disease attributes
        List<String> nodeNames = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        int hasDiseaseCount = 0;
        int noDiseaseCount = 0;
        int noLabelCount = 0;
        
        for (CyNode node : currentNetwork.getNodeList()) {
            String nodeName = currentNetwork.getRow(node).get("name", String.class);
            
            if (nodeName == null || nodeName.trim().isEmpty()) {
                System.err.println("[SVM Classifier] Skipping node with no name");
                continue;
            }
            
            // Check disease_1 to disease_15 columns
            boolean hasDisease = false;
            boolean hasLabelColumn = false;
            
            for (int i = 1; i <= 15; i++) {
                String columnName = "disease_" + i;
                // Check if column exists
                if (currentNetwork.getDefaultNodeTable().getColumn(columnName) != null) {
                    hasLabelColumn = true;
                    String disease = currentNetwork.getRow(node).get(columnName, String.class);
                    if (disease != null && !disease.trim().isEmpty()) {
                        hasDisease = true;
                        break;
                    }
                }
            }
            
            if (!hasLabelColumn) {
                // No disease columns found for this node
                noLabelCount++;
                continue;
            }
            
            nodeNames.add(nodeName);
            labels.add(hasDisease ? 1 : 0);
            
            if (hasDisease) {
                hasDiseaseCount++;
            } else {
                noDiseaseCount++;
            }
        }
        
        // Check if we found any disease columns
        if (noLabelCount > 0 && nodeNames.isEmpty()) {
            taskMonitor.setStatusMessage("No disease label columns found!");
            System.err.println("[SVM Classifier] No disease_* columns found in node table");
            
            SwingUtilities.invokeLater(() -> 
                JOptionPane.showMessageDialog(null, 
                    "No disease label columns found in node table!\n\n" +
                    "Please import Phenotype2Genes_Wide.tsv as node attributes:\n" +
                    "File → Import → Table from File → Import as Node Table Columns\n\n" +
                    "Expected columns: disease_1, disease_2, ..., disease_15",
                    "Missing Labels", JOptionPane.ERROR_MESSAGE)
            );
            return;
        }
        
        if (nodeNames.isEmpty()) {
            taskMonitor.setStatusMessage("No valid nodes with labels found!");
            System.err.println("[SVM Classifier] No valid nodes found");
            
            SwingUtilities.invokeLater(() -> 
                JOptionPane.showMessageDialog(null, 
                    "No valid nodes found for training!",
                    "Error", JOptionPane.ERROR_MESSAGE)
            );
            return;
        }
        
        System.out.println(String.format("[SVM Classifier] Collected %d nodes: %d with disease, %d without disease",
            nodeNames.size(), hasDiseaseCount, noDiseaseCount));
        
        // Create JSON payload
        JsonObject requestBody = new JsonObject();
        requestBody.add("node_names", gson.toJsonTree(nodeNames));
        requestBody.add("labels", gson.toJsonTree(labels));

        String jsonPayload = gson.toJson(requestBody);
        System.out.println("[SVM Classifier] Sending data to Python server...");

        // Send HTTP POST request
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(SERVER_URL);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(jsonPayload));

            taskMonitor.setStatusMessage("Training SVM classifier... (This may take 1-2 minutes)");
            System.out.println("[SVM Classifier] Waiting for server response...");
            
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                int statusCode = response.getStatusLine().getStatusCode();
                
                if (statusCode == 200) {
                    // Parse response
                    JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
                    JsonObject metrics = responseJson.getAsJsonObject("metrics");
                    JsonObject dataset = responseJson.getAsJsonObject("dataset");
                    JsonObject confusionMatrix = responseJson.getAsJsonObject("confusion_matrix");
                    JsonObject bestParams = responseJson.getAsJsonObject("best_params");
                    
                    double accuracy = metrics.get("accuracy").getAsDouble();
                    double precision = metrics.get("precision").getAsDouble();
                    double recall = metrics.get("recall").getAsDouble();
                    double f1Score = metrics.get("f1_score").getAsDouble();
                    double rocAuc = metrics.get("roc_auc").getAsDouble();
                    
                    int totalNodes = dataset.get("total_nodes").getAsInt();
                    int trainSize = dataset.get("train_size").getAsInt();
                    int testSize = dataset.get("test_size").getAsInt();
                    int hasDiseaseTotal = dataset.get("has_disease_count").getAsInt();
                    int noDiseaseTotal = dataset.get("no_disease_count").getAsInt();
                    int skippedNodes = dataset.get("skipped_nodes").getAsInt();
                    
                    int tn = confusionMatrix.get("true_negative").getAsInt();
                    int fp = confusionMatrix.get("false_positive").getAsInt();
                    int fn = confusionMatrix.get("false_negative").getAsInt();
                    int tp = confusionMatrix.get("true_positive").getAsInt();
                    
                    double bestCvF1 = responseJson.get("best_cv_f1").getAsDouble();
                    
                    String successMessage = String.format(
                        "✅ SVM Classifier trained successfully!\n" +
                        "Accuracy: %.3f | F1-Score: %.3f | ROC-AUC: %.3f",
                        accuracy, f1Score, rocAuc
                    );
                    
                    taskMonitor.setStatusMessage(successMessage);
                    System.out.println("[SVM Classifier] " + successMessage);
                    
                    // Show detailed results dialog
                    final String dialogMessage = String.format(
                        "SVM Classifier Training Results\n" +
                        "================================\n\n" +
                        "📊 Dataset:\n" +
                        "  • Total nodes: %d\n" +
                        "  • Labeled nodes: %d\n" +
                        "  • Has disease: %d (%.1f%%)\n" +
                        "  • No disease: %d (%.1f%%)\n" +
                        (skippedNodes > 0 ? "  ⚠ Skipped nodes (no embeddings): " + skippedNodes + "\n" : "") +
                        "\n" +
                        "🎯 Training:\n" +
                        "  • Train set: %d nodes\n" +
                        "  • Test set: %d nodes\n" +
                        "  • Best CV F1-score: %.3f\n" +
                        "  • Best params: C=%s, gamma=%s\n" +
                        "\n" +
                        "📈 Performance (Test Set):\n" +
                        "  • Accuracy:  %.3f (%.1f%%)\n" +
                        "  • Precision: %.3f\n" +
                        "  • Recall:    %.3f\n" +
                        "  • F1-Score:  %.3f\n" +
                        "  • ROC-AUC:   %.3f\n" +
                        "\n" +
                        "🔢 Confusion Matrix:\n" +
                        "  • True Negatives:  %d\n" +
                        "  • False Positives: %d\n" +
                        "  • False Negatives: %d\n" +
                        "  • True Positives:  %d\n" +
                        "\n" +
                        "💾 Model saved to: svm_disease_classifier.pkl",
                        totalNodes,
                        totalNodes,
                        hasDiseaseTotal, (hasDiseaseTotal * 100.0 / totalNodes),
                        noDiseaseTotal, (noDiseaseTotal * 100.0 / totalNodes),
                        trainSize,
                        testSize,
                        bestCvF1,
                        bestParams.get("C").toString(),
                        bestParams.get("gamma").toString(),
                        accuracy, accuracy * 100,
                        precision,
                        recall,
                        f1Score,
                        rocAuc,
                        tn, fp, fn, tp
                    );
                    
                    SwingUtilities.invokeLater(() -> 
                        JOptionPane.showMessageDialog(null, dialogMessage,
                            "SVM Training Complete", JOptionPane.INFORMATION_MESSAGE)
                    );
                    
                } else {
                    String errorMsg = "❌ Training failed: " + response.getStatusLine();
                    taskMonitor.setStatusMessage(errorMsg);
                    System.err.println("[SVM Classifier] " + errorMsg);
                    System.err.println("[SVM Classifier] Response: " + responseBody);
                    
                    // Parse error message from response if available
                    String errorDetail = responseBody;
                    try {
                        JsonObject errorJson = gson.fromJson(responseBody, JsonObject.class);
                        if (errorJson.has("message")) {
                            errorDetail = errorJson.get("message").getAsString();
                        }
                    } catch (Exception ignored) {}
                    
                    final String finalErrorDetail = errorDetail;
                    SwingUtilities.invokeLater(() -> 
                        JOptionPane.showMessageDialog(null, 
                            "SVM training failed:\n\n" + finalErrorDetail,
                            "Error", JOptionPane.ERROR_MESSAGE)
                    );
                }
            }
        } catch (Exception e) {
            String errorMsg = "Failed to communicate with Python server: " + e.getMessage();
            taskMonitor.setStatusMessage(errorMsg);
            System.err.println("[SVM Classifier] Connection error: " + e.getMessage());
            e.printStackTrace();
            
            SwingUtilities.invokeLater(() -> 
                JOptionPane.showMessageDialog(null, 
                    "Error communicating with Python server:\n" + e.getMessage() +
                    "\n\nPlease ensure:\n" +
                    "1. Python server is running (python server.py)\n" +
                    "2. Node2Vec model has been trained\n" +
                    "3. Labels have been imported into node table",
                    "Connection Error", JOptionPane.ERROR_MESSAGE)
            );
        }
    }
}

