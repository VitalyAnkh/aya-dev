module aya.util.more {
  requires aya.pretty;
  requires transitive aya.util;

  requires static org.jetbrains.annotations;
  requires transitive kala.collection.primitive;

  exports org.aya.util.binop;
  exports org.aya.util.error;
  exports org.aya.util.position;
  exports org.aya.util.reporter;
  exports org.aya.util.terck;
  exports org.aya.util.tyck.pat;
}
