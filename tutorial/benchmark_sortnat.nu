#!/usr/bin/env nu
# Benchmark a Dhall script: wall-clock time vs env N (iterations) and S (list size).
# Fit: t ≈ const * N^a * S^b via OLS on log(t) = log(const) + a*log(N) + b*log(S).
#
# Usage (from this directory):
#   nu benchmark_sortnat.nu SortNat.dhall 1000 200
# With no arguments, prints usage and exits with an error.
# Requires: dhall on PATH; uses benchmark_sortnat_regress.py (stdlib Python).

def print-help [] {
    print "Usage: nu benchmark_sortnat.nu <script.dhall> <max_N> <max_S>"
    print ""
    print "Benchmark a Dhall file with environment N (iterations) and S (list size)."
    print "For each axis, the grid is three points: max/2, 3*max/4, max (integer division)."
    print "  max_N — upper bound for N; grid uses N/2, 3N/4, N"
    print "  max_S — upper bound for S; grid uses S/2, 3S/4, S"
    print ""
    print "Examples:"
    print "  nu benchmark_sortnat.nu SortNat.dhall 900 180"
    print "  nu benchmark_sortnat.nu help"
}

# Three-point grid: max/2, 3*max/2, max (requires max >= 3 so all values are positive).
def grid-three [m: int] {
    [ ($m // 2) ((3 * $m) // 4) $m ]
}

def main [
    script?: string
    max_n?: int
    max_s?: int
] {
    if $script == "help" {
        print-help
        exit 0
    }

    if $script == null or $max_n == null or $max_s == null {
        print "error: missing required arguments."
        print ""
        print-help
        exit 1
    }

    if $max_n < 3 or $max_s < 3 {
        print "error: max_N and max_S must be at least 3 (so grid values are positive)."
        exit 1
    }

    let dhall_script = ($script | path expand)
    let regress_py = ([(pwd) "benchmark_sortnat_regress.py"] | path join)
    let repeats = 3

    let n_vals = (grid-three $max_n)
    let s_vals = (grid-three $max_s)

    def run-once [n: int, s: int] {
        let secs = (
            with-env { N: ($n | into string), S: ($s | into string) } {
                timeit { ^dhall --file $dhall_script }
            }
        ) / 1sec
        $secs
    }

    print "Warming up (Dhall cache / imports)..."
    with-env { N: "8", S: "4" } { ^dhall --file $dhall_script out> /dev/null err> /dev/null }

    mut rows = []
    print $"Grid: N=($n_vals) × S=($s_vals) × ($repeats) repeats  script=($dhall_script)"

    for n in $n_vals {
        for s in $s_vals {
            mut samples = []
            for r in 1..$repeats {
                $samples = ($samples | append (run-once $n $s))
            }
            let sorted = ($samples | sort)
            let idx = ((($sorted | length) - 1) // 2)
            let t_med = ($sorted | get $idx)
            $rows = ($rows | append { N: $n, S: $s, t_sec: $t_med, samples: $samples })
            print $"N=($n) S=($s)  median_real_s=($t_med)  samples=($samples)"
        }
    }

    print ""
    print "Raw table (N, S, t_sec median):"
    $rows | each { |r| print $"($r.N)\t($r.S)\t($r.t_sec)" }

    let tsv = (
        $rows
        | each { |r| $"($r.N)\t($r.S)\t($r.t_sec)" }
        | str join "\n"
    )

    $tsv | python3 $regress_py
}
