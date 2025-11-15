package your.org.myapp.internal;

/**
 * Singleton to store the currently selected model (MetaPath2Vec or Node2Vec)
 * This allows tasks to know which server to call
 */
public class ModelState {
    private static String currentModel = "MetaPath2Vec"; // Default
    
    public static String getCurrentModel() {
        return currentModel;
    }
    
    public static void setCurrentModel(String model) {
        currentModel = model;
        System.out.println("[ModelState] Current model set to: " + model);
    }
}

