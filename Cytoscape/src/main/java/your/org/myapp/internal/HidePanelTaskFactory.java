package your.org.myapp.internal;

import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;
import org.osgi.framework.BundleContext;

public class HidePanelTaskFactory extends AbstractTaskFactory {

    private final BundleContext context;

    public HidePanelTaskFactory(BundleContext context) {
        this.context = context;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new HidePanelTask(context));
    }

    @Override
    public boolean isReady() {
        // Chỉ nên sẵn sàng nếu panel đang mở?
        // Tạm thời để luôn sẵn sàng, task sẽ kiểm tra
        return true;
    }
} 