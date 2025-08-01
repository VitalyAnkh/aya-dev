// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.control.Option;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.context.Candidate;
import org.aya.syntax.context.ModuleExport;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/// @author re-xyr
/// @apiNote in each file's dependency tree there should be one and only one [EmptyContext] which is also the tree root.
/// @implNote this is the context storing the underlying file, and its Reporter in the resolving stage.
public record EmptyContext(@NotNull Path underlyingFile) implements Context {
  @Override public @Nullable Context parent() { return null; }

  @Override public @Nullable Option<AnyVar> getQualifiedLocalMaybe(
    @NotNull ModuleName.Qualified modName,
    @NotNull String name,
    @NotNull SourcePos sourcePos,
    @NotNull Reporter reporter
  ) { return null; }

  @Override public @NotNull PhysicalModuleContext derive(@NotNull ModulePath extraName) {
    return new PhysicalModuleContext(this, extraName);
  }

  @Override public @NotNull ModulePath modulePath() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable Candidate<AnyVar> getCandidateLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    return null;
  }

  @Override public @Nullable ModuleExport getModuleLocalMaybe(@NotNull ModuleName.Qualified modName) {
    return null;
  }
}
