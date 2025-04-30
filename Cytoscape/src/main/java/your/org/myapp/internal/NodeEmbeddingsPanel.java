package your.org.myapp.internal;

import javax.swing.*;
import java.awt.*;
import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.application.swing.CytoPanelName;

// Panel hiển thị bên trái
public class NodeEmbeddingsPanel extends JPanel implements CytoPanelComponent {

    public NodeEmbeddingsPanel() {
        // Giao diện đơn giản
        JLabel label = new JLabel("Node Embeddings Controls Placeholder");
        this.setLayout(new BorderLayout());
        this.add(label, BorderLayout.CENTER);
        this.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public CytoPanelName getCytoPanelName() {
        return CytoPanelName.WEST; // Hiển thị bên trái
    }

    @Override
    public String getTitle() {
        return "Node Embeddings"; // Tiêu đề tab
    }

    @Override
    public Icon getIcon() {
        return null;
    }
} 