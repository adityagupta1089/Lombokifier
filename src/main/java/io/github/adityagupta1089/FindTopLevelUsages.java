package io.github.adityagupta1089;

import com.github.javaparser.symbolsolver.utils.SymbolSolverCollectionStrategy;

import java.io.IOException;
import java.nio.file.Paths;

public class FindTopLevelUsages {
    public static void main(String[] args) throws IOException {
        var root = Paths.get("~/Work/udio-wallet/wallet/src/main/java/in/");
        new SymbolSolverCollectionStrategy().collect(root)
                .getSourceRoots()
                .forEach(System.out::println);
    }
}
