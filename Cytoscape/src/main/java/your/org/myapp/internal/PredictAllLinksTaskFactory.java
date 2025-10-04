package your.org.myapp.internal;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.work.TaskIterator;

public class PredictAllLinksTaskFactory implements TaskFactory {

    private final CyApplicationManager cyApplicationManager;

    public PredictAllLinksTaskFactory(CyApplicationManager cyApplicationManager) {
        this.cyApplicationManager = cyApplicationManager;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new PredictAllLinksTask(cyApplicationManager));
    }

    @Override
    public boolean isReady() {
        // Ready if a network is currently selected
        return cyApplicationManager.getCurrentNetwork() != null;
    }
}
