import com.lumina.engine.runtime.ui.*;
public class DumpRegistry { public static void main(String[] a){ System.out.println(new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(UIWidgetRegistry.describe())); } }
