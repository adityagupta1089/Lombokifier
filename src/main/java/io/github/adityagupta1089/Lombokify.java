package io.github.adityagupta1089;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithMembers;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class Lombokify {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Please provide directory path as argument");
        } else {
            Files.walk(Paths.get(args[0])).parallel().filter(Files::isRegularFile).forEach(Lombokify::process);
        }
    }

    private static void process(Path file) {
        System.out.printf(">>> %s\n", file);
        try {
            var ast = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(ast);
            new Lombokifier().visit(ast, null);
            Files.writeString(file, LexicalPreservingPrinter.print(ast));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class Metadata {
        Set<String> annotations;
        Set<String> imports;
        CompilationUnit cu;
        NodeWithMembers<?> top;

        public Metadata() {
            annotations = new HashSet<>();
            imports = new HashSet<>();
        }
    }

    private static class Lombokifier extends ModifierVisitor<Metadata> {
        @Override
        public Visitable visit(CompilationUnit n, Metadata arg) {
            Metadata metadata = new Metadata();
            metadata.cu = n;
            return super.visit(n, metadata);
        }

        @Override
        public Visitable visit(ClassOrInterfaceDeclaration n, Metadata arg) {
            return visitClassOrInterfaceOrEnumDeclaration(n, arg);
        }

        @Override
        public Visitable visit(EnumDeclaration n, Metadata arg) {
            return this.visitClassOrInterfaceOrEnumDeclaration(n, arg);
        }

        private <N extends NodeWithMembers<?> & NodeWithAnnotations<?>> N visitClassOrInterfaceOrEnumDeclaration(N n, Metadata arg) {
            Metadata metadata = new Metadata();
            metadata.cu = arg.cu;
            metadata.top = n;
            if (n instanceof ClassOrInterfaceDeclaration clazz) {
                super.visit(clazz, metadata);
            } else if (n instanceof EnumDeclaration en) {
                super.visit(en, metadata);
            }
            for (var annotation : metadata.annotations) {
                n.addMarkerAnnotation(annotation);
            }
            for (var imp : metadata.imports) {
                arg.cu.addImport(imp);
            }
            return n;
        }

        @Override
        public Visitable visit(ConstructorDeclaration n, Metadata metadata) {
            int parameters = n.asConstructorDeclaration().getParameters().size();
            int totalParameters = metadata.top.getFields().size();
            if (parameters == 0) {
                metadata.annotations.add("NoArgsConstructor");
                metadata.imports.add("lombok.NoArgsConstructor");
            } else if (parameters == totalParameters) {
                metadata.annotations.add("AllArgsConstructor");
                metadata.imports.add("lombok.AllArgsConstructor");
            } else {
                return n;
            }
            return null;
        }

        @Override
        public Visitable visit(MethodDeclaration m, Metadata metadata) {
            if (isGetter(m)) {
                metadata.annotations.add("Getter");
                metadata.imports.add("lombok.Getter");
            } else if (isSetter(m)) {
                metadata.annotations.add("Setter");
                metadata.imports.add("lombok.Setter");
            } else if (isToString(m)) {
                metadata.annotations.add("ToString");
                metadata.imports.add("lombok.ToString");
            } else {
                return m;
            }
            return null;
        }

        private boolean isToString(MethodDeclaration m) {
            return m.getNameAsString().equals("toString")
                   && m.getAnnotations().size() == 1
                   && m.getAnnotation(0).getNameAsString().equals("Override");
        }

        private boolean isSetter(MethodDeclaration m) {
            if (m.getNameAsString().startsWith("set")) {
                var body = m.getBody();
                if (body.isPresent()) {
                    var statements = body.get().getStatements();
                    if (statements.size() == 1) {
                        var statement = statements.get(0);
                        return statement.isExpressionStmt()
                               && statement.asExpressionStmt().getExpression().isAssignExpr();
                    }
                }
            }
            return false;
        }

        private boolean isGetter(MethodDeclaration m) {
            if (m.getNameAsString().startsWith("get") || m.getNameAsString().startsWith("is")) {
                var body = m.getBody();
                if (body.isPresent()) {
                    var statements = body.get().getStatements();
                    if (statements.size() == 1) {
                        var statement = statements.get(0);
                        if (statement.isReturnStmt()) {
                            var expression = statement.asReturnStmt().getExpression();
                            return statement.isReturnStmt()
                                   && expression.isPresent()
                                   && (expression.get().isNameExpr() || expression.get().isFieldAccessExpr());
                        }
                    }
                }
            }
            return false;
        }

    }

}