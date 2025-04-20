package your.org.myapp.internal;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.work.TaskIterator;

public class PredictLinksTaskFactory implements TaskFactory {

    private final CyApplicationManager cyApplicationManager;

    public PredictLinksTaskFactory(CyApplicationManager cyApplicationManager) {
        this.cyApplicationManager = cyApplicationManager;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new PredictLinksTask(cyApplicationManager));
    }

    @Override
    public boolean isReady() {
        // Sẵn sàng nếu có một network view đang được chọn
        return cyApplicationManager.getCurrentNetworkView() != null;
    }
} 