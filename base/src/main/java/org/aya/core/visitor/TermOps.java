// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.mutable.MutableMap;
import org.aya.core.term.*;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface TermOps extends TermView {
  @NotNull TermView view();
  @Override default @NotNull Term initial() {
    return view().initial();
  }

  record Mapper(
    @Override @NotNull TermView view,
    @NotNull Function<@NotNull Term, @NotNull Term> pre,
    @NotNull Function<@NotNull Term, @NotNull Term> post
  ) implements TermOps {
    @Override public TermView preMap(Function<Term, Term> f) {
      return new Mapper(view, pre.compose(f), post);
    }

    @Override public TermView postMap(Function<Term, Term> f) {
      return new Mapper(view, pre, f.compose(post));
    }

    @Override public Term pre(Term term) {
      return view.pre(pre.apply(term));
    }

    @Override public Term post(Term term) {
      return post.apply(view.post(term));
    }
  }

  record Subster(@NotNull @Override TermView view, Subst subst) implements TermOps {
    @Override public TermView subst(Subst subst) {
      return new Subster(view, subst.add(subst));
    }

    @Override public Term post(Term term) {
      return switch (view.post(term)) {
        case RefTerm ref -> subst.map().getOption(ref.var()).map(Term::rename).getOrDefault(ref);
        case RefTerm.Field field -> subst.map().getOption(field.ref()).map(Term::rename).getOrDefault(field);
        case Term misc -> misc;
      };
    }
  }

  /** Not an IntelliJ Renamer. */
  record Renamer(@NotNull @Override TermView view, Subst subst) implements TermOps {
    public Renamer(@NotNull TermView view) {
      this(view, new Subst(MutableMap.create()));
    }

    private @NotNull Term.Param handleBinder(@NotNull Term.Param param) {
      var v = param.renameVar();
      subst.addDirectly(param.ref(), new RefTerm(v, 0));
      return new Term.Param(v, param.type(), param.pattern(), param.explicit());
    }

    @Override public Term pre(Term term) {
      return switch (view.pre(term)) {
        case IntroTerm.Lambda lambda -> new IntroTerm.Lambda(handleBinder(lambda.param()), lambda.body());
        case FormTerm.Pi pi -> new FormTerm.Pi(handleBinder(pi.param()), pi.body());
        case FormTerm.Sigma sigma -> new FormTerm.Sigma(sigma.params().map(this::handleBinder));
        case Term misc -> misc;
      };
    }

    @Override public Term post(Term term) {
      return switch (view.post(term)) {
        case RefTerm ref -> subst.map().getOrDefault(ref.var(), ref);
        // [ice]: need to generate "replacements" for 'this' bindings as well?
        case RefTerm.Field field -> subst.map().getOrDefault(field.ref(), field);
        case Term misc -> misc;
      };
    }
  }

  /** A lift but in American English. */
  record Elevator(@NotNull @Override TermView view, int ulift) implements TermOps {
    @Override public TermView lift(int shift) {
      return new Elevator(view, ulift + shift);
    }

    @Override public Term post(Term term) {
      // TODO: Implement the correct rules.
      return switch (view.post(term)) {
        case FormTerm.Univ univ -> new FormTerm.Univ(univ.lift() + ulift);
        case ElimTerm.Proj proj -> new ElimTerm.Proj(proj.of(), proj.ulift() + ulift, proj.ix());
        case CallTerm.Struct struct -> new CallTerm.Struct(struct.ref(), struct.ulift() + ulift, struct.args());
        case CallTerm.Data data -> new CallTerm.Data(data.ref(), data.ulift() + ulift, data.args());
        case CallTerm.Con con -> {
          var head = con.head();
          head = new CallTerm.ConHead(head.dataRef(), head.ref(), head.ulift() + ulift, head.dataArgs());
          yield new CallTerm.Con(head, con.conArgs());
        }
        case CallTerm.Fn fn -> new CallTerm.Fn(fn.ref(), fn.ulift() + ulift, fn.args());
        case CallTerm.Access access ->
          new CallTerm.Access(access.of(), access.ref(), access.ulift() + ulift, access.structArgs(), access.fieldArgs());
        case CallTerm.Prim prim -> new CallTerm.Prim(prim.ref(), prim.ulift() + ulift, prim.args());
        case CallTerm.Hole hole -> new CallTerm.Hole(hole.ref(), hole.ulift() + ulift, hole.contextArgs(), hole.args());
        case Term misc -> misc;
      };
    }
  }
}