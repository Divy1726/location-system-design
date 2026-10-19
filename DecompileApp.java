import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import java.io.File;
import java.io.FileWriter;

public class DecompileApp {
    public static void main(String[] args) throws Exception {
        JadxArgs jadxArgs = new JadxArgs();
        jadxArgs.getInputFiles().add(new File("C:\\Users\\sutha\\Desktop\\rea\\extracted\\love8-xapk\\ltd.love8.couples.relationship.apk"));
        jadxArgs.setOutDir(new File("C:\\Users\\sutha\\Desktop\\rea\\extracted\\decompiled_love8"));
        try (JadxDecompiler jadx = new JadxDecompiler(jadxArgs)) {
            jadx.load();
            int count = 0;
            for (JavaClass cls : jadx.getClasses()) {
                String name = cls.getFullName();
                if (name.startsWith("com.lovemaps.app.")) {
                    File outFile = new File("C:\\Users\\sutha\\Desktop\\rea\\extracted\\decompiled_love8\\" + name.replace('.', '/') + ".java");
                    outFile.getParentFile().mkdirs();
                    try (FileWriter writer = new FileWriter(outFile)) {
                        writer.write(cls.getCode());
                        count++;
                    } catch (Exception e) {}
                }
            }
            System.out.println("Decompiled com.lovemaps.app: " + count + " classes");
        }
    }
}
