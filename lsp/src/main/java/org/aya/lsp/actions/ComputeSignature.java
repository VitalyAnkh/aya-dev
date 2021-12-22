// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.stmt.Signatured;
import org.aya.core.def.Def;
import org.aya.core.term.Term;
import org.aya.distill.BaseDistiller;
import org.aya.distill.CoreDistiller;
import org.aya.lsp.utils.Resolver;
import org.aya.pretty.doc.Doc;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;

public interface ComputeSignature {
  static @NotNull Doc invokeHover(
    @NotNull LibrarySource source,
    @NotNull Position position
  ) {
    var target = Resolver.resolveVar(source, position).firstOrNull();
    if (target == null) return Doc.empty();
    return computeSignature(target.data(), true);
  }

  static @NotNull Doc computeSignature(@NotNull Var target, boolean withResult) {
    return switch (target) {
      case LocalVar localVar -> BaseDistiller.varDoc(localVar);
      case DefVar<?, ?> defVar -> {
        @SuppressWarnings("unchecked")
        var ref = (DefVar<? extends Def, ? extends Signatured>) defVar;
        // #299: hovering a mouse on a definition whose header is failed to tyck
        if (ref.core == null && ref.concrete.signature == null) yield Doc.empty();
        yield computeSignature(Def.defTele(ref), Def.defResult(ref), withResult);
      }
      default -> Doc.empty();
    };
  }
  static @NotNull Doc computeSignature(ImmutableSeq<Term.Param> defTele, Term defResult, boolean withResult) {
    var options = DistillerOptions.pretty();
    var distiller = new CoreDistiller(options);
    var tele = distiller.visitTele(defTele, defResult, Term::findUsages);
    if (withResult) {
      var res = defResult.toDoc(options);
      if (tele.isEmpty()) return res;
      return Doc.stickySep(tele, Doc.symbol(":"), res);
    } else return tele;
  }
}