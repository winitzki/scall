#!/usr/bin/env python3
"""Read TSV lines: N \\t S \\t t_sec; fit log(t) = log(c) + a log(N) + b log(S)."""
from __future__ import annotations

import math
import sys


def solve_3x3(a: list[list[float]], b: list[float]) -> list[float]:
    n = 3
    m_ = [row[:] + [b[i]] for i, row in enumerate(a)]
    for col in range(n):
        piv = max(range(col, n), key=lambda r: abs(m_[r][col]))
        m_[col], m_[piv] = m_[piv], m_[col]
        if abs(m_[col][col]) < 1e-18:
            raise SystemExit("singular matrix — need more varied N,S points")
        for r in range(col + 1, n):
            f = m_[r][col] / m_[col][col]
            for c in range(col, n + 1):
                m_[r][c] -= f * m_[col][c]
    x = [0.0] * n
    for i in range(n - 1, -1, -1):
        s = m_[i][n]
        for j in range(i + 1, n):
            s -= m_[i][j] * x[j]
        x[i] = s / m_[i][i]
    return x


def main() -> None:
    rows: list[tuple[float, float, float]] = []
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) < 3:
            continue
        nn, ss, tt = float(parts[0]), float(parts[1]), float(parts[2])
        if tt <= 0 or nn <= 0 or ss <= 0:
            continue
        rows.append((nn, ss, tt))

    if len(rows) < 4:
        print("Need at least 4 data points", file=sys.stderr)
        sys.exit(1)

    xrows: list[list[float]] = []
    y: list[float] = []
    for nn, ss, tt in rows:
        xrows.append([1.0, math.log(nn), math.log(ss)])
        y.append(math.log(tt))

    m = len(xrows)
    xtx = [[0.0] * 3 for _ in range(3)]
    xty = [0.0] * 3
    for i in range(3):
        for j in range(3):
            xtx[i][j] = sum(xrows[k][i] * xrows[k][j] for k in range(m))
        xty[i] = sum(xrows[k][i] * y[k] for k in range(m))

    beta = solve_3x3(xtx, xty)
    log_c, a, b = beta
    c = math.exp(log_c)

    y_mean = sum(y) / len(y)
    ss_tot = sum((yi - y_mean) ** 2 for yi in y)
    ss_res = 0.0
    for k in range(m):
        pred = beta[0] + beta[1] * xrows[k][1] + beta[2] * xrows[k][2]
        ss_res += (y[k] - pred) ** 2
    r2 = 1.0 - ss_res / ss_tot if ss_tot > 0 else float("nan")

    print()
    print("--- Fit: t ≈ const * N^a * S^b (OLS on log(time)) ---")
    print(f"const = {c:.6g}")
    print(f"a     = {a:.6g}   (exponent on N)")
    print(f"b     = {b:.6g}   (exponent on S)")
    print(f"R² (log t) = {r2:.4f}")
    print()
    print("Interpretation (approximate):")
    if abs(a - 1.0) < 0.25:
        print("- Time scales about linearly with N.")
    elif abs(a - 2.0) < 0.35:
        print("- Time scales about quadratically with N.")
    else:
        print(f"- Time scales roughly like N^{a:.2f}.")
    if abs(b - 1.0) < 0.25:
        print("- Time scales about linearly with S.")
    elif abs(b - 2.0) < 0.35:
        print("- Time scales about quadratically with S.")
    else:
        print(f"- Time scales roughly like S^{b:.2f}.")


if __name__ == "__main__":
    main()
