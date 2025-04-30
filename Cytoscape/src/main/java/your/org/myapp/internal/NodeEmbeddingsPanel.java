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
        buildLayout();
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
                 // Lấy các giá trị đã chọn
                 String networkType = (String) networkTypeComboBox.getSelectedItem();
                 String dimension = (String) dimensionComboBox.getSelectedItem();
                 String model = (String) modelComboBox.getSelectedItem();
                 System.out.println("Running with: Network=" + networkType +
                                    ", Dimension=" + dimension + ", Model=" + model);
                 // TODO: Gọi TaskFactory tương ứng để thực thi
             }
         });
    }

    private void buildLayout() {
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        // ---- Section Network ----
        JPanel networkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        networkPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        networkPanel.add(networkLabel);
        networkPanel.add(networkTypeComboBox);
        add(networkPanel);

        // Thêm khoảng cách giữa các section
        add(Box.createRigidArea(new Dimension(0, 10)));

        // ---- Section Dimension ----
        JPanel dimensionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        dimensionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dimensionPanel.add(dimensionLabel);
        dimensionPanel.add(dimensionComboBox);
        add(dimensionPanel);

        add(Box.createRigidArea(new Dimension(0, 10)));

        // ---- Section Models ----
        JPanel modelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modelPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        modelPanel.add(modelLabel);
        modelPanel.add(modelComboBox);
        add(modelPanel);

        add(Box.createRigidArea(new Dimension(0, 15))); // Khoảng cách lớn hơn trước nút Run

        // ---- Run Button ----
        // Tạo panel riêng cho nút để căn giữa hoặc căn trái dễ hơn nếu cần
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER)); // Căn giữa nút
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT); // Panel căn trái
        buttonPanel.add(runButton);
        add(buttonPanel);

        // Đẩy mọi thứ lên trên
        add(Box.createVerticalGlue());
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