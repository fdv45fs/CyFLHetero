package your.org.myapp.internal;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.application.swing.CytoPanelName;
import org.cytoscape.work.TaskManager;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNetwork;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;

// Panel hiển thị bên trái
public class NodeEmbeddingsPanel extends JPanel implements CytoPanelComponent {

    // Services
    private final TaskManager taskManager;
    private final SendHeteroDataTaskFactory sendHeteroDataTaskFactory;
    private final PredictLinksTaskFactory predictLinksTaskFactory; 
    private final CyNetworkManager cyNetworkManager;
    private final ClusterNodesTaskFactory clusterNodesTaskFactory; // Thêm service

    // Select Network Section
    private JLabel selectNetworkLabel;
    private JComboBox<String> networkSelectionComboBox;

    // Network Type Section
    private JLabel networkLabel;
    private JComboBox<String> networkTypeComboBox;

    // Dimension Section
    private JLabel dimensionLabel;
    private JComboBox<String> dimensionComboBox;

    // Models Section
    private JLabel modelLabel;
    private JComboBox<String> modelComboBox;

    // Run Button
    private JButton runButton; // Nút này giờ là "Train"

    // Task Section (MỚI)
    private JLabel taskLabel;
    private JComboBox<String> taskComboBox;
    private JButton runTaskButton; // Nút MỚI cho task

    public NodeEmbeddingsPanel(TaskManager taskManager, 
                               SendHeteroDataTaskFactory sendHeteroDataTaskFactory,
                               CyNetworkManager cyNetworkManager,
                               ClusterNodesTaskFactory clusterNodesTaskFactory) {
    public NodeEmbeddingsPanel(TaskManager taskManager, SendHeteroDataTaskFactory sendHeteroDataTaskFactory, PredictLinksTaskFactory predictLinksTaskFactory, CyNetworkManager cyNetworkManager) {
        this.taskManager = taskManager;
        this.sendHeteroDataTaskFactory = sendHeteroDataTaskFactory;
        this.predictLinksTaskFactory = predictLinksTaskFactory; 
        this.cyNetworkManager = cyNetworkManager;
        this.clusterNodesTaskFactory = clusterNodesTaskFactory; // Gán giá trị
        initComponents();
        buildLayoutWithGridBag();
    }

    private void initComponents() {
        // Section Select Network
        selectNetworkLabel = new JLabel("Select Network:");
        networkSelectionComboBox = new JComboBox<>();
        populateNetworkComboBox();
        networkSelectionComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedNetworkName = (String) networkSelectionComboBox.getSelectedItem();
                if (selectedNetworkName != null && !selectedNetworkName.equals("No networks loaded")) {
                    System.out.println("Network selected from dropdown: " + selectedNetworkName);
                }
            }
        });

        // Section Network Type
        networkLabel = new JLabel("Network Type:");
        String[] networkOptions = {"Homogeneous Network", "Heterogeneous network"};
        networkTypeComboBox = new JComboBox<>(networkOptions);
        networkTypeComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selected = (String) networkTypeComboBox.getSelectedItem();
                System.out.println("Network type selected: " + selected);
            }
        });

        // Section Dimension
        dimensionLabel = new JLabel("Dimension:");
        String[] dimensionOptions = {"32", "64", "128", "256"};
        dimensionComboBox = new JComboBox<>(dimensionOptions);
        dimensionComboBox.setSelectedItem("128");
        dimensionComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selected = (String) dimensionComboBox.getSelectedItem();
                System.out.println("Dimension selected: " + selected);
            }
        });

        // Section Models
        modelLabel = new JLabel("Model:");
        String[] modelOptions = {"MetaPath2Vec", "Others"};
        modelComboBox = new JComboBox<>(modelOptions);
        modelComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selected = (String) modelComboBox.getSelectedItem();
                System.out.println("Model selected: " + selected);
            }
        });

        // Đổi tên nút "Run" thành "Train Model" cho rõ ràng
        runButton = new JButton("Train Model");
        runButton.addActionListener(new ActionListener() {
             @Override
             public void actionPerformed(ActionEvent e) {
                 System.out.println("Train button clicked - Executing SendHeteroDataTask");
                 TaskIterator taskIterator = sendHeteroDataTaskFactory.createTaskIterator();
                 taskManager.execute(taskIterator);
                 String selectedTask = (String) taskComboBox.getSelectedItem();
                 TaskIterator taskIterator = null;

                 if ("Train Metapath2Vec Model".equals(selectedTask)) {
                     System.out.println("Run button clicked - Executing SendHeteroDataTask (Train Metapath2Vec)");
                     taskIterator = sendHeteroDataTaskFactory.createTaskIterator();
                 } else if ("Link Prediction".equals(selectedTask)) {
                     System.out.println("Run button clicked - Executing PredictLinksTask");
                     taskIterator = predictLinksTaskFactory.createTaskIterator();
                 } else if ("Node Classification".equals(selectedTask)) {
                     // TODO: Implement Node Classification Task Factory and call it here
                     System.out.println("Node Classification selected - NOT IMPLEMENTED YET");
                 } else {
                     System.out.println("Run button clicked - No valid task selected or task not yet handled: " + selectedTask); 
                 }

                 if (taskIterator != null) {
                     taskManager.execute(taskIterator);
                 } else {
                    JOptionPane.showMessageDialog(NodeEmbeddingsPanel.this, "Please select a valid task or implement the selected task.", "Task Error", JOptionPane.ERROR_MESSAGE);
                 } 
             }
         });

        // Section Task
        taskLabel = new JLabel("Task:");
        String[] taskOptions = {"Train Metapath2Vec Model", "Link Prediction", "Node Classification"}; 
        taskComboBox = new JComboBox<>(taskOptions);

        // Nút Run MỚI cho Task
        runTaskButton = new JButton("Run Task");
        runTaskButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedTask = (String) taskComboBox.getSelectedItem();
                System.out.println("Run Task button clicked for: " + selectedTask);

                if ("Node clustering".equals(selectedTask)) {
                    // Gọi ClusterNodesTask
                    TaskIterator taskIterator = clusterNodesTaskFactory.createTaskIterator();
                    taskManager.execute(taskIterator);
                } else {
                    // Thông báo cho các task chưa được cài đặt
                    JOptionPane.showMessageDialog(null, selectedTask + " is not implemented yet.");
                    System.out.println(selectedTask + " is not implemented yet.");
                }
                System.out.println("Task selected: " + selectedTask);
                // TODO: Logic để gọi TaskFactory tương ứng với task được chọn (đã được xử lý trong runButton)
            }
        });
    }

    private void populateNetworkComboBox() {
        Set<CyNetwork> networks = cyNetworkManager.getNetworkSet();
        networkSelectionComboBox.removeAllItems();

        if (networks.isEmpty()) {
            networkSelectionComboBox.addItem("No networks loaded");
            networkSelectionComboBox.setEnabled(false);
        } else {
            ArrayList<String> networkNames = new ArrayList<>();
            for (CyNetwork network : networks) {
                String networkName = network.getRow(network).get(CyNetwork.NAME, String.class);
                if (networkName == null || networkName.trim().isEmpty()) {
                    networkName = "Network SUID: " + network.getSUID();
                }
                networkNames.add(networkName);
            }
            Collections.sort(networkNames);
            for (String name : networkNames) {
                networkSelectionComboBox.addItem(name);
            }
            networkSelectionComboBox.setEnabled(true);
        }
    }

    private void buildLayoutWithGridBag() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;

        int currentRow = 0;

        // Hàng 0: Select Network
        gbc.gridx = 0;
        gbc.gridy = currentRow;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(selectNetworkLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(networkSelectionComboBox, gbc);
        currentRow++;

        // Hàng 1: Network Type
        gbc.gridx = 0;
        gbc.gridy = currentRow;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(networkLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(networkTypeComboBox, gbc);
        currentRow++;

        // Hàng 2: Dimension
        gbc.gridx = 0;
        gbc.gridy = currentRow;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(dimensionLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(dimensionComboBox, gbc);
        currentRow++;

        // Hàng 3: Models
        gbc.gridx = 0;
        gbc.gridy = currentRow;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(modelLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(modelComboBox, gbc);
        currentRow++;

        // Hàng 4: Run/Train Button
        gbc.gridy = currentRow++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(10, 2, 2, 2);
        add(runButton, gbc);
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 2, 2);

        // Hàng 5: Task
        gbc.gridy = currentRow++;
        gbc.gridx = 0; add(taskLabel, gbc);
        gbc.gridx = 1; add(taskComboBox, gbc);

        // Hàng 6: Run Task Button (MỚI)
        gbc.gridy = currentRow++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(5, 2, 2, 2); // Khoảng cách nhỏ hơn
        add(runTaskButton, gbc);
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 2, 2);

        // Thành phần giãn nở
        gbc.gridy = currentRow++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        add(Box.createGlue(), gbc);
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public CytoPanelName getCytoPanelName() {
        return CytoPanelName.WEST;
    }

    @Override
    public String getTitle() {
        return "Node Embeddings";
    }

    @Override
    public Icon getIcon() {
        return null;
    }
} 