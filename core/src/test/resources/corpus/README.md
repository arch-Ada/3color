# Regression corpus

Properties files use zero-based node IDs, canonical edge pairs, and named colors.
`index.txt` lists every executable fixture. `CorpusTest` checks exact status,
logical hashes when recorded, human difficulty when supplied, and geometry failures.

The generated fixtures include their seed, full spec, attempt index, generator
version, normalized layout, and fixed logical hash. They retain historical labels across all five difficulty
bands; these are static solver/analyzer fixtures, not supported generation routes.
Retired-family seeds are not a regeneration recipe for the current application. Hand-built fixtures cover contradictions, multiplicity, givens, and crossing
geometry. The historical-bugs directory starts with a defensive conflicting-givens
regression; it does not imply a previous application existed.
