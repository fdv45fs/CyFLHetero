package your.org.myapp.internal;

import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;
import org.osgi.framework.BundleContext;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.work.TaskManager;
import org.cytoscape.model.CyNetworkManager;
import your.org.myapp.internal.SendHeteroDataTaskFactory;
import your.org.myapp.internal.PredictLinksTaskFactory;

public class ShowPanelTaskFactory extends AbstractTaskFactory {

    private final BundleContext context;
    private final CySwingApplication cySwingApplication;
    private final TaskManager taskManager;
    private final SendHeteroDataTaskFactory sendHeteroDataTaskFactory;
    private final PredictLinksTaskFactory predictLinksTaskFactory;
    private final CyNetworkManager cyNetworkManager;
    private final ClusterNodesTaskFactory clusterNodesTaskFactory; // Thêm field

    public ShowPanelTaskFactory(BundleContext context, CySwingApplication cySwingApplication, TaskManager taskManager, SendHeteroDataTaskFactory sendHeteroDataTaskFactory, PredictLinksTaskFactory predictLinksTaskFactory, CyNetworkManager cyNetworkManager) {
    // Cập nhật constructor
    public ShowPanelTaskFactory(BundleContext context, 
                                CySwingApplication cySwingApplication, 
                                TaskManager taskManager, 
                                SendHeteroDataTaskFactory sendHeteroDataTaskFactory,
                                CyNetworkManager cyNetworkManager,
                                ClusterNodesTaskFactory clusterNodesTaskFactory) { // Thêm tham số
        this.context = context;
        this.cySwingApplication = cySwingApplication;
        this.taskManager = taskManager;
        this.sendHeteroDataTaskFactory = sendHeteroDataTaskFactory;
        this.predictLinksTaskFactory = predictLinksTaskFactory;
        this.cyNetworkManager = cyNetworkManager;
        this.clusterNodesTaskFactory = clusterNodesTaskFactory; // Gán giá trị
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new ShowPanelTask(context, cySwingApplication, taskManager, sendHeteroDataTaskFactory, predictLinksTaskFactory, cyNetworkManager));
        // Truyền các service vào Task
        return new TaskIterator(new ShowPanelTask(context, 
                                                cySwingApplication, 
                                                taskManager, 
                                                sendHeteroDataTaskFactory, 
                                                cyNetworkManager,
                                                clusterNodesTaskFactory));
    }

    @Override
    public boolean isReady() {
        return true;
    }
} 