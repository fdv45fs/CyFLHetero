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
    private final ClusterNodesTaskFactory clusterNodesTaskFactory; // Thêm field
    private final PredictAllLinksTaskFactory predictAllLinksTaskFactory; // Thêm field
    private final ClusterNodesTaskFactory clusterNodesTaskFactory;

    public ShowPanelTaskFactory(BundleContext context, 
                                CySwingApplication cySwingApplication, 
                                TaskManager taskManager, 
                                SendHeteroDataTaskFactory sendHeteroDataTaskFactory,
                                PredictLinksTaskFactory predictLinksTaskFactory,
                                CyNetworkManager cyNetworkManager,
                                ClusterNodesTaskFactory clusterNodesTaskFactory,
                                PredictAllLinksTaskFactory predictAllLinksTaskFactory) { // Thêm tham số
                                ClusterNodesTaskFactory clusterNodesTaskFactory) {
        this.context = context;
        this.cySwingApplication = cySwingApplication;
        this.taskManager = taskManager;
        this.sendHeteroDataTaskFactory = sendHeteroDataTaskFactory;
        this.predictLinksTaskFactory = predictLinksTaskFactory;
        this.cyNetworkManager = cyNetworkManager;
        this.clusterNodesTaskFactory = clusterNodesTaskFactory; // Gán giá trị
        this.predictAllLinksTaskFactory = predictAllLinksTaskFactory; // Gán giá trị
        this.clusterNodesTaskFactory = clusterNodesTaskFactory;
    }

    @Override
    public TaskIterator createTaskIterator() {
        // Truyền các service vào Task
        return new TaskIterator(new ShowPanelTask(context, 
                                                cySwingApplication, 
                                                taskManager, 
                                                sendHeteroDataTaskFactory, 
                                                cyNetworkManager,
                                                clusterNodesTaskFactory,
                                                predictAllLinksTaskFactory));
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
