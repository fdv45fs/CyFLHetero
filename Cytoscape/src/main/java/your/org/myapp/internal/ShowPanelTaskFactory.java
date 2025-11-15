package your.org.myapp.internal;

import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;
import org.osgi.framework.BundleContext;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.work.TaskManager;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.event.CyEventHelper;

public class ShowPanelTaskFactory extends AbstractTaskFactory {

    private final BundleContext context;
    private final CySwingApplication cySwingApplication;
    private final TaskManager<?, ?> taskManager;
    private final SendHeteroDataTaskFactory sendHeteroDataTaskFactory;
    private final SendEdgeIndicesTaskFactory sendEdgeIndicesTaskFactory;
    private final PredictLinksTaskFactory predictLinksTaskFactory;
    private final CyNetworkManager cyNetworkManager;
    private final ClusterNodesTaskFactory clusterNodesTaskFactory;
    private final PredictAllLinksTaskFactory predictAllLinksTaskFactory;
    private final org.cytoscape.application.CyApplicationManager applicationManager;
    private final org.cytoscape.view.model.CyNetworkViewManager networkViewManager;
    private final CyEventHelper eventHelper;

    public ShowPanelTaskFactory(BundleContext context, 
                                CySwingApplication cySwingApplication, 
                                TaskManager<?, ?> taskManager,
                                SendHeteroDataTaskFactory sendHeteroDataTaskFactory,
                                SendEdgeIndicesTaskFactory sendEdgeIndicesTaskFactory,
                                PredictLinksTaskFactory predictLinksTaskFactory,
                                CyNetworkManager cyNetworkManager,
                                ClusterNodesTaskFactory clusterNodesTaskFactory,
                                PredictAllLinksTaskFactory predictAllLinksTaskFactory,
                                org.cytoscape.application.CyApplicationManager applicationManager,
                                org.cytoscape.view.model.CyNetworkViewManager networkViewManager,
                                CyEventHelper eventHelper) {
        this.context = context;
        this.cySwingApplication = cySwingApplication;
        this.taskManager = taskManager;
        this.sendHeteroDataTaskFactory = sendHeteroDataTaskFactory;
        this.sendEdgeIndicesTaskFactory = sendEdgeIndicesTaskFactory;
        this.predictLinksTaskFactory = predictLinksTaskFactory;
        this.cyNetworkManager = cyNetworkManager;
        this.clusterNodesTaskFactory = clusterNodesTaskFactory;
        this.predictAllLinksTaskFactory = predictAllLinksTaskFactory;
        this.applicationManager = applicationManager;
        this.networkViewManager = networkViewManager;
        this.eventHelper = eventHelper;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new ShowPanelTask(
            context, 
            cySwingApplication, 
            taskManager, 
            sendHeteroDataTaskFactory,
            sendEdgeIndicesTaskFactory,
            predictLinksTaskFactory,
            cyNetworkManager,
            clusterNodesTaskFactory,
            predictAllLinksTaskFactory,
            applicationManager,
            networkViewManager,
            eventHelper
        ));
    }

    @Override
    public boolean isReady() {
        return true;
    }
}
