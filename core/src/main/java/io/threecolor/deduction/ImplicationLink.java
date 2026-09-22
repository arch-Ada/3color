package io.threecolor.deduction;

/** One readable step: source has sourceMask, so target must have targetMask. */
public record ImplicationLink(int source, int sourceMask, int target, int targetMask) {}
