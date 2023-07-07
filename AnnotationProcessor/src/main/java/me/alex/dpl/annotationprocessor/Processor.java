package me.alex.dpl.annotationprocessor;

import me.alex.dpl.Constants;
import me.alex.dpl.annotation.AutoLoadable;
import me.alex.dpl.annotation.DependencyConstructor;
import me.alex.dpl.annotation.Inject;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.*;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SupportedAnnotationTypes("me.alex.dpl.annotation.AutoLoadable")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class Processor extends AbstractProcessor {
    private final ArrayList<String> foundAnnotations = new ArrayList<>();
    private final Class<? extends Annotation> annotationClass = AutoLoadable.class;
    private final Class<? extends Annotation> dependencyConstrutor = DependencyConstructor.class;
    private final Class<? extends Annotation> injectClass = Inject.class;
    private final Map<String, List<String>> dependencies = new ConcurrentHashMap<>();
    private Messager messager;
    private Filer filer;
    private DependencyHandler dependencyHandler;
    private Types typeUtils;

    private static void readOldIndexFile(Set<String> entries, Reader reader) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            String line = bufferedReader.readLine();
            while (line != null) {
                entries.add(line);
                line = bufferedReader.readLine();
            }
        }
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
        dependencyHandler = new DependencyHandler();
        typeUtils = processingEnv.getTypeUtils();
        messager.printMessage(Diagnostic.Kind.NOTE, "Starting annotation processing...");

    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return false;
        }

        roundEnv.getElementsAnnotatedWith(annotationClass).forEach(element -> {
            if (element.getKind() == ElementKind.CLASS) {
                if (element instanceof TypeElement typeElement) {
                    String className = typeElement.getQualifiedName().toString();
                    if (!dependencies.containsKey(className)) {
                        dependencies.put(className, new ArrayList<>());
                    }
                }
            }
        });

        roundEnv.getElementsAnnotatedWith(dependencyConstrutor).forEach(element -> {
            if (element.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement constructor = (ExecutableElement) element;
                TypeElement enclosingElement = (TypeElement) constructor.getEnclosingElement();
                String className = enclosingElement.getQualifiedName().toString();

                dependencies.computeIfPresent(className, (key, value) -> {
                    for (VariableElement parameter : constructor.getParameters()) {
                        TypeElement typeElement = (TypeElement) typeUtils.asElement(parameter.asType());
                        if (typeElement != null) {
                            value.add(typeElement.getQualifiedName().toString());
                        }
                    }
                    return value;
                });
            }
        });

        roundEnv.getElementsAnnotatedWith(injectClass).forEach(element -> {
            if (element.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) element;
                TypeElement enclosingElement = (TypeElement) field.getEnclosingElement();
                String className = enclosingElement.getQualifiedName().toString();


                dependencies.computeIfPresent(className, (key, value) -> {
                    TypeElement typeElement = (TypeElement) typeUtils.asElement(field.asType());
                    if (typeElement != null) {
                        value.add(typeElement.getQualifiedName().toString());
                    }
                    return value;
                });
            }
        });


        List<DependencyHandler.Dependency> classes = new ArrayList<>();

        for (Map.Entry<String, List<String>> dep : dependencies.entrySet()) {
            String className = dep.getKey();
            List<String> dependencies = dep.getValue();
            classes.add(new DependencyHandler.Dependency(className, dependencies));
        }

        classes = dependencyHandler.sortDependencies(classes);

        Set<String> sortedClasses = new LinkedHashSet<>();
        for (DependencyHandler.Dependency dep : classes) {
            sortedClasses.add(dep.getKlass());

            messager.printMessage(Diagnostic.Kind.NOTE, "Found " + dep.getKlass() + " with dependencies " + Arrays.toString(dep.getDependencies().toArray()));
        }
        messager.printMessage(Diagnostic.Kind.NOTE, "Found " + Arrays.toString(sortedClasses.toArray()));

        try {
            writeSimpleNameIndexFile(sortedClasses, Constants.ANNOTATION_STORAGE_FILE);
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Failed to write annotation storage file: " + e.getMessage());
        }

        return false;
    }

    private void writeSimpleNameIndexFile(Set<String> elementList, String resourceName)
            throws IOException {
        FileObject file = readOldIndexFile(elementList, resourceName);
        if (file != null) {
            /*
             * Ugly hack for Eclipse JDT incremental compilation.
             * Eclipse JDT can't createResource() after successful getResource().
             * But we can file.openWriter().
             */
            try {
                writeIndexFile(elementList, resourceName, file);
                return;
            } catch (IllegalStateException e) {
                // Thrown by HotSpot Java Compiler
            }
        }
        writeIndexFile(elementList, resourceName, null);
    }

    private FileObject readOldIndexFile(Set<String> entries, String resourceName) throws IOException {
        Reader reader = null;
        try {
            final FileObject resource = filer.getResource(StandardLocation.CLASS_OUTPUT, "", resourceName);
            reader = resource.openReader(true);
            readOldIndexFile(entries, reader);
            return resource;
        } catch (FileNotFoundException e) {
            /*
             * Ugly hack for Intellij IDEA incremental compilation.
             * The problem is that it throws FileNotFoundException on the files, if they were not created during the
             * current session of compilation.
             */
            final String realPath = e.getMessage();
            if (new File(realPath).exists()) {
                try (Reader fileReader = new FileReader(realPath, StandardCharsets.UTF_8)) {
                    readOldIndexFile(entries, fileReader);
                }
            }
        } catch (IOException e) {
            // Thrown by Eclipse JDT when not found
        } catch (UnsupportedOperationException e) {
            // Java6 does not support reading old index files
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return null;
    }

    private void writeIndexFile(Set<String> entries, String resourceName, FileObject overrideFile) throws IOException {
        FileObject file = overrideFile;
        if (file == null) {
            file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", resourceName);
        }
        try (Writer writer = file.openWriter()) {
            for (String entry : entries) {
                writer.write(entry);
                writer.write("\n");
            }
            writer.flush();
            messager.printMessage(Diagnostic.Kind.NOTE, "Wrote annotation storage file: " + file.toUri());
        }
    }
}
