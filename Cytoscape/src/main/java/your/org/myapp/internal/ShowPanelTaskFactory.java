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
    private final PredictLinksTaskFactory predictLinksTaskFactory;
    private final CyNetworkManager cyNetworkManager;
    private final ClusterNodesTaskFactory clusterNodesTaskFactory;

    public ShowPanelTaskFactory(BundleContext context, 
                                CySwingApplication cySwingApplication, 
                                TaskManager taskManager, 
                                SendHeteroDataTaskFactory sendHeteroDataTaskFactory,
                                PredictLinksTaskFactory predictLinksTaskFactory,
                                CyNetworkManager cyNetworkManager,
                                ClusterNodesTaskFactory clusterNodesTaskFactory) {
        this.context = context;
        this.cySwingApplication = cySwingApplication;
        this.taskManager = taskManager;
        this.sendHeteroDataTaskFactory = sendHeteroDataTaskFactory;
        this.predictLinksTaskFactory = predictLinksTaskFactory;
        this.cyNetworkManager = cyNetworkManager;
        this.clusterNodesTaskFactory = clusterNodesTaskFactory;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new ShowPanelTask(
            context, 
            cySwingApplication, 
            taskManager, 
            sendHeteroDataTaskFactory, 
            predictLinksTaskFactory,
            cyNetworkManager,
            clusterNodesTaskFactory
        ));
    }

    @Override
    public boolean isReady() {
        return true;
    }
}
