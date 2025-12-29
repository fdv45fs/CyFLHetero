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
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.event.CyEventHelper;
import org.cytoscape.model.events.NetworkAddedListener;
import org.cytoscape.model.events.NetworkDestroyedListener;

public class ShowPanelTask extends AbstractTask {

    private final BundleContext context;
    private final CySwingApplication cySwingApplication;
    private final TaskManager<?, ?> taskManager;
    private final SendHeteroDataTaskFactory sendHeteroDataTaskFactory;
    private final SendEdgeIndicesTaskFactory sendEdgeIndicesTaskFactory;
    private final SendEdgeIndicesAndNodeFeatureTaskFactory sendEdgeIndicesAndNodeFeatureTaskFactory;
    private final SendEdgeIndicesAndNodeFeatureGATTaskFactory sendEdgeIndicesAndNodeFeatureGATTaskFactory;
    private final SendEdgeIndicesDGITaskFactory sendEdgeIndicesDGITaskFactory;
    private final SendHeteroDataHGATTaskFactory sendHeteroDataHGATTaskFactory;
    private final SendHeteroDataGTNTaskFactory sendHeteroDataGTNTaskFactory;
    private final PredictLinksTaskFactory predictLinksTaskFactory;
    private final CyNetworkManager cyNetworkManager;
    private final ClusterNodesTaskFactory clusterNodesTaskFactory;
    private final PredictAllLinksTaskFactory predictAllLinksTaskFactory;
    private final TrainSVMClassifierTaskFactory trainSVMClassifierTaskFactory;
    private final org.cytoscape.application.CyApplicationManager applicationManager;
    private final org.cytoscape.view.model.CyNetworkViewManager networkViewManager;
    private final CyEventHelper eventHelper;
    public static final String PANEL_ID_PROPERTY = "myapp.panel.id";
    public static final String NODE_EMBEDDINGS_PANEL_ID = "nodeEmbeddingsPanel";

    public ShowPanelTask(BundleContext context, 
                         CySwingApplication cySwingApplication,
                         TaskManager<?, ?> taskManager,
                         SendHeteroDataTaskFactory sendHeteroDataTaskFactory,
                         SendEdgeIndicesTaskFactory sendEdgeIndicesTaskFactory,
                         SendEdgeIndicesAndNodeFeatureTaskFactory sendEdgeIndicesAndNodeFeatureTaskFactory,
                         SendEdgeIndicesAndNodeFeatureGATTaskFactory sendEdgeIndicesAndNodeFeatureGATTaskFactory,
                         SendEdgeIndicesDGITaskFactory sendEdgeIndicesDGITaskFactory,
                         SendHeteroDataHGATTaskFactory sendHeteroDataHGATTaskFactory,
                         SendHeteroDataGTNTaskFactory sendHeteroDataGTNTaskFactory,
                         PredictLinksTaskFactory predictLinksTaskFactory,
                         CyNetworkManager cyNetworkManager,
                         ClusterNodesTaskFactory clusterNodesTaskFactory,
                         PredictAllLinksTaskFactory predictAllLinksTaskFactory,
                         TrainSVMClassifierTaskFactory trainSVMClassifierTaskFactory,
                         org.cytoscape.application.CyApplicationManager applicationManager,
                         org.cytoscape.view.model.CyNetworkViewManager networkViewManager,
                         CyEventHelper eventHelper) {
        this.context = context;
        this.cySwingApplication = cySwingApplication;
        this.taskManager = taskManager;
        this.sendHeteroDataTaskFactory = sendHeteroDataTaskFactory;
        this.sendEdgeIndicesTaskFactory = sendEdgeIndicesTaskFactory;
        this.sendEdgeIndicesAndNodeFeatureTaskFactory = sendEdgeIndicesAndNodeFeatureTaskFactory;
        this.sendEdgeIndicesAndNodeFeatureGATTaskFactory = sendEdgeIndicesAndNodeFeatureGATTaskFactory;
        this.sendEdgeIndicesDGITaskFactory = sendEdgeIndicesDGITaskFactory;
        this.sendHeteroDataHGATTaskFactory = sendHeteroDataHGATTaskFactory;
        this.sendHeteroDataGTNTaskFactory = sendHeteroDataGTNTaskFactory;
        this.predictLinksTaskFactory = predictLinksTaskFactory;
        this.cyNetworkManager = cyNetworkManager;
        this.clusterNodesTaskFactory = clusterNodesTaskFactory;
        this.predictAllLinksTaskFactory = predictAllLinksTaskFactory;
        this.trainSVMClassifierTaskFactory = trainSVMClassifierTaskFactory;
        this.applicationManager = applicationManager;
        this.networkViewManager = networkViewManager;
        this.eventHelper = eventHelper;
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

        NodeEmbeddingsPanel panel = new NodeEmbeddingsPanel(
            taskManager, 
            sendHeteroDataTaskFactory,
            sendEdgeIndicesTaskFactory,
            sendEdgeIndicesAndNodeFeatureTaskFactory,
            sendEdgeIndicesAndNodeFeatureGATTaskFactory,
            sendEdgeIndicesDGITaskFactory,
            sendHeteroDataHGATTaskFactory,
            sendHeteroDataGTNTaskFactory,
            predictLinksTaskFactory,
            cyNetworkManager,
            clusterNodesTaskFactory,
            predictAllLinksTaskFactory,
            trainSVMClassifierTaskFactory,
            applicationManager,
            networkViewManager
        );

        // Register panel as CytoPanelComponent
        Properties props = new Properties();
        ServiceRegistration registration = context.registerService(CytoPanelComponent.class.getName(), panel, props);

        // Register panel as network event listeners to auto-refresh dropdown
        context.registerService(NetworkAddedListener.class.getName(), panel, new Properties());
        context.registerService(NetworkDestroyedListener.class.getName(), panel, new Properties());

        HidePanelTask.panelRegistration = registration;

        System.out.println("Node Embeddings Panel registered with network event listeners.");
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
