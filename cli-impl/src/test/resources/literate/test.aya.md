```aya
open inductive Unit | unit
```

This is a tutorial about aya, but I won't tell you how to write aya.
For example, this is how aya looks like:

```aya
```

Oh sorry... I forgot to put something in it.
But don't worry, I have another code block, look here:

```aya
def foo =>

```

This is an illegal code, because you should write something in the function body.

```aya
  unit
```

Umm, now it looks good!

Also, we can use some special characters as identifier:

```aya
open inductive Bool | true | false

def > (lhs rhs : Unit) : Bool =>
```

It probably always false, I think.

```aya
  false
```

This is an Aya code block with only lexer-highlighting:

```aya-lexer
def goofy => ???
// keywords that don't exist
coinductive Stream (A : Type) : Type
```
