package your.org.myapp.internal;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;

public class ClusterNodesTaskFactory extends AbstractTaskFactory {
    private final CyApplicationManager applicationManager;

    public ClusterNodesTaskFactory(CyApplicationManager applicationManager) {
        this.applicationManager = applicationManager;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new ClusterNodesTask(applicationManager));
    }
}
