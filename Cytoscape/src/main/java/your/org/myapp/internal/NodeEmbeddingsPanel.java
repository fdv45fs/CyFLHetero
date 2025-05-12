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
    private final CyNetworkManager cyNetworkManager;

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

    // Task Section (MỚI)
    private JLabel taskLabel;
    private JComboBox<String> taskComboBox;

    public NodeEmbeddingsPanel(TaskManager taskManager, SendHeteroDataTaskFactory sendHeteroDataTaskFactory, CyNetworkManager cyNetworkManager) {
        this.taskManager = taskManager;
        this.sendHeteroDataTaskFactory = sendHeteroDataTaskFactory;
        this.cyNetworkManager = cyNetworkManager;
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

        // Run Button
        runButton = new JButton("Run");
        runButton.addActionListener(new ActionListener() {
             @Override
             public void actionPerformed(ActionEvent e) {
                 System.out.println("Run button clicked - Executing SendHeteroDataTask (like menu item)");
                 TaskIterator taskIterator = sendHeteroDataTaskFactory.createTaskIterator();
                 taskManager.execute(taskIterator);
             }
         });

        // Section Task (MỚI)
        taskLabel = new JLabel("Task:");
        String[] taskOptions = {"Node clustering", "Link Prediction", "Node Classification"};
        taskComboBox = new JComboBox<>(taskOptions);
        taskComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedTask = (String) taskComboBox.getSelectedItem();
                System.out.println("Task selected: " + selectedTask);
                // TODO: Logic để gọi TaskFactory tương ứng với task được chọn
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

        // Hàng 4: Run Button
        gbc.gridx = 0;
        gbc.gridy = currentRow;
        gbc.gridwidth = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(10, 2, 2, 2); // Khoảng cách trên nút Run
        add(runButton, gbc);
        currentRow++;
        gbc.gridwidth = 1; // Reset gridwidth
        gbc.anchor = GridBagConstraints.WEST; // Reset anchor
        gbc.insets = new Insets(2, 2, 2, 2); // Reset insets

        // Hàng 5: Task (MỚI)
        gbc.gridx = 0;
        gbc.gridy = currentRow;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(taskLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(taskComboBox, gbc);
        currentRow++;

        // Thành phần giãn nở để đẩy mọi thứ lên
        gbc.gridx = 0;
        gbc.gridy = currentRow;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
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