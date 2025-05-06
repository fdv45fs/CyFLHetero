package your.org.myapp.internal;

import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;
import org.osgi.framework.BundleContext;
import org.cytoscape.application.swing.CySwingApplication;

public class ShowPanelTaskFactory extends AbstractTaskFactory {

    private final BundleContext context;
    private final CySwingApplication cySwingApplication;

    public ShowPanelTaskFactory(BundleContext context, CySwingApplication cySwingApplication) {
        this.context = context;
        this.cySwingApplication = cySwingApplication;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new ShowPanelTask(context, cySwingApplication));
    }

    @Override
    public boolean isReady() {
        // Luôn sẵn sàng để mở panel (trừ khi nó đã mở, nhưng task sẽ xử lý việc đó)
        return true;
    }
} 