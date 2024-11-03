// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.interactive;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedSet;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import kala.tuple.Tuple2;
import org.aya.resolve.context.*;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.ref.AnyDefVar;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.RepoLike;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ReplContext extends PhysicalModuleContext implements RepoLike<ReplContext> {
  private @Nullable ReplContext downstream = null;
  private boolean modified = true;
  private @Nullable ImmutableMap<String, ModuleTree> moduleTree = null;

  public ReplContext(@NotNull Context parent, @NotNull ModulePath name) {
    super(parent, name);
  }

  @Override public void importSymbol(
    @NotNull AnyVar ref,
    @NotNull ModuleName fromModule,
    @NotNull String name,
    @NotNull Stmt.Accessibility acc,
    @NotNull SourcePos sourcePos
  ) {
    modified = true;
    // REPL always overwrites symbols.
    symbols().add(name, ref, fromModule);
    if (ref instanceof DefVar<?, ?> defVar && acc == Stmt.Accessibility.Public) exportSymbol(name, defVar);
  }

  @Override public boolean exportSymbol(@NotNull String name, @NotNull AnyDefVar ref) {
    super.exportSymbol(name, ref);
    // REPL always overwrites symbols.
    return true;
  }

  @Override public void importModule(
    @NotNull ModuleName.Qualified modName,
    @NotNull ModuleExport mod,
    Stmt.@NotNull Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    modified = true;
    modules.put(modName, mod);
    if (accessibility == Stmt.Accessibility.Public) exports.export(modName, mod);
  }

  @Override public @NotNull ReplContext derive(@NotNull ModulePath extraName) {
    return new ReplContext(this, this.modulePath().derive(extraName));
  }

  @Override public @NotNull ReplContext derive(@NotNull String extraName) {
    return new ReplContext(this, this.modulePath().derive(extraName));
  }

  @Override public void setDownstream(@Nullable ReplContext downstream) {
    this.downstream = downstream;
  }

  public @NotNull ReplContext fork() {
    var kid = derive(":theKid");
    fork(kid);
    return kid;
  }

  @Override public void merge() {
    var bors = downstream;
    RepoLike.super.merge();
    if (bors == null) return;
    modified = true;
    mergeSymbols(symbols, bors.symbols);
    exports.symbols().putAll(bors.exports.symbols());
    exports.modules().putAll(bors.exports.modules());
    modules.putAll(bors.modules);
  }

  @Contract(mutates = "this") public void clear() {
    modified = true;
    modules.clear();
    exports.symbols().clear();
    exports.modules().clear();
    symbols.table().clear();
  }

  /**
   * @apiNote It is possible that putting {@link ModuleName.Qualified} and {@link ModuleName.ThisRef} to the same name,
   * so be careful about {@param rhs}
   */
  private static <T> void mergeSymbols(@NotNull ModuleSymbol<T> dest, @NotNull ModuleSymbol<T> src) {
    for (var key : src.table().keysView()) {
      var candy = dest.get(key);
      dest.table().put(key, candy.merge(src.get(key)));
    }
  }

  /// region Rebuild Module Tree

  public record ModuleTree(@NotNull ImmutableMap<String, ModuleTree> children, boolean inhabited) { }

  private @Nullable ModuleTree resolve(@NotNull ImmutableSeq<String> path) {
    var pathView = path.view();
    var tree = new ModuleTree(moduleTree(), false);
    while (pathView.isNotEmpty() && tree != null) {
      var head = pathView.getFirst();
      var tail = pathView.drop(1);
      tree = tree.children().getOrNull(head);
      pathView = tail;
    }

    return tree;
  }

  public @NotNull ImmutableSeq<String> giveMeHint(@NotNull ImmutableSeq<String> prefix) {
    var node = resolve(prefix);
    if (node == null) return ImmutableSeq.empty();

    var hint = MutableLinkedSet.<String>create();

    hint.addAll(node.children().keysView());
    if (node.inhabited) {
      var mod = getModuleMaybe(new ModuleName.Qualified(prefix));
      assert mod != null;
      hint.addAll(mod.symbols().keysView());
    }

    return hint.toImmutableSeq();
  }

  public @NotNull ImmutableMap<String, ModuleTree> moduleTree() {
    if (!modified) {
      assert this.moduleTree != null;
      return this.moduleTree;
    }

    var moduleNames = this.modules.keysView().toImmutableSeq()
      .map(x -> x.ids().view());

    this.moduleTree = buildModuleTree(moduleNames);
    this.modified = false;

    return moduleTree;
  }

  /**
   * Rebuild module tree from flattened module names
   *
   * @param moduleNames a list of {@link ModuleName.Qualified} but in an efficient representation, the element should be non-empty
   */
  private @NotNull ImmutableMap<String, ModuleTree> buildModuleTree(
    @NotNull Seq<SeqView<String>> moduleNames
  ) {
    var indexed = MutableMap.<String, MutableList<SeqView<String>>>create();
    var inhabited = MutableSet.<String>create();

    for (var name : moduleNames) {
      var head = name.getFirst();
      var tail = name.drop(1);
      // we always create a record even [tail] is empty
      var root = indexed.getOrPut(head, MutableList::create);
      if (tail.isNotEmpty()) {
        root.append(tail);
      } else {
        inhabited.add(head);
      }
    }

    var result = indexed.toImmutableSeq()
      .collect(ImmutableMap.collector(Tuple2::component1, x -> {
        var children = buildModuleTree(x.component2());
        var isInhabited = inhabited.contains(x.component1());
        return new ModuleTree(children, isInhabited);
      }));

    return result;
  }

  /// endregion Rebuild Module Tree
}
