package your.org.myapp.internal;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.work.TaskIterator;

public class SendDataPy4CyTaskFactory implements TaskFactory {

    private final CyApplicationManager applicationManager;

    public SendDataPy4CyTaskFactory(CyApplicationManager applicationManager) {
        this.applicationManager = applicationManager;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new SendDataPy4CyTask(applicationManager));
    }

    @Override
    public boolean isReady() {
        // Luôn sẵn sàng hoặc có thể thêm điều kiện kiểm tra network
        return applicationManager.getCurrentNetwork() != null;
    }
} 