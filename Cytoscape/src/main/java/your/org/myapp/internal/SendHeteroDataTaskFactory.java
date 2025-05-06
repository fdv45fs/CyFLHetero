package your.org.myapp.internal;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;

public class SendHeteroDataTaskFactory extends AbstractTaskFactory {
    private final CyApplicationManager applicationManager;

    public SendHeteroDataTaskFactory(CyApplicationManager applicationManager) {
        this.applicationManager = applicationManager;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new SendHeteroDataTask(applicationManager));
    }
}
