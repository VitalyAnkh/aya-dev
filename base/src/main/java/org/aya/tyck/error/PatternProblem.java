// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.mutable.MutableList;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.ConCall;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public sealed interface PatternProblem extends Problem {
  @NotNull WithPos<? extends Pattern> pattern();
  @Override default @NotNull SourcePos sourcePos() { return pattern().sourcePos(); }

  record BlockedEval(
    @Override @NotNull WithPos<Pattern> pattern,
    @NotNull DataCall dataCall
  ) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.english("Unsure if this pattern is actually impossible, as constructor selection is blocked on:"),
        Doc.par(1, dataCall.toDoc(options)));
    }
  }

  record PossiblePat(
    @Override @NotNull WithPos<Pattern> pattern,
    @NotNull ConCall.Head available
  ) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("Absurd pattern does not fit here because"),
        Doc.code(BasePrettier.refVar(available.ref())),
        Doc.english("is still available")
      );
    }
  }

  record SplittingOnNonData(
    @Override @NotNull WithPos<Pattern> pattern,
    @NotNull Term type
  ) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.english("Cannot split on a non-inductive type"),
        Doc.par(1, type.toDoc(options)),
        Doc.english("with a constructor pattern"),
        Doc.par(1, pattern.data().toDoc(options)));
    }
  }

  record UnavailableCon(
    @Override @NotNull WithPos<Pattern> pattern,
    @NotNull DataCall dataCall
  ) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.english("I'm unsure if there should be a case for"),
        Doc.par(1, pattern.data().toDoc(options)),
        Doc.english("as index unification is blocked for type"),
        Doc.par(1, dataCall.toDoc(options)));
    }
  }

  record UnknownCon(@Override @NotNull WithPos<Pattern> pattern) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.english("Unknown constructor"),
        Doc.par(1, pattern.data().toDoc(options))
      );
    }
  }

  record TupleNonSig(
    @Override @NotNull WithPos<Pattern> pattern,
    @NotNull Stateful stateful, @NotNull Term type
  ) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var typeDoc = type.toDoc(options);
      var lines = MutableList.of(
        Doc.english("The tuple pattern"),
        Doc.par(1, pattern.data().toDoc(options)),
        Doc.english("splits only on sigma types, while the actual type is not:"),
        Doc.par(1, typeDoc));
      var normalizedDoc = stateful.fullNormalize(type).toDoc(options);
      if (!typeDoc.equals(normalizedDoc)) {
        lines.append(Doc.english("Normalized:"));
        lines.append(Doc.par(1, normalizedDoc));
      }
      return Doc.vcat(lines);
    }
  }

  record TooManyPattern(@Override @NotNull WithPos<Pattern> pattern) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.english("There is no parameter for the pattern"),
        Doc.par(1, pattern.data().toDoc(options)),
        Doc.english("to match against."),
        Doc.parened(Doc.sep(
          Doc.english("and in case it's a function type, you may want to move its parameters before the"),
          Doc.code(":"),
          Doc.english("in the signature"))));
    }
  }

  record InsufficientPattern(
    @Override @NotNull WithPos<Pattern> pattern,
    @NotNull Param param
  ) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.english("There is no pattern for the parameter"),
        Doc.par(1, param.toDoc(options)));
    }
  }

  record ImplicitDisallowed(@Override @NotNull WithPos<Pattern> pattern) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("Pattern matching with"),
        Doc.styled(BasePrettier.KEYWORD, "elim"),
        Doc.english("is not compatible with implicit patterns.")
      );
    }
  }

  record UnimportedConName(
    @Override @NotNull WithPos<Pattern.Bind> pattern
  ) implements PatternProblem {
    @Override public @NotNull Severity level() { return Severity.WARN; }

    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.english("You wrote the following pattern:"),
        Doc.par(1, Doc.plain(pattern.data().bind().name())),
        Doc.english("It sounds like you are trying to match with a constructor that is not in scope, " +
          "so it will be treated as a variable pattern.")
      );
    }
  }

  record TooManyImplicitPattern(
    @Override @NotNull WithPos<Pattern> pattern,
    @NotNull Param param
  ) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.english("There are too many implicit patterns:"),
        Doc.par(1, pattern.data().toDoc(options)),
        Doc.english("should be an explicit pattern matched against"),
        Doc.par(1, param.toDoc(options)));
    }
  }

  @Override default @NotNull Severity level() { return Severity.ERROR; }

  record BadLitPattern(
    @NotNull WithPos<Pattern> pattern,
    @NotNull Term type
  ) implements PatternProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(Doc.english("The literal"),
        Doc.par(1, pattern.data().toDoc(options)),
        Doc.english("cannot be encoded as a term of type:"),
        Doc.par(1, type.toDoc(options)));
    }
  }

  record InvalidEmptyBody(@NotNull Pattern.Clause match) implements Problem {
    @Override @NotNull public SourcePos sourcePos() { return match.sourcePos; }

    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.english("This match arm does not contain any absurd pattern but it has an empty body");
    }

    @Override public @NotNull Severity level() { return Severity.ERROR; }
  }
}
