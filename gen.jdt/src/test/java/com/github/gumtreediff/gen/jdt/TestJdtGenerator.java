/*
 * This file is part of GumTree.
 *
 * GumTree is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GumTree is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GumTree.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2011-2015 Jean-Rémy Falleri <jr.falleri@gmail.com>
 * Copyright 2011-2015 Floréal Morandat <florealm@gmail.com>
 */

package com.github.gumtreediff.gen.jdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.junit.jupiter.api.Test;

import com.github.gumtreediff.actions.ChawatheScriptGenerator;
import com.github.gumtreediff.actions.EditScript;
import com.github.gumtreediff.actions.SimplifiedChawatheScriptGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.gen.SyntaxException;
import com.github.gumtreediff.matchers.CompositeMatchers;
import com.github.gumtreediff.matchers.ConfigurableMatcher;
import com.github.gumtreediff.matchers.ConfigurationOptions;
import com.github.gumtreediff.matchers.GumTreeProperties;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.heuristic.gt.GreedySubtreeMatcher;
import com.github.gumtreediff.tree.Tree;
import com.github.gumtreediff.tree.TreeContext;
import com.github.gumtreediff.tree.Type;

public class TestJdtGenerator {
    private static final Type COMPILATION_UNIT = AbstractJdtVisitor.nodeAsSymbol(ASTNode.COMPILATION_UNIT);

    @Test
    public void testSimpleSyntax() throws IOException {
        String input = "public class Foo { public int foo; }";
        Tree tree = new JdtTreeGenerator().generateFrom().string(input).getRoot();
        assertEquals(COMPILATION_UNIT, tree.getType());
        assertEquals(10, tree.getMetrics().size);
    }

    @Test
    public void testJava5Syntax() throws IOException {
        String input = "public class Foo<A> { public List<A> foo; public void foo() "
                + "{ for (A f : foo) { System.out.println(f); } } }";
        Tree tree = new JdtTreeGenerator().generateFrom().string(input).getRoot();
        assertEquals(COMPILATION_UNIT, tree.getType());
        assertEquals(35, tree.getMetrics().size);
    }

    @Test
    public void testMethodInvocation() throws IOException {
        String leftInput = "class Main {\n" + "    public static void foo() {\n" + "        a(b);\n" + "    }\n"
                + "}\n";
        TreeContext leftCtx = new JdtTreeGenerator().generateFrom().string(leftInput);
        String rightInput = "class Main {\n" + "    public static void foo() {\n" + "        a.b();\n" + "    }\n"
                + "}";
        TreeContext rightCtx = new JdtTreeGenerator().generateFrom().string(rightInput);
        assertFalse(rightCtx.getRoot().isIsomorphicTo(leftCtx.getRoot()));
    }

    @Test
    public void testVarargs() throws IOException {
        String leftInput = "class Main {\n" + "    public foo(String a) {}\n" + "}\n";
        TreeContext leftCtx = new JdtTreeGenerator().generateFrom().string(leftInput);
        String rightInput = "class Main {\n" + "    public foo(String... a) {}\n" + "}\n";
        TreeContext rightCtx = new JdtTreeGenerator().generateFrom().string(rightInput);
        assertFalse(rightCtx.getRoot().isIsomorphicTo(leftCtx.getRoot()));
    }

    @Test
    public void testJava8Syntax() throws IOException {
        String input = "public class Foo { public void foo(){ new ArrayList<Object>().stream().forEach(a -> {}); } }";
        Tree tree = new JdtTreeGenerator().generateFrom().string(input).getRoot();
        assertEquals(COMPILATION_UNIT, tree.getType());
        assertEquals(28, tree.getMetrics().size);
    }

    @Test
    public void badSyntax() throws IOException {
        String input = "public clas Foo {}";
        assertThrows(SyntaxException.class, () -> {
            new JdtTreeGenerator().generateFrom().string(input);
        });
    }

    @Test
    public void testTypeDefinition() throws IOException {
        String leftInput = "public class Foo {}";
        String rightInput = "public interface Foo {}";
        TreeContext leftContext = new JdtTreeGenerator().generateFrom().string(leftInput);
        TreeContext rightContext = new JdtTreeGenerator().generateFrom().string(rightInput);
        assertFalse(leftContext.getRoot().isIsomorphicTo(rightContext.getRoot()));
    }

    @Test
    public void testInfixOperator() throws IOException {
        String leftInput = "class Foo { int i = 3 + 3; }";
        String rightInput = "class Foo { int i = 3 - 3; }";
        TreeContext leftContext = new JdtTreeGenerator().generateFrom().string(leftInput);
        TreeContext rightContext = new JdtTreeGenerator().generateFrom().string(rightInput);
        assertFalse(leftContext.getRoot().isIsomorphicTo(rightContext.getRoot()));
    }

    @Test
    public void testAssignment() throws IOException {
        String leftInput = "class Foo { void foo() { int i = 12; } }";
        String rightInput = "class Foo { void foo() { int i += 12; } }";
        TreeContext leftContext = new JdtTreeGenerator().generateFrom().string(leftInput);
        TreeContext rightContext = new JdtTreeGenerator().generateFrom().string(rightInput);
        assertFalse(leftContext.getRoot().isIsomorphicTo(rightContext.getRoot()));
    }

    @Test
    public void testPrefixExpression() throws IOException {
        String leftInput = "class Foo { void foo() { ++i; } }";
        String rightInput = "class Foo { void foo() { --i; } }";
        TreeContext leftContext = new JdtTreeGenerator().generateFrom().string(leftInput);
        TreeContext rightContext = new JdtTreeGenerator().generateFrom().string(rightInput);
        assertFalse(leftContext.getRoot().isIsomorphicTo(rightContext.getRoot()));
    }

    @Test
    public void testPostfixExpression() throws IOException {
        String leftInput = "class Foo { void foo() { i++; } }";
        String rightInput = "class Foo { void foo() { i--; } }";
        TreeContext leftContext = new JdtTreeGenerator().generateFrom().string(leftInput);
        TreeContext rightContext = new JdtTreeGenerator().generateFrom().string(rightInput);
        assertFalse(leftContext.getRoot().isIsomorphicTo(rightContext.getRoot()));

        CompositeMatchers.ClassicGumtree matcher = new CompositeMatchers.ClassicGumtree();
        ChawatheScriptGenerator edGenerator = new ChawatheScriptGenerator();

        MappingStore mappings = matcher.match(leftContext.getRoot(), rightContext.getRoot());

        EditScript actions = edGenerator.computeActions(mappings);

        List<Action> actionsAll = actions.asList();

        assertEquals(1, actionsAll.size());
    }

    @Test
    public void testArrayCreation() throws IOException {
        String leftInput = "class Foo { int[][] tab = new int[12][]; }";
        TreeContext leftContext = new JdtTreeGenerator().generateFrom().string(leftInput);
        String rightInput = "class Foo { int[][] tab = new int[12][12]; }";
        TreeContext rightContext = new JdtTreeGenerator().generateFrom().string(rightInput);
        assertFalse(leftContext.getRoot().isIsomorphicTo(rightContext.getRoot()));
    }

    @Test
    public void testIds() throws IOException {
        String input = "class Foo { String a; void foo(int a, String b) {}; void bar() { } }";
        TreeContext ct = new JdtTreeGenerator().generateFrom().string(input);
        assertEquals(ct.getRoot().getChild(0).getMetadata("id"), "Type Foo");
        assertEquals(ct.getRoot().getChild("0.2").getMetadata("id"), "Field a");
        assertEquals(ct.getRoot().getChild("0.3").getMetadata("id"), "Method foo( int String)");
        assertEquals(ct.getRoot().getChild("0.4").getMetadata("id"), "Method bar()");
    }

    @Test
    public void testChangeConstantFromFile() throws IOException {

        // URL resource =
        // getClass().getClassLoader().getResource("java_simple_case_1/T1_s.java");

        TreeContext leftContext = new JdtTreeGenerator().generateFrom()
                .file("/Users/matias/develop/gt-tuning/git-code-gpgt/examples/java_simple_case_1/T1_s.java");
        TreeContext rightContext = new JdtTreeGenerator().generateFrom()
                .file("/Users/matias/develop/gt-tuning/git-code-gpgt/examples/java_simple_case_1/T1_t.java");
        assertFalse(leftContext.getRoot().isIsomorphicTo(rightContext.getRoot()));

        CompositeMatchers.ClassicGumtree matcher = new CompositeMatchers.ClassicGumtree();
        ChawatheScriptGenerator edGenerator = new ChawatheScriptGenerator();

        MappingStore mappings = matcher.match(leftContext.getRoot(), rightContext.getRoot());

        EditScript actions = edGenerator.computeActions(mappings);

        List<Action> actionsAll = actions.asList();

        assertEquals(1, actionsAll.size());
    }

    @Test
    public void testNotSpurious1() throws IOException {

        URL resourceSource = getClass().getClassLoader().getResource("case_1_without_spurious/ClassA_s.javaa");
        URL resourceTarget = getClass().getClassLoader().getResource("case_1_without_spurious/ClassA_t.javaa");

        TreeContext leftContext = new JdtTreeGenerator().generateFrom().file(resourceSource.getFile());
        TreeContext rightContext = new JdtTreeGenerator().generateFrom().file(resourceTarget.getFile());
        assertFalse(leftContext.getRoot().isIsomorphicTo(rightContext.getRoot()));

        CompositeMatchers.ClassicGumtree matcher = new CompositeMatchers.ClassicGumtree();
        ChawatheScriptGenerator edGenerator = new ChawatheScriptGenerator();

        MappingStore mappings = matcher.match(leftContext.getRoot(), rightContext.getRoot());

        EditScript actions = edGenerator.computeActions(mappings);

        List<Action> actionsAll = actions.asList();

        assertEquals(1, actionsAll.size());
        assertEquals("update-node", actionsAll.get(0).getName());

    }

    @Test
    public void testSpurious1WithSimple() throws IOException {

        URL resourceSource = getClass().getClassLoader().getResource("case_1_with_spurious/ClassA_s.javaa");
        URL resourceTarget = getClass().getClassLoader().getResource("case_1_with_spurious/ClassA_t.javaa");

        TreeContext leftContext = new JdtTreeGenerator().generateFrom().file(resourceSource.getFile());
        TreeContext rightContext = new JdtTreeGenerator().generateFrom().file(resourceTarget.getFile());
        assertFalse(leftContext.getRoot().isIsomorphicTo(rightContext.getRoot()));

        Matcher matcher = new CompositeMatchers.SimpleGumtree();
        SimplifiedChawatheScriptGenerator edGenerator = new SimplifiedChawatheScriptGenerator();

        MappingStore mappings = matcher.match(leftContext.getRoot(), rightContext.getRoot());

        EditScript actions = edGenerator.computeActions(mappings);

        List<Action> actionsAll = actions.asList();

        assertEquals(1, actionsAll.size());

        assertEquals("update-node", actionsAll.get(0).getName());

    }

    @Test
    public void testSpurious1WithClassicDefault() throws IOException {

        URL resourceSource = getClass().getClassLoader().getResource("case_1_with_spurious/ClassA_s.javaa");
        URL resourceTarget = getClass().getClassLoader().getResource("case_1_with_spurious/ClassA_t.javaa");

        TreeContext leftContext = new JdtTreeGenerator().generateFrom().file(resourceSource.getFile());
        TreeContext rightContext = new JdtTreeGenerator().generateFrom().file(resourceTarget.getFile());
        Tree rootLeft = leftContext.getRoot();
        assertFalse(rootLeft.isIsomorphicTo(rightContext.getRoot()));

        Matcher matcher = new CompositeMatchers.ClassicGumtree();
        SimplifiedChawatheScriptGenerator edGenerator = new SimplifiedChawatheScriptGenerator();

        MappingStore mappings = matcher.match(rootLeft, rightContext.getRoot());

        EditScript actions = edGenerator.computeActions(mappings);

        List<Action> actionsAll = actions.asList();

        // Check size of imports
        assertEquals(2, rootLeft.getChild(1).getMetrics().size);
        assertEquals(2, rootLeft.getChild(2).getMetrics().size);

        // THis is the assertion that fails: it should be one
        assertEquals(1, actionsAll.size());

        assertEquals("update-node", actionsAll.get(0).getName());

        assertEquals(4, rootLeft.getChildren().size());

        assertTrue(mappings.isSrcMapped(rootLeft));
        // TypeDecl
        assertTrue(mappings.isSrcMapped(rootLeft.getChild(3)));
        // ImportDecl
        assertTrue(mappings.isSrcMapped(rootLeft.getChild(2)));
        // ImportDecl
        assertTrue(mappings.isSrcMapped(rootLeft.getChild(1)));
        // PackageDecl
        assertTrue(mappings.isSrcMapped(rootLeft.getChild(0)));

    }

    @Test
    public void testSpurious1WithClassicConfiguredGreedyBottomUpMatcher() throws IOException {

        URL resourceSource = getClass().getClassLoader().getResource("case_1_with_spurious/ClassA_s.javaa");
        URL resourceTarget = getClass().getClassLoader().getResource("case_1_with_spurious/ClassA_t.javaa");

        TreeContext leftContext = new JdtTreeGenerator().generateFrom().file(resourceSource.getFile());
        TreeContext rightContext = new JdtTreeGenerator().generateFrom().file(resourceTarget.getFile());
        assertFalse(leftContext.getRoot().isIsomorphicTo(rightContext.getRoot()));

        Matcher matcher = new CompositeMatchers.ClassicGumtree();
        SimplifiedChawatheScriptGenerator edGenerator = new SimplifiedChawatheScriptGenerator();

        ConfigurableMatcher configurableMatcher = (ConfigurableMatcher) matcher;
        GumTreeProperties properties = new GumTreeProperties();
        // With 1001 fails
        properties.tryConfigure(ConfigurationOptions.bu_minsize, 1002);

        configurableMatcher.configure(properties);

        MappingStore mappings = matcher.match(leftContext.getRoot(), rightContext.getRoot());

        EditScript actions = edGenerator.computeActions(mappings);

        List<Action> actionsAll = actions.asList();

        assertEquals(1, actionsAll.size());

        assertEquals("update-node", actionsAll.get(0).getName());

    }

    @Test
    public void testSpurious1WithClassicConfiguredGreedySubtreeMatcher() throws IOException {

        URL resourceSource = getClass().getClassLoader().getResource("case_1_with_spurious/ClassA_s.javaa");
        URL resourceTarget = getClass().getClassLoader().getResource("case_1_with_spurious/ClassA_t.javaa");

        TreeContext leftContext = new JdtTreeGenerator().generateFrom().file(resourceSource.getFile());
        TreeContext rightContext = new JdtTreeGenerator().generateFrom().file(resourceTarget.getFile());
        assertFalse(leftContext.getRoot().isIsomorphicTo(rightContext.getRoot()));

        Matcher matcher = new CompositeMatchers.ClassicGumtree();
        SimplifiedChawatheScriptGenerator edGenerator = new SimplifiedChawatheScriptGenerator();

        ConfigurableMatcher configurableMatcher = (ConfigurableMatcher) matcher;
        GumTreeProperties properties = new GumTreeProperties();
        // With default (height=2) fails

        properties.tryConfigure(ConfigurationOptions.st_minprio, 1);

        configurableMatcher.configure(properties);

        MappingStore mappings = matcher.match(leftContext.getRoot(), rightContext.getRoot());

        EditScript actions = edGenerator.computeActions(mappings);

        List<Action> actionsAll = actions.asList();

        assertEquals(1, actionsAll.size());

        assertEquals("update-node", actionsAll.get(0).getName());

        // With Min = 1 the SubTreeMatcher matches the import
        GreedySubtreeMatcher greedyMatcher = new GreedySubtreeMatcher();
        greedyMatcher.configure(properties);

        MappingStore mappingsFromGreedy = greedyMatcher.match(leftContext.getRoot(), rightContext.getRoot());

        // Check those not mapped with Min = 2
        // ImportDecl
        assertTrue(mappingsFromGreedy.isSrcMapped(leftContext.getRoot().getChild(2)));
        // ImportDecl
        assertTrue(mappingsFromGreedy.isSrcMapped(leftContext.getRoot().getChild(1)));
        // PackageDecl
        assertTrue(mappingsFromGreedy.isSrcMapped(leftContext.getRoot().getChild(0)));

        // Now check unmapped when Min = 2 (Default)
        properties = new GumTreeProperties();
        properties.tryConfigure(ConfigurationOptions.st_minprio, 2);
        assertEquals(2, properties.get(ConfigurationOptions.st_minprio));
        greedyMatcher.configure(properties);
        mappingsFromGreedy = greedyMatcher.match(leftContext.getRoot(), rightContext.getRoot());

        assertEquals(2, greedyMatcher.getMinPriority());
        // Check those not mapped with Min = 2
        // ImportDecl
        assertFalse(mappingsFromGreedy.isSrcMapped(leftContext.getRoot().getChild(2)));
        // ImportDecl
        assertFalse(mappingsFromGreedy.isSrcMapped(leftContext.getRoot().getChild(1)));
        // PackageDecl
        assertFalse(mappingsFromGreedy.isSrcMapped(leftContext.getRoot().getChild(0)));

    }

}
