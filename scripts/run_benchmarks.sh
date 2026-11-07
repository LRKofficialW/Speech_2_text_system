#!/usr/bin/env bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
PROJECT_ROOT="$(dirname "$DIR")"

echo "=== Running NeuroScribe Benchmark Suite ==="
python3 "$PROJECT_ROOT/benchmark/benchmark_runner.py"

echo "=== Benchmark Finished Successfully ==="
