// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.AyaDocile;
import org.aya.prettier.CorePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.compile.JitDef;
import org.aya.syntax.concrete.stmt.decl.Decl;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A well-typed generic definition,
 * used before JIT compilation.
 *
 * @author zaoqi
 */
public sealed interface TyckDef extends AyaDocile permits SubLevelDef, TopLevelDef {
  @Override default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new CorePrettier(options).def(this);
  }

  //region Pretty & IDE only APIs

  static @NotNull Term defType(@NotNull AnyDef var) {
    return switch (var) {
      case TyckAnyDef<?> tyckDef -> Objects.requireNonNull(tyckDef.ref.signature).makePi();
      case JitDef jitDef -> jitDef.makePi();
    };
  }
  //endregion

  /**
   * @see AnyDef#signature()
   */
  static @NotNull AbstractTele defSignature(@NotNull DefVar<? extends TyckDef, ? extends Decl> defVar) {
    if (defVar.core != null) return new AbstractTele.Locns(defVar.core.telescope(), defVar.core.result());
    // guaranteed as this is already a core term
    var signature = defVar.signature;
    assert signature != null : defVar.name();
    return new AbstractTele.Locns(signature.rawParams(), signature.result());
  }

  @NotNull DefVar<? extends TyckDef, ?> ref();
  @NotNull Term result();
  @NotNull ImmutableSeq<Param> telescope();
}
