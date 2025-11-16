package your.org.myapp.internal;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;

/**
 * Factory for creating GTN training tasks.
 * 
 * GTN (Graph Transformer Network) is designed for heterogeneous graphs
 * with automatic meta-path learning.
 */
public class SendHeteroDataGTNTaskFactory extends AbstractTaskFactory {
    private final CyApplicationManager applicationManager;

    public SendHeteroDataGTNTaskFactory(CyApplicationManager applicationManager) {
        this.applicationManager = applicationManager;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new SendHeteroDataGTNTask(applicationManager));
    }

    @Override
    public boolean isReady() {
        // Only ready if there's a network loaded
        return applicationManager.getCurrentNetwork() != null;
    }
}

