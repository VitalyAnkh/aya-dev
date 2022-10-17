// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import kala.tuple.Tuple;
import org.aya.concrete.Expr;
import org.jetbrains.annotations.NotNull;

/**
 * A generic view for traversing Expr
 *
 * @author luna
 */

public interface ExprView {
  @NotNull Expr initial();

  default @NotNull Expr pre(@NotNull Expr expr) {return expr;}

  default @NotNull Expr post(@NotNull Expr expr) {return expr;}

  private @NotNull Expr commit(@NotNull Expr expr) {return post(traverse(pre(expr)));}

  private Expr.@NotNull Param commit(Expr.@NotNull Param param) {
    var type = commit(param.type());
    if (type == param.type()) return param;
    return new Expr.Param(param, type);
  }

  private Expr.@NotNull NamedArg commit(Expr.@NotNull NamedArg arg) {
    var expr = commit(arg.expr());
    if (expr == arg.expr()) return arg;
    return new Expr.NamedArg(arg.explicit(), expr);
  }

  private Expr.@NotNull PartEl commit(Expr.@NotNull PartEl partial) {
    var clauses = partial.clauses().map(cls -> Tuple.of(commit(cls._1), commit(cls._2)));
    if (clauses.allMatchWith(partial.clauses(), (l, r) ->
      l._1 == r._1 && l._2 == r._2)) return partial;
    return new Expr.PartEl(partial.sourcePos(), clauses);
  }

  private Expr.@NotNull ProjOrCoe commit(@NotNull Expr.ProjOrCoe data) {
    var freeze = data.freeze().map(this::commit);
    if (freeze.sameElements(data.freeze(), true)) return data;
    return new Expr.ProjOrCoe(data.id(), freeze, data.resolvedIx());
  }

  private @NotNull Expr traverse(@NotNull Expr expr) {
    return switch (expr) {
      case Expr.RefExpr ref -> ref;
      case Expr.UnresolvedExpr unresolved -> unresolved;
      case Expr.LamExpr lam -> {
        var param = commit(lam.param());
        var body = commit(lam.body());
        if (param == lam.param() && body == lam.body()) yield lam;
        yield new Expr.LamExpr(lam.sourcePos(), param, body);
      }
      case Expr.PiExpr pi -> {
        var param = commit(pi.param());
        var last = commit(pi.last());
        if (param == pi.param() && last == pi.last()) yield pi;
        yield new Expr.PiExpr(pi.sourcePos(), pi.co(), param, last);
      }
      case Expr.SigmaExpr sigma -> {
        var params = sigma.params().map(this::commit);
        if (params.sameElements(sigma.params(), true)) yield sigma;
        yield new Expr.SigmaExpr(sigma.sourcePos(), sigma.co(), params);
      }
      case Expr.RawSortExpr rawType -> rawType;
      case Expr.LiftExpr lift -> {
        var inner = commit(lift.expr());
        if (inner == lift.expr()) yield lift;
        yield new Expr.LiftExpr(lift.sourcePos(), inner, lift.lift());
      }
      case Expr.SortExpr univ -> univ;
      case Expr.AppExpr(var pos, var f, var a) -> {
        var func = commit(f);
        var arg = commit(a);
        if (func == f && arg == a) yield expr;
        yield new Expr.AppExpr(pos, func, arg);
      }
      case Expr.HoleExpr hole -> {
        var filling = hole.filling();
        var committed = filling != null ? commit(filling) : null;
        if (committed == filling) yield hole;
        yield new Expr.HoleExpr(hole.sourcePos(), hole.explicit(), committed, hole.accessibleLocal());
      }
      case Expr.TupExpr tup -> {
        var items = tup.items().map(this::commit);
        if (items.sameElements(tup.items(), true)) yield tup;
        yield new Expr.TupExpr(tup.sourcePos(), items);
      }
      case Expr.ProjExpr proj -> {
        var tup = commit(proj.tup());
        var ix = proj.ix().map(x -> x, this::commit);
        var sameElements = ix.map(x -> proj.ix().getLeftValue().equals(x), x -> x == proj.ix().getRightValue())
          .fold(x -> x, x -> x);
        if (tup == proj.tup() && sameElements) yield proj;
        yield new Expr.ProjExpr(proj.sourcePos(), tup, ix, proj.theCore());
      }
      case Expr.CoeExpr coe -> {
        var e = commit(coe.expr());
        var data = commit(coe.coeData());
        if (e == coe.expr() && data == coe.coeData()) yield coe;
        yield new Expr.CoeExpr(coe.sourcePos(), e, data);
      }
      case Expr.NewExpr neu -> {
        var struct = commit(neu.struct());
        var fields = neu.fields().map(field ->
          new Expr.Field(field.name(), field.bindings(), commit(field.body()), field.resolvedField()));
        if (struct == neu.struct() && fields.sameElements(neu.fields(), true)) yield neu;
        yield new Expr.NewExpr(neu.sourcePos(), struct, fields);
      }
      case Expr.PartEl el -> commit(el);
      case Expr.Path path -> {
        var partial = commit(path.partial());
        var type = commit(path.type());
        if (partial == path.partial() && type == path.type()) yield path;
        yield new Expr.Path(path.sourcePos(), path.params(), type, partial);
      }
      case Expr.LitIntExpr litInt -> litInt;
      case Expr.LitStringExpr litStr -> litStr;
      case Expr.BinOpSeq binOpSeq -> {
        var seq = binOpSeq.seq().map(this::commit);
        if (seq.sameElements(binOpSeq.seq(), true)) yield binOpSeq;
        yield new Expr.BinOpSeq(binOpSeq.sourcePos(), seq);
      }
      case Expr.ErrorExpr error -> error;
      case Expr.MetaPat meta -> meta;
      case Expr.Idiom idiom -> {
        var newInner = idiom.barredApps().map(this::commit);
        var newNames = idiom.names().fmap(this::commit);
        if (newInner.sameElements(idiom.barredApps()) && newNames.identical(idiom.names()))
          yield expr;
        yield new Expr.Idiom(idiom.sourcePos(), newNames, newInner);
      }
      case Expr.Do doNotation -> {
        var lamExprs = doNotation.binds().map(x ->
          new Expr.DoBind(x.sourcePos(), x.var(), commit(x.expr())));
        var bindName = commit(doNotation.bindName());
        if (lamExprs.sameElements(doNotation.binds()) && bindName == doNotation.bindName())
          yield doNotation;
        yield new Expr.Do(doNotation.sourcePos(), bindName, lamExprs);
      }
      case Expr.Array arrayExpr -> arrayExpr.arrayBlock().fold(
        left -> {
          var generator = commit(left.generator());
          var bindings = left.binds().map(binding ->
            new Expr.DoBind(binding.sourcePos(), binding.var(), commit(binding.expr()))
          );
          var bindName = commit(left.bindName());
          var pureName = commit(left.pureName());

          if (generator == left.generator() && bindings.sameElements(left.binds()) && bindName == left.bindName() && pureName == left.pureName()) {
            return arrayExpr;
          } else {
            return Expr.Array.newGenerator(arrayExpr.sourcePos(), generator, bindings, bindName, pureName);
          }
        },
        right -> {
          var exprs = right.exprList().map(this::commit);
          var nilCtor = commit(right.nilCtor());
          var consCtor = commit(right.consCtor());

          if (exprs.sameElements(right.exprList()) && nilCtor == right.nilCtor() && consCtor == right.consCtor()) {
            return arrayExpr;
          } else {
            return Expr.Array.newList(arrayExpr.sourcePos(), exprs, nilCtor, consCtor);
          }
        }
      );
    };
  }

  default @NotNull Expr commit() {
    return commit(initial());
  }
}
