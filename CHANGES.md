# Upcoming changes

The next engine and web update is in preparation. There is no release date yet.

- Faster hint processing and puzzle certification, with existing deduction rules
  and difficulty ratings preserved.
- A choice between a single deduction and a hint that finds a colour, with clearer
  explanations and improved mobile layouts.
- More reliable retries and cancellation: busy requests retain their seed, and
  changing the board cancels obsolete hint requests.
- Expanded regression tests and performance benchmarks.

Performance improvements have been measured on a development notebook; Raspberry
Pi and Android device measurements are still pending.

## Later: standalone Android

A separate Android release is planned, with 6080 bundled puzzles, offline hints
and exact puzzle replay. F-Droid preparation is underway, no APK release date is
announced. Android will remain standalone, without a server or account.
