package demo;

import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Hase {

    public static void main(String[] args) throws IOException {

        String extractionPath = "/app/classes/";
        List<Path> sourceFiles = new ArrayList<>();
        sourceFiles.add(Paths.get("/Users/schmitch/projects/schmitch/scala/sbt-jib/src/sbt-test/simple/target/scala-2.12/classes/"));
        sourceFiles.add(Paths.get("/Users/schmitch/projects/schmitch/scala/sbt-jib/src/sbt-test/simple/module/target/scala-2.12/classes/"));
        for (Path sourceFile : sourceFiles) {
            if (Files.isDirectory(sourceFile)) {
                new DirectoryWalker(sourceFile)
                        .filterRoot()
                        .walk(
                                path -> {
                                    /*
                                     * Builds the same file path as in the source file for extraction. The iteration
                                     * is necessary because the path needs to be in Unix-style.
                                     */
                                    StringBuilder subExtractionPath = new StringBuilder(extractionPath);
                                    Path sourceFileRelativePath = sourceFile.getParent().relativize(path);
                                    System.out.println("Parent:" + sourceFile.getParent() + "||" + path + sourceFileRelativePath);
                                    for (Path sourceFileRelativePathComponent : sourceFileRelativePath) {
                                        subExtractionPath.append('/').append(sourceFileRelativePathComponent);
                                    }
                                    System.out.println("Path: " + path.toFile().toString() + ":" + subExtractionPath.toString());

                                });
            }
        }
    }

}
