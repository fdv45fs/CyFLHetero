package your.org.myapp.internal;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

import java.util.Map;

public class ClusterNodesTask extends AbstractTask {
    private static final String SERVER_URL = "http://localhost:5001/cluster_nodes"; // URL mới
    private final CyApplicationManager applicationManager;
    private static final Gson gson = new Gson();

    public ClusterNodesTask(CyApplicationManager applicationManager) {
        this.applicationManager = applicationManager;
    }

    @Override
    public void run(TaskMonitor taskMonitor) throws Exception {
        CyNetwork currentNetwork = applicationManager.getCurrentNetwork();
        if (currentNetwork == null) {
            taskMonitor.setStatusMessage("No network selected!");
            return;
        }

        // Tạo request body (có thể cấu hình số cụm từ người dùng)
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("num_clusters", 10); // Số cụm mặc định

        // Gửi request đến server
        String responseString = null;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(SERVER_URL);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(gson.toJson(requestBody)));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                responseString = EntityUtils.toString(response.getEntity()); // Đọc response
                taskMonitor.setStatusMessage("Server response: " + response.getStatusLine());
            }
        } catch (Exception e) {
            taskMonitor.setStatusMessage("Failed to cluster nodes: " + e.getMessage());
            e.printStackTrace();
            return; // Thoát task nếu có lỗi
        }

        // Parse response (quan trọng)
        JsonObject jsonResponse = null;
        try {
            jsonResponse = new Gson().fromJson(responseString, JsonObject.class); // Chuyển response thành JsonObject
        }
        catch (Exception e){
            taskMonitor.setStatusMessage("Failed to parse json response: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        if (!jsonResponse.get("status").getAsString().equals("success")) {
            taskMonitor.setStatusMessage("Server returned error: " + jsonResponse.get("message").getAsString());
            return;
        }

        JsonObject nodeToCluster = jsonResponse.getAsJsonObject("node_to_cluster");

        // Thêm cột "cluster" vào node table (nếu chưa có)
        CyTable nodeTable = currentNetwork.getDefaultNodeTable();
        if (nodeTable.getColumn("cluster") == null) {
            nodeTable.createColumn("cluster", Integer.class, false); // Cột không thay đổi được
        }

        // Cập nhật thuộc tính "cluster" cho từng nút
        for (CyNode node : currentNetwork.getNodeList()) {
            String nodeName = currentNetwork.getRow(node).get("name", String.class);
            if (nodeToCluster.has(nodeName)) {
                int clusterId = nodeToCluster.get(nodeName).getAsInt(); // Lấy cluster ID
                currentNetwork.getRow(node).set("cluster", clusterId); // Set giá trị
            }
        }
        taskMonitor.setStatusMessage("Clustering completed and 'cluster' attribute added.");
    }
}