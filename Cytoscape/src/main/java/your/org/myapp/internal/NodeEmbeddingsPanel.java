package your.org.myapp.internal;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.application.swing.CytoPanelName;

// Panel hiển thị bên trái
public class NodeEmbeddingsPanel extends JPanel implements CytoPanelComponent {

    // Network Section
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

    public NodeEmbeddingsPanel() {
        initComponents();
        buildLayoutWithGridBag();
    }

    private void initComponents() {
        // Section Network
        networkLabel = new JLabel("Network Type:");
        String[] networkOptions = {"Homogeneous Network", "Heterogeneous network"};
        networkTypeComboBox = new JComboBox<>(networkOptions);
        networkTypeComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selected = (String) networkTypeComboBox.getSelectedItem();
                System.out.println("Network type selected: " + selected);
                // TODO: Logic khi chọn network type
            }
        });

        // Section Dimension
        dimensionLabel = new JLabel("Dimension:");
        String[] dimensionOptions = {"32", "64", "128", "256"};
        dimensionComboBox = new JComboBox<>(dimensionOptions);
        dimensionComboBox.setSelectedItem("128"); // Giá trị mặc định
        dimensionComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selected = (String) dimensionComboBox.getSelectedItem();
                System.out.println("Dimension selected: " + selected);
                // TODO: Logic khi chọn dimension
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
                // TODO: Logic khi chọn model (ví dụ: ẩn/hiện các tùy chọn khác)
            }
        });

        // Run Button
        runButton = new JButton("Run");
        runButton.addActionListener(new ActionListener() {
             @Override
             public void actionPerformed(ActionEvent e) {
                 System.out.println("Run button clicked");
                 String networkType = (String) networkTypeComboBox.getSelectedItem();
                 String dimension = (String) dimensionComboBox.getSelectedItem();
                 String model = (String) modelComboBox.getSelectedItem();
                 System.out.println("Running with: Network=" + networkType +
                                    ", Dimension=" + dimension + ", Model=" + model);
                 // TODO: Gọi TaskFactory tương ứng để thực thi
             }
         });
    }

    private void buildLayoutWithGridBag() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(networkLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(networkTypeComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(dimensionLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(dimensionComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        add(modelLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(modelComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(10, 2, 2, 2);
        add(runButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
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