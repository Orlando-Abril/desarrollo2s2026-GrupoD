package com.example.demo.architecture;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Source check for a compile-time constant that is inlined and invisible to ArchUnit bytecode rules. */
final class ControllerHeaderSourceCheck {
    private static final String FILTER = "com.example.demo.filter.CorrelationIdFilter";
    private static final String OPENAPI_HEADER = "io.swagger.v3.oas.annotations.headers.Header";

    private ControllerHeaderSourceCheck() {
    }

    static List<String> violations(Path source) {
        try {
            if (Files.isDirectory(source)) {
                try (var files = Files.walk(source)) {
                    return files.filter(path -> path.toString().endsWith(".java"))
                            .sorted()
                            .flatMap(path -> violationsInFile(path).stream())
                            .toList();
                }
            }
            return violationsInFile(source);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static List<String> violationsInFile(Path file) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return List.of("A JDK compiler is required to inspect " + file);
        }
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null)) {
            var units = manager.getJavaFileObjects(file.toFile());
            JavacTask task = (JavacTask) compiler.getTask(null, manager, null,
                    List.of("-proc:none"), null, units);
            List<String> violations = new ArrayList<>();
            for (CompilationUnitTree unit : task.parse()) {
                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitImport(ImportTree node, Void unused) {
                        String imported = node.getQualifiedIdentifier().toString();
                        if (imported.startsWith("com.example.demo.filter.")
                                && (!FILTER.equals(imported) || node.isStatic())) {
                            violations.add(file + ": forbidden filter import " + imported);
                        }
                        return null;
                    }

                    @Override
                    public Void visitMemberSelect(MemberSelectTree node, Void unused) {
                        if (FILTER.equals(node.toString())) {
                            violations.add(file + ": fully qualified CorrelationIdFilter reference");
                        } else if ("CorrelationIdFilter".equals(node.getExpression().toString())
                                && (!"HEADER".contentEquals(node.getIdentifier())
                                || !isHeaderNameValue(getCurrentPath()))) {
                            violations.add(file + ": CorrelationIdFilter may only supply @Header(name=...): "
                                    + node);
                        }
                        return super.visitMemberSelect(node, unused);
                    }

                    @Override
                    public Void visitIdentifier(IdentifierTree node, Void unused) {
                        if ("CorrelationIdFilter".contentEquals(node.getName())) {
                            TreePath parentPath = getCurrentPath().getParentPath();
                            if (parentPath == null || !(parentPath.getLeaf() instanceof MemberSelectTree parent)
                                    || parent.getExpression() != node) {
                                violations.add(file + ": unexpected CorrelationIdFilter identifier");
                            }
                        }
                        return super.visitIdentifier(node, unused);
                    }
                }.scan(unit, null);
            }
            return violations;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static boolean isHeaderNameValue(TreePath path) {
        TreePath assignmentPath = path.getParentPath();
        if (assignmentPath == null || !(assignmentPath.getLeaf() instanceof AssignmentTree assignment)
                || assignment.getExpression() != path.getLeaf()
                || !"name".equals(assignment.getVariable().toString())) {
            return false;
        }
        TreePath annotationPath = assignmentPath.getParentPath();
        if (annotationPath == null || !(annotationPath.getLeaf() instanceof AnnotationTree annotation)) {
            return false;
        }
        String type = annotation.getAnnotationType().toString();
        if (OPENAPI_HEADER.equals(type)) {
            return true;
        }
        return "Header".equals(type) && path.getCompilationUnit().getImports().stream()
                .map(importTree -> importTree.getQualifiedIdentifier().toString())
                .anyMatch(OPENAPI_HEADER::equals);
    }
}
