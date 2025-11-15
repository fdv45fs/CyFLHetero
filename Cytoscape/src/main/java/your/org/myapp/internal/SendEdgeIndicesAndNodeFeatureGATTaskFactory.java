package your.org.myapp.internal;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;

public class SendEdgeIndicesAndNodeFeatureGATTaskFactory extends AbstractTaskFactory {
    private final CyApplicationManager applicationManager;

    public SendEdgeIndicesAndNodeFeatureGATTaskFactory(CyApplicationManager applicationManager) {
        this.applicationManager = applicationManager;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new SendEdgeIndicesAndNodeFeatureGATTask(applicationManager));
    }
}

