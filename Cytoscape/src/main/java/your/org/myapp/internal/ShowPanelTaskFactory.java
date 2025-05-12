package your.org.myapp.internal;

import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;
import org.osgi.framework.BundleContext;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.work.TaskManager;
import org.cytoscape.model.CyNetworkManager;

public class ShowPanelTaskFactory extends AbstractTaskFactory {

    private final BundleContext context;
    private final CySwingApplication cySwingApplication;
    private final TaskManager taskManager;
    private final SendHeteroDataTaskFactory sendHeteroDataTaskFactory;
    private final CyNetworkManager cyNetworkManager;

    public ShowPanelTaskFactory(BundleContext context, CySwingApplication cySwingApplication, TaskManager taskManager, SendHeteroDataTaskFactory sendHeteroDataTaskFactory, CyNetworkManager cyNetworkManager) {
        this.context = context;
        this.cySwingApplication = cySwingApplication;
        this.taskManager = taskManager;
        this.sendHeteroDataTaskFactory = sendHeteroDataTaskFactory;
        this.cyNetworkManager = cyNetworkManager;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new ShowPanelTask(context, cySwingApplication, taskManager, sendHeteroDataTaskFactory, cyNetworkManager));
    }

    @Override
    public boolean isReady() {
        // Luôn sẵn sàng để mở panel (trừ khi nó đã mở, nhưng task sẽ xử lý việc đó)
        return true;
    }
} 