package io.threecolor.deduction;

/** Logical relations never mutate the physical graph. Masks use Color's three bits. */
public sealed interface LogicalConclusion {
  enum Kind {
    NARROW_DOMAIN,
    EQUAL,
    NOT_EQUAL,
    CONTRADICTION
  }

  Kind kind();

  record NarrowDomain(int node, int mask) implements LogicalConclusion {
    public NarrowDomain {
      if (node < 0 || mask < 0 || mask > 7) throw new IllegalArgumentException("Invalid domain");
    }

    public Kind kind() {
      return Kind.NARROW_DOMAIN;
    }
  }

  record Equal(int a, int b) implements LogicalConclusion {
    public Equal {
      if (a < 0 || b < 0) throw new IllegalArgumentException("Invalid vertex");
      if (a > b) {
        int t = a;
        a = b;
        b = t;
      }
    }

    public Kind kind() {
      return Kind.EQUAL;
    }
  }

  record NotEqual(int a, int b) implements LogicalConclusion {
    public NotEqual {
      if (a < 0 || b < 0) throw new IllegalArgumentException("Invalid vertex");
      if (a > b) {
        int t = a;
        a = b;
        b = t;
      }
    }

    public Kind kind() {
      return Kind.NOT_EQUAL;
    }
  }

  record Contradiction() implements LogicalConclusion {
    public Kind kind() {
      return Kind.CONTRADICTION;
    }
  }
}
