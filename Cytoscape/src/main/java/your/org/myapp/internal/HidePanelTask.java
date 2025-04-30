package your.org.myapp.internal;

import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.application.swing.CytoPanelComponent;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceRegistration;

public class HidePanelTask extends AbstractTask {

    private final BundleContext context;

    static ServiceRegistration panelRegistration = null;

    public HidePanelTask(BundleContext context) {
        this.context = context;
    }

    @Override
    public void run(TaskMonitor taskMonitor) throws Exception {
        taskMonitor.setTitle("Closing Node Embeddings Panel");

        if (panelRegistration != null) {
            try {
                panelRegistration.unregister();
                System.out.println("Node Embeddings Panel unregistered successfully.");
                taskMonitor.setStatusMessage("Panel closed.");
                panelRegistration = null;
            } catch (IllegalStateException e) {
                System.err.println("Panel already unregistered: " + e.getMessage());
                taskMonitor.setStatusMessage("Panel already closed.");
                panelRegistration = null;
            }
        } else {
            System.out.println("Node Embeddings Panel registration not found or already closed.");
            taskMonitor.setStatusMessage("Panel already closed.");
        }
    }
} 