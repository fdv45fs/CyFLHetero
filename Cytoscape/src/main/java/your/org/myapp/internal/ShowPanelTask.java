package your.org.myapp.internal;

import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.application.swing.CytoPanelComponent;
import org.osgi.framework.BundleContext;
import java.util.Properties;
import org.osgi.framework.ServiceRegistration;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.application.swing.CytoPanel;
import org.cytoscape.application.swing.CytoPanelName;
import org.cytoscape.application.swing.CytoPanelState;
import java.awt.Component;
import org.cytoscape.work.TaskManager;

public class ShowPanelTask extends AbstractTask {

    private final BundleContext context;
    private final CySwingApplication cySwingApplication;
    private final TaskManager taskManager;
    private final SendHeteroDataTaskFactory sendHeteroDataTaskFactory;
    public static final String PANEL_ID_PROPERTY = "myapp.panel.id";
    public static final String NODE_EMBEDDINGS_PANEL_ID = "nodeEmbeddingsPanel";

    public ShowPanelTask(BundleContext context, 
                         CySwingApplication cySwingApplication,
                         TaskManager taskManager,
                         SendHeteroDataTaskFactory sendHeteroDataTaskFactory) {
        this.context = context;
        this.cySwingApplication = cySwingApplication;
        this.taskManager = taskManager;
        this.sendHeteroDataTaskFactory = sendHeteroDataTaskFactory;
    }

    @Override
    public void run(TaskMonitor taskMonitor) throws Exception {
        taskMonitor.setTitle("Opening Node Embeddings Panel");

        if (HidePanelTask.panelRegistration != null) {
             taskMonitor.setStatusMessage("Panel already open. Selecting it.");
             System.out.println("Node Embeddings Panel already registered. Selecting it.");
             selectAndShowPanel();
             return;
         }

        NodeEmbeddingsPanel panel = new NodeEmbeddingsPanel(taskManager, sendHeteroDataTaskFactory);

        Properties props = new Properties();
        ServiceRegistration registration = context.registerService(CytoPanelComponent.class.getName(), panel, props);

        HidePanelTask.panelRegistration = registration;

        System.out.println("Node Embeddings Panel registered.");
        taskMonitor.setStatusMessage("Panel opened and selected.");

        selectAndShowPanel();
    }

    private void selectAndShowPanel() {
        CytoPanel cytoPanel = cySwingApplication.getCytoPanel(CytoPanelName.WEST);

        if (cytoPanel.getState() == CytoPanelState.HIDE) {
            cytoPanel.setState(CytoPanelState.DOCK);
        }

        int index = -1;
        for (int i = 0; i < cytoPanel.getCytoPanelComponentCount(); i++) {
             Component comp = cytoPanel.getComponentAt(i);
             if (comp instanceof NodeEmbeddingsPanel) {
                 index = i;
                 break;
             }
         }

        if (index != -1) {
            cytoPanel.setSelectedIndex(index);
        } else {
             System.err.println("Could not find the registered NodeEmbeddingsPanel in CytoPanel WEST.");
        }
    }
} 