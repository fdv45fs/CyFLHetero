// File: CyActivator.java
package your.org.myapp.internal;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.service.util.AbstractCyActivator;
import org.osgi.framework.BundleContext;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.work.TaskManager;
import org.cytoscape.model.CyNetworkManager;

import java.util.Properties;

public class CyActivator extends AbstractCyActivator {
    @Override
    public void start(BundleContext context) throws Exception {
        CyApplicationManager applicationManager = getService(context, CyApplicationManager.class);
        CySwingApplication cySwingApplication = getService(context, CySwingApplication.class);
        TaskManager taskManager = getService(context, TaskManager.class);
        CyNetworkManager cyNetworkManager = getService(context, CyNetworkManager.class);

        // Register CountNodesTaskFactory
        CountNodesTaskFactory countNodesFactory = new CountNodesTaskFactory(applicationManager);
        Properties countNodesProps = new Properties();
        countNodesProps.setProperty("preferredMenu", "Apps.MyApp");
        countNodesProps.setProperty("title", "Count Nodes");
        registerService(context, countNodesFactory, org.cytoscape.work.TaskFactory.class, countNodesProps);

        // Register CountEdgesTaskFactory
        CountEdgesTaskFactory countEdgesFactory = new CountEdgesTaskFactory(applicationManager);
        Properties countEdgesProps = new Properties();
        countEdgesProps.setProperty("preferredMenu", "Apps.MyApp");
        countEdgesProps.setProperty("title", "Count Edges");
        registerService(context, countEdgesFactory, org.cytoscape.work.TaskFactory.class, countEdgesProps);

        PrintSelectedNodeFeaturesTaskFactory printSelectedNodeFeaturesTaskFactory = new PrintSelectedNodeFeaturesTaskFactory(applicationManager);
        Properties printSelectedNodeFeaturesProps = new Properties();
        printSelectedNodeFeaturesProps.setProperty("preferredMenu", "Apps.MyApp");
        printSelectedNodeFeaturesProps.setProperty("title", "Print Selected Node Features");
        registerService(context, printSelectedNodeFeaturesTaskFactory, org.cytoscape.work.TaskFactory.class, printSelectedNodeFeaturesProps);

        DisplayEdgeIndicesTaskFactory displayEdgeIndicesTaskFactory = new DisplayEdgeIndicesTaskFactory(applicationManager);
        Properties displayEdgeIndicesProps = new Properties();
        displayEdgeIndicesProps.setProperty("preferredMenu", "Apps.MyApp");
        displayEdgeIndicesProps.setProperty("title", "Display Edge Indices");
        registerService(context, displayEdgeIndicesTaskFactory, org.cytoscape.work.TaskFactory.class, displayEdgeIndicesProps);

        //Node2Vec training
        SendEdgeIndicesTaskFactory sendEdgeIndexTaskFactory = new SendEdgeIndicesTaskFactory(applicationManager);
        Properties sendEdgeIndexProps = new Properties();
        sendEdgeIndexProps.setProperty("preferredMenu", "Apps.MyApp");
        sendEdgeIndexProps.setProperty("title", "Train on Node2Vec");
        registerService(context, sendEdgeIndexTaskFactory, org.cytoscape.work.TaskFactory.class, sendEdgeIndexProps);
        //Node2Vec prediction
        predictNodeNode2VecTaskFactory predictNodeNode2VecTaskFactory = new predictNodeNode2VecTaskFactory(applicationManager);
        Properties predictNodeN2VProps = new Properties();
        predictNodeN2VProps.setProperty("preferredMenu", "Apps.MyApp");
        predictNodeN2VProps.setProperty("title", "Predict class for Node2Vec");
        registerService(context, predictNodeNode2VecTaskFactory, org.cytoscape.work.TaskFactory.class, predictNodeN2VProps);

        //GCN training
        SendEdgeIndicesAndNodeFeatureTaskFactory sendEdgeIndicesAndNodeFeatureTaskFactory = new SendEdgeIndicesAndNodeFeatureTaskFactory(applicationManager);
        Properties sendEdgeIndicesAndNodeFeatureProps = new Properties();
        sendEdgeIndicesAndNodeFeatureProps.setProperty("preferredMenu", "Apps.MyApp");
        sendEdgeIndicesAndNodeFeatureProps.setProperty("title", "Train on GCN");
        registerService(context, sendEdgeIndicesAndNodeFeatureTaskFactory, org.cytoscape.work.TaskFactory.class, sendEdgeIndicesAndNodeFeatureProps);
        //GCN prediction
        predictNodeGCNTaskFactory predictNodeGCNTaskFactory = new predictNodeGCNTaskFactory(applicationManager);
        Properties predictNodeGCNProps = new Properties();
        predictNodeGCNProps.setProperty("preferredMenu", "Apps.MyApp");
        predictNodeGCNProps.setProperty("title", "Predict class for GCN");
        registerService(context, predictNodeGCNTaskFactory, org.cytoscape.work.TaskFactory.class, predictNodeGCNProps);

        //SendHeteroData (Training Metapath2Vec)
        SendHeteroDataTaskFactory sendHeteroDataTaskFactory = new SendHeteroDataTaskFactory(applicationManager);
        Properties sendHeteroDataProps = new Properties();
        sendHeteroDataProps.setProperty("preferredMenu", "Apps.MyApp.HeteroGNN");
        sendHeteroDataProps.setProperty("title", "Train Metapath2Vec Model");
        registerService(context, sendHeteroDataTaskFactory, org.cytoscape.work.TaskFactory.class, sendHeteroDataProps);

        //Clustering
        ClusterNodesTaskFactory clusterNodesTaskFactory = new ClusterNodesTaskFactory(applicationManager);
        Properties clusterNodesProps = new Properties();
        clusterNodesProps.setProperty("preferredMenu", "Apps.MyApp.HeteroGNN");
        clusterNodesProps.setProperty("title", "Cluster Nodes (after Training)");
        registerService(context, clusterNodesTaskFactory, org.cytoscape.work.TaskFactory.class, clusterNodesProps);

        //Link Prediction
        PredictLinksTaskFactory predictLinksTaskFactory = new PredictLinksTaskFactory(applicationManager);
        Properties predictLinksProps = new Properties();
        predictLinksProps.setProperty("preferredMenu", "Apps.MyApp.HeteroGNN");
        predictLinksProps.setProperty("title", "Predict Link Score (Select 2 Nodes)");
        registerService(context, predictLinksTaskFactory, org.cytoscape.work.TaskFactory.class, predictLinksProps);

        // --- Đăng ký TaskFactory để hiển thị Panel ---
        ShowPanelTaskFactory showPanelFactory = new ShowPanelTaskFactory(
            context, 
            cySwingApplication, 
            taskManager, 
            sendHeteroDataTaskFactory,
            cyNetworkManager
        );
        Properties showPanelProps = new Properties();
        showPanelProps.setProperty("preferredMenu", "Apps.MyApp");
        showPanelProps.setProperty("title", "Main Function");
        registerService(context, showPanelFactory, TaskFactory.class, showPanelProps);

        // --- Đăng ký TaskFactory để ẩn Panel ---
        HidePanelTaskFactory hidePanelFactory = new HidePanelTaskFactory(context);
        Properties hidePanelProps = new Properties();
        hidePanelProps.setProperty("preferredMenu", "Apps.MyApp");
        hidePanelProps.setProperty("title", "Exit App");
        registerService(context, hidePanelFactory, TaskFactory.class, hidePanelProps);
    }
}
