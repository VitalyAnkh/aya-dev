// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.Seq;
import org.aya.syntax.compile.JitMatchy;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.QName;
import org.jetbrains.annotations.NotNull;

public sealed interface MatchyLike permits JitMatchy, Matchy {
  @NotNull QName qualifiedName();
  @NotNull Term type(@NotNull Seq<Term> captures, @NotNull Seq<Term> args);
}
