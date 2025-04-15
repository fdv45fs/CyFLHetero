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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SendDataPy4CyTask extends AbstractTask {

    // Endpoint của CyREST để chạy lệnh Python
    // Lưu ý: Port 1234 là mặc định
    private static final String CYREST_PYTHON_RUN_URL = "http://localhost:1234/v1/commands/python/run";
    // Đường dẫn tuyệt đối hoặc tương đối đến file Python từ nơi Cytoscape được chạy
    // Cần đảm bảo đường dẫn này đúng.
    // Giả sử Cytoscape chạy từ thư mục gốc của project GNN-on-Cytoscape
    private static final String PYTHON_SCRIPT_PATH = "D:/Projects/GNN-on-Cytoscape/Python/py4cy_link_predictor.py";

    private final CyApplicationManager applicationManager;
    private static final Gson gson = new Gson();
    private static final Logger logger = LoggerFactory.getLogger(SendDataPy4CyTask.class);

    public SendDataPy4CyTask(CyApplicationManager applicationManager) {
        this.applicationManager = applicationManager;
    }

    @Override
    public void run(TaskMonitor taskMonitor) throws Exception {
        taskMonitor.setTitle("Send Data and Train (Py4Cy)");
        CyNetwork currentNetwork = applicationManager.getCurrentNetwork();
        if (currentNetwork == null) {
            taskMonitor.setStatusMessage("No network selected!");
            logger.warn("No network selected, task aborted.");
            return;
        }

        taskMonitor.setStatusMessage("Collecting edges from network...");
        logger.info("Collecting edges from network: {}", currentNetwork.getRow(currentNetwork).get(CyNetwork.NAME, String.class));

        // 1. Thu thập cạnh (tương tự SendHeteroDataTask)
        List<Map<String, String>> edgeList = new ArrayList<>();
        for (CyEdge edge : currentNetwork.getEdgeList()) {
            // Lấy tên node từ cột 'name' (hoặc cột bạn dùng để định danh node)
            String source = currentNetwork.getRow(edge.getSource()).get("name", String.class);
            String target = currentNetwork.getRow(edge.getTarget()).get("name", String.class);

            if (source == null || target == null) {
                 logger.warn("Skipping edge with null source/target name: SUIDs ({}, {})", edge.getSource().getSUID(), edge.getTarget().getSUID());
                 continue;
             }

            Map<String, String> edgeMap = new HashMap<>();
            edgeMap.put("source", source);
            edgeMap.put("target", target);
            edgeList.add(edgeMap);
        }
        logger.info("Collected {} edges.", edgeList.size());
        if (edgeList.isEmpty()) {
             taskMonitor.setStatusMessage("No edges found in the network.");
             logger.warn("No edges found, task aborted.");
             return;
         }

        // 2. Meta-path (ví dụ cố định, có thể làm tham số sau này)
        List<String> metapath = Arrays.asList("drug", "to", "gene", "to", "drug");
        logger.info("Using metapath: {}", metapath);

        // 3. Chuẩn bị tham số cho hàm Python
        // Hàm Python: initialize_and_train(edge_list, metapath_list, ...)
        Map<String, Object> pythonArgs = new HashMap<>();
        pythonArgs.put("edge_list", edgeList); // Truyền trực tiếp List<Map>
        pythonArgs.put("metapath_list", metapath); // Truyền trực tiếp List<String>
        // Thêm các tham số khác nếu cần (ví dụ: epochs)
        // pythonArgs.put("epochs", 10);

        // 4. Tạo payload cho CyREST
        // Payload cần chứa đường dẫn file và lệnh gọi hàm với tham số
        // Chúng ta sẽ dùng "run file" command của CyREST
        String pythonFunctionName = "initialize_and_train";
        String command = String.format("%s(**args)", pythonFunctionName);

        JsonObject cyRestPayload = new JsonObject();
        cyRestPayload.addProperty("scriptPath", PYTHON_SCRIPT_PATH);
        cyRestPayload.addProperty("function", pythonFunctionName); // Chỉ định hàm rõ ràng (tùy chọn nhưng nên có)
        // Chuyển đổi pythonArgs thành JSON string
        cyRestPayload.add("args", gson.toJsonTree(pythonArgs));
        // Hoặc có thể dùng cách khác để gọi:
        // cyRestPayload.addProperty("command", command);
        // cyRestPayload.add("args", gson.toJsonTree(Map.of("args", pythonArgs)));

        String jsonPayload = gson.toJson(cyRestPayload);
        logger.debug("Sending CyREST payload to {}: {}", CYREST_PYTHON_RUN_URL, jsonPayload);
        taskMonitor.setStatusMessage("Sending data to Python script via CyREST...");

        // 5. Gửi POST request đến CyREST
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(CYREST_PYTHON_RUN_URL + "?command=run file"); // Thêm command vào query param
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Accept", "application/json");
            httpPost.setEntity(new StringEntity(jsonPayload, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                logger.info("CyREST response status: {}", statusCode);
                logger.debug("CyREST response body: {}", responseBody);

                if (statusCode >= 200 && statusCode < 300) {
                    // Parse kết quả trả về từ Python (nằm trong 'data' của response CyREST)
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                    JsonObject pythonResult = jsonResponse.getAsJsonObject("data"); // Kết quả của hàm Python nằm ở đây

                    if (pythonResult != null) {
                        String status = pythonResult.has("status") ? pythonResult.get("status").getAsString() : "unknown";
                        String message = pythonResult.has("message") ? pythonResult.get("message").getAsString() : "No message from Python.";

                        if ("success".equalsIgnoreCase(status)) {
                            taskMonitor.setStatusMessage("Python script executed successfully: " + message);
                            logger.info("Python execution successful: {}", message);
                        } else {
                            taskMonitor.setStatusMessage("Python script returned error: " + message);
                            logger.error("Python script returned error: {}", message);
                        }
                    } else {
                         taskMonitor.setStatusMessage("CyREST call successful, but no 'data' field in response.");
                         logger.error("CyREST call successful, but no 'data' field in response body: {}", responseBody);
                    }
                } else {
                    // Xử lý lỗi từ CyREST
                    taskMonitor.setStatusMessage("Error calling CyREST: " + response.getStatusLine().getReasonPhrase());
                    logger.error("Error calling CyREST ({}): {}\nResponse: {}", statusCode, response.getStatusLine().getReasonPhrase(), responseBody);
                }
            }
        } catch (IOException e) {
            taskMonitor.setStatusMessage("Failed to connect to CyREST/Python: " + e.getMessage());
            logger.error("IOException during CyREST call: ", e);
            // Ném lại lỗi để Cytoscape biết task thất bại
            throw new Exception("Failed to communicate with Python script via CyREST.", e);
        } catch (Exception e) {
            taskMonitor.setStatusMessage("An unexpected error occurred: " + e.getMessage());
            logger.error("Unexpected error in SendDataPy4CyTask: ", e);
            throw e;
        }
    }
} 