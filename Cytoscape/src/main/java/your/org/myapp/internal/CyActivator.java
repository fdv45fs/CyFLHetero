// File: CyActivator.java
package your.org.myapp.internal;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.service.util.AbstractCyActivator;
import org.osgi.framework.BundleContext;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.work.TaskManager;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.event.CyEventHelper;

import java.util.Properties;

public class CyActivator extends AbstractCyActivator {
    @Override
    public void start(BundleContext context) throws Exception {
        CyApplicationManager applicationManager = getService(context, CyApplicationManager.class);
        CySwingApplication cySwingApplication = getService(context, CySwingApplication.class);
        TaskManager<?, ?> taskManager = getService(context, TaskManager.class);
        CyNetworkManager cyNetworkManager = getService(context, CyNetworkManager.class);
        org.cytoscape.view.model.CyNetworkViewManager networkViewManager = 
            getService(context, org.cytoscape.view.model.CyNetworkViewManager.class);
        CyEventHelper eventHelper = getService(context, CyEventHelper.class);

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

        // Node2Vec training factory (no longer in menu, used by panel)
        SendEdgeIndicesTaskFactory sendEdgeIndexTaskFactory = new SendEdgeIndicesTaskFactory(applicationManager);

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

        //GAT training (for node clustering via panel)
        SendEdgeIndicesAndNodeFeatureGATTaskFactory sendEdgeIndicesAndNodeFeatureGATTaskFactory = new SendEdgeIndicesAndNodeFeatureGATTaskFactory(applicationManager);
        Properties sendEdgeIndicesAndNodeFeatureGATProps = new Properties();
        sendEdgeIndicesAndNodeFeatureGATProps.setProperty("preferredMenu", "Apps.MyApp");
        sendEdgeIndicesAndNodeFeatureGATProps.setProperty("title", "Train on GAT");
        registerService(context, sendEdgeIndicesAndNodeFeatureGATTaskFactory, org.cytoscape.work.TaskFactory.class, sendEdgeIndicesAndNodeFeatureGATProps);
        // Note: GAT is used for node clustering (not classification), use Panel -> Task: Node clustering

        //DGI training (Deep Graph Infomax - unsupervised for node clustering)
        SendEdgeIndicesDGITaskFactory sendEdgeIndicesDGITaskFactory = new SendEdgeIndicesDGITaskFactory(applicationManager);
        Properties sendEdgeIndicesDGIProps = new Properties();
        sendEdgeIndicesDGIProps.setProperty("preferredMenu", "Apps.MyApp");
        sendEdgeIndicesDGIProps.setProperty("title", "Train on DGI");
        registerService(context, sendEdgeIndicesDGITaskFactory, org.cytoscape.work.TaskFactory.class, sendEdgeIndicesDGIProps);
        // Note: DGI does NOT need node features - auto-generates from graph structure

        //HGAT training (Heterogeneous GAT - for Drug-Gene networks)
        SendHeteroDataHGATTaskFactory sendHeteroDataHGATTaskFactory = new SendHeteroDataHGATTaskFactory(applicationManager);
        Properties sendHeteroDataHGATProps = new Properties();
        sendHeteroDataHGATProps.setProperty("preferredMenu", "Apps.MyApp");
        sendHeteroDataHGATProps.setProperty("title", "Train on HGAT");
        registerService(context, sendHeteroDataHGATTaskFactory, org.cytoscape.work.TaskFactory.class, sendHeteroDataHGATProps);
        // Note: HGAT is for heterogeneous graphs (e.g., Drug-Gene), supports clustering + link prediction

        //GTN training (Graph Transformer Network - for Drug-Gene networks with auto meta-path learning)
        SendHeteroDataGTNTaskFactory sendHeteroDataGTNTaskFactory = new SendHeteroDataGTNTaskFactory(applicationManager);
        Properties sendHeteroDataGTNProps = new Properties();
        sendHeteroDataGTNProps.setProperty("preferredMenu", "Apps.MyApp");
        sendHeteroDataGTNProps.setProperty("title", "Train on GTN");
        registerService(context, sendHeteroDataGTNTaskFactory, org.cytoscape.work.TaskFactory.class, sendHeteroDataGTNProps);
        // Note: GTN automatically learns meta-paths, supports clustering + link prediction

        //SendHeteroData (Training Metapath2Vec)
        SendHeteroDataTaskFactory sendHeteroDataTaskFactory = new SendHeteroDataTaskFactory(applicationManager);
        Properties sendHeteroDataProps = new Properties();
        sendHeteroDataProps.setProperty("preferredMenu", "Apps.MyApp.HeteroGNN");
        sendHeteroDataProps.setProperty("title", "Train Metapath2Vec Model");
        registerService(context, sendHeteroDataTaskFactory, org.cytoscape.work.TaskFactory.class, sendHeteroDataProps);

        //Clustering - Cần instance này
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

        //Predict All Links
        PredictAllLinksTaskFactory predictAllLinksTaskFactory = new PredictAllLinksTaskFactory(applicationManager);
        Properties predictAllLinksProps = new Properties();
        predictAllLinksProps.setProperty("preferredMenu", "Apps.MyApp.HeteroGNN");
        predictAllLinksProps.setProperty("title", "Predict All Links (Top 10)");
        registerService(context, predictAllLinksTaskFactory, org.cytoscape.work.TaskFactory.class, predictAllLinksProps);

        //SVM Classifier for Node Classification (Node2Vec + Labels)
        TrainSVMClassifierTaskFactory trainSVMClassifierTaskFactory = new TrainSVMClassifierTaskFactory(applicationManager);
        Properties trainSVMProps = new Properties();
        trainSVMProps.setProperty("preferredMenu", "Apps.MyApp");
        trainSVMProps.setProperty("title", "Train SVM Classifier (Node2Vec + Labels)");
        registerService(context, trainSVMClassifierTaskFactory, org.cytoscape.work.TaskFactory.class, trainSVMProps);
        // Note: Trains SVM on Node2Vec embeddings using disease labels from node table

        // --- Đăng ký TaskFactory để hiển thị Panel ---
        ShowPanelTaskFactory showPanelFactory = new ShowPanelTaskFactory(
            context, 
            cySwingApplication, 
            taskManager, 
            sendHeteroDataTaskFactory,
            sendEdgeIndexTaskFactory,
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
            networkViewManager,
            eventHelper
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
