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
import org.cytoscape.model.events.NetworkAddedListener;
import org.cytoscape.model.events.NetworkAddedEvent;
import org.cytoscape.model.events.NetworkDestroyedListener;
import org.cytoscape.model.events.NetworkDestroyedEvent;

// Panel hiển thị bên trái
public class NodeEmbeddingsPanel extends JPanel implements CytoPanelComponent, NetworkAddedListener, NetworkDestroyedListener {

    // Services
    private final TaskManager<?, ?> taskManager;
    private final SendHeteroDataTaskFactory sendHeteroDataTaskFactory;
    private final SendEdgeIndicesTaskFactory sendEdgeIndicesTaskFactory;
    private final PredictLinksTaskFactory predictLinksTaskFactory; 
    private final CyNetworkManager cyNetworkManager;
    private final ClusterNodesTaskFactory clusterNodesTaskFactory;
    private final PredictAllLinksTaskFactory predictAllLinksTaskFactory;
    
    // Need ApplicationManager to get and set current network
    private org.cytoscape.application.CyApplicationManager applicationManager;
    private org.cytoscape.view.model.CyNetworkViewManager networkViewManager;
    
    // Flag to prevent circular updates
    private boolean isUpdatingDropdown = false;

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
    private JButton runButton;

    // Task Section
    private JLabel taskLabel;
    private JComboBox<String> taskComboBox;
    private JButton runTaskButton;

    public NodeEmbeddingsPanel(TaskManager<?, ?> taskManager,
                               SendHeteroDataTaskFactory sendHeteroDataTaskFactory,
                               SendEdgeIndicesTaskFactory sendEdgeIndicesTaskFactory,
                               PredictLinksTaskFactory predictLinksTaskFactory,
                               CyNetworkManager cyNetworkManager,
                               ClusterNodesTaskFactory clusterNodesTaskFactory,
                               PredictAllLinksTaskFactory predictAllLinksTaskFactory,
                               org.cytoscape.application.CyApplicationManager applicationManager,
                               org.cytoscape.view.model.CyNetworkViewManager networkViewManager) {
        this.taskManager = taskManager;
        this.sendHeteroDataTaskFactory = sendHeteroDataTaskFactory;
        this.sendEdgeIndicesTaskFactory = sendEdgeIndicesTaskFactory;
        this.predictLinksTaskFactory = predictLinksTaskFactory;
        this.cyNetworkManager = cyNetworkManager;
        this.clusterNodesTaskFactory = clusterNodesTaskFactory;
        this.predictAllLinksTaskFactory = predictAllLinksTaskFactory;
        this.applicationManager = applicationManager;
        this.networkViewManager = networkViewManager;
        initComponents();
        buildLayoutWithGridBag();
    }

    private void initComponents() {
        // Section Select Network (User can switch network from here)
        selectNetworkLabel = new JLabel("Select Network:");
        networkSelectionComboBox = new JComboBox<>();
        populateNetworkDropdown(); // Show all networks
        
        // Add listener to switch network when user selects
        networkSelectionComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isUpdatingDropdown) {
                    return; // Prevent circular updates
                }
                
                String selectedNetworkName = (String) networkSelectionComboBox.getSelectedItem();
                if (selectedNetworkName == null || selectedNetworkName.equals("No networks loaded")) {
                    return;
                }
                
                // Find and switch to the selected network
                switchToNetwork(selectedNetworkName);
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
        String[] modelOptions = {"MetaPath2Vec", "Node2Vec"};
        modelComboBox = new JComboBox<>(modelOptions);
        modelComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selected = (String) modelComboBox.getSelectedItem();
                System.out.println("Model selected: " + selected);
                // Update global state so tasks know which model is selected
                ModelState.setCurrentModel(selected);
            }
        });

        // Train Model Button
        runButton = new JButton("Train Model");
        runButton.addActionListener(new ActionListener() {
             @Override
             public void actionPerformed(ActionEvent e) {
                 String selectedModel = (String) modelComboBox.getSelectedItem();
                 TaskIterator taskIterator = null;
                 
                 if ("MetaPath2Vec".equals(selectedModel)) {
                     System.out.println("Train button clicked - Training MetaPath2Vec");
                     taskIterator = sendHeteroDataTaskFactory.createTaskIterator();
                 } else if ("Node2Vec".equals(selectedModel)) {
                     System.out.println("Train button clicked - Training Node2Vec");
                     taskIterator = sendEdgeIndicesTaskFactory.createTaskIterator();
                 } else {
                     JOptionPane.showMessageDialog(null, "Please select a valid model (MetaPath2Vec or Node2Vec)");
                     return;
                 }
                 
                 if (taskIterator != null) {
                     taskManager.execute(taskIterator);
                 }
             }
         });

        // Section Task
        taskLabel = new JLabel("Task:");
        String[] taskOptions = {"Node clustering", "Predict Link Score (2 selected nodes)", "Predict All Links (Top 10)", "Node Classification"};
        taskComboBox = new JComboBox<>(taskOptions);

        // Run Task Button
        runTaskButton = new JButton("Run Task");
        runTaskButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedTask = (String) taskComboBox.getSelectedItem();
                System.out.println("Run Task button clicked for: " + selectedTask);
                
                TaskIterator taskIterator = null;
                
                if ("Node clustering".equals(selectedTask)) {
                    taskIterator = clusterNodesTaskFactory.createTaskIterator();
                } else if ("Predict Link Score (2 selected nodes)".equals(selectedTask)) {
                    taskIterator = predictLinksTaskFactory.createTaskIterator();
                } else if ("Predict All Links (Top 10)".equals(selectedTask)) {
                    taskIterator = predictAllLinksTaskFactory.createTaskIterator();
                } else {
                    JOptionPane.showMessageDialog(null, selectedTask + " is not implemented yet.");
                    System.out.println(selectedTask + " is not implemented yet.");
                }

                if (taskIterator != null) {
                    taskManager.execute(taskIterator);
                }
            }
        });
    }

    /**
     * Populate dropdown with all available networks and auto-select current one
     */
    private void populateNetworkDropdown() {
        isUpdatingDropdown = true; // Prevent ActionListener from firing
        
        networkSelectionComboBox.removeAllItems();
        
        Set<CyNetwork> networks = cyNetworkManager.getNetworkSet();
        
        if (networks.isEmpty()) {
            networkSelectionComboBox.addItem("No networks loaded");
            networkSelectionComboBox.setEnabled(false);
            isUpdatingDropdown = false;
            return;
        }
        
        // Get current network to auto-select it
        CyNetwork currentNetwork = applicationManager.getCurrentNetwork();
        String currentNetworkName = null;
        
        if (currentNetwork != null) {
            currentNetworkName = currentNetwork.getRow(currentNetwork).get(CyNetwork.NAME, String.class);
            if (currentNetworkName == null || currentNetworkName.trim().isEmpty()) {
                currentNetworkName = "Network SUID: " + currentNetwork.getSUID();
            }
        }
        
        // Add all networks to dropdown, sorted by name
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
        
        // Auto-select current network
        if (currentNetworkName != null) {
            networkSelectionComboBox.setSelectedItem(currentNetworkName);
            System.out.println("Auto-selected current network: " + currentNetworkName);
        }
        
        networkSelectionComboBox.setEnabled(true);
        isUpdatingDropdown = false;
    }
    
    /**
     * Switch to the network with the given name
     */
    private void switchToNetwork(String networkName) {
        if (networkName == null) {
            return;
        }
        
        // Find network by name
        CyNetwork targetNetwork = null;
        for (CyNetwork network : cyNetworkManager.getNetworkSet()) {
            String name = network.getRow(network).get(CyNetwork.NAME, String.class);
            if (name == null || name.trim().isEmpty()) {
                name = "Network SUID: " + network.getSUID();
            }
            
            if (networkName.equals(name)) {
                targetNetwork = network;
                break;
            }
        }
        
        if (targetNetwork == null) {
            System.err.println("Network not found: " + networkName);
            return;
        }
        
        // Switch current network
        applicationManager.setCurrentNetwork(targetNetwork);
        System.out.println("Switched to network: " + networkName);
        
        // Also switch view if available
        java.util.Collection<org.cytoscape.view.model.CyNetworkView> views = 
            networkViewManager.getNetworkViews(targetNetwork);
        
        if (!views.isEmpty()) {
            org.cytoscape.view.model.CyNetworkView view = views.iterator().next();
            applicationManager.setCurrentNetworkView(view);
            System.out.println("Switched to network view for: " + networkName);
        }
    }
    
    /**
     * Refresh the dropdown to show all networks and auto-select current one
     */
    public void refreshNetworkDropdown() {
        populateNetworkDropdown();
    }
    
    /**
     * Listen for network added events and refresh dropdown
     */
    @Override
    public void handleEvent(NetworkAddedEvent e) {
        System.out.println("Network added detected - refreshing dropdown");
        // Use SwingUtilities to ensure UI update happens on EDT
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                populateNetworkDropdown();
            }
        });
    }
    
    /**
     * Listen for network destroyed events and refresh dropdown
     */
    @Override
    public void handleEvent(NetworkDestroyedEvent e) {
        System.out.println("Network destroyed detected - refreshing dropdown");
        // Use SwingUtilities to ensure UI update happens on EDT
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                populateNetworkDropdown();
            }
        });
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

        // Hàng 4: Train Button
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

        // Hàng 6: Run Task Button
        gbc.gridy = currentRow++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(5, 2, 2, 2);
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
