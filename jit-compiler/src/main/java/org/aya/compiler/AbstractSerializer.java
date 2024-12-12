// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.*;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

// TODO: remove this abstraction
public abstract class AbstractSerializer<B, T> {
  public record JitParam(@NotNull String name, @NotNull String type) { }

  protected AbstractSerializer() { }

  /**
   * the implementation should keep {@link SourceBuilder#indent} after invocation.
   */
  public abstract AbstractSerializer<B, T> serialize(@NotNull B builder, T unit);

  protected @NotNull FreeJavaExpr serializeTermUnderTele(
    @NotNull FreeExprBuilder builder,
    @NotNull Term term,
    @NotNull FreeJavaExpr argsTerm,
    int size
  ) {
    return serializeTermUnderTele(builder, term, AbstractExprializer.fromSeq(builder, Constants.CD_Term, argsTerm, size));
  }

  protected @NotNull FreeJavaExpr serializeTermUnderTele(
    @NotNull FreeExprBuilder builder,
    @NotNull Term term,
    @NotNull ImmutableSeq<FreeJavaExpr> argTerms
  ) {
    return new TermExprializer(builder, argTerms)
      .serialize(term);
  }

  protected @NotNull FreeJavaExpr serializeTerm(@NotNull FreeCodeBuilder builder, @NotNull Term term) {
    return serializeTermUnderTele(builder, term, ImmutableSeq.empty());
  }
}
