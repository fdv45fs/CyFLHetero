package your.org.myapp.internal;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;

public class SendHeteroDataHGATTaskFactory extends AbstractTaskFactory {
    
    private final CyApplicationManager applicationManager;
    
    public SendHeteroDataHGATTaskFactory(CyApplicationManager applicationManager) {
        this.applicationManager = applicationManager;
    }
    
    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new SendHeteroDataHGATTask(applicationManager));
    }
    
    @Override
    public boolean isReady() {
        return applicationManager.getCurrentNetwork() != null;
    }
}

